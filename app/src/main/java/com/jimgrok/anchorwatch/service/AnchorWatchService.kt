package com.jimgrok.anchorwatch.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jimgrok.anchorwatch.AnchorWatchApp
import com.jimgrok.anchorwatch.MainActivity
import com.jimgrok.anchorwatch.R
import com.jimgrok.anchorwatch.alarm.AlarmPlayer
import com.jimgrok.anchorwatch.alarm.shouldFireDragAlarm
import com.jimgrok.anchorwatch.data.GeoFix
import com.jimgrok.anchorwatch.data.WatchState
import com.jimgrok.anchorwatch.data.WatchStore
import com.jimgrok.anchorwatch.data.haversineFt

class AnchorWatchService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var alarmPlayer: AlarmPlayer
    private var dwellMs: Long = DEFAULT_DWELL_MS

    private val mainHandler = Handler(Looper.getMainLooper())
    private val alarmCheckRunnable = object : Runnable {
        override fun run() {
            evaluateAlarm()
            mainHandler.postDelayed(this, ALARM_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        alarmPlayer = AlarmPlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWatchInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SILENCE -> {
                WatchStore.setAlarming(false)
                WatchStore.clearOutsideSince()
                alarmPlayer.stop()
                startForeground(NOTIF_ID, buildNotification(WatchStore.snapshot()))
                return START_STICKY
            }
            ACTION_TEST_ALARM -> {
                WatchStore.setAlarming(true)
                alarmPlayer.start()
                startForeground(NOTIF_ID, buildNotification(WatchStore.snapshot()))
                return START_STICKY
            }
        }

        val state = WatchStore.snapshot()
        val radius = intent?.getIntExtra(EXTRA_RADIUS_FT, state.radiusFt) ?: state.radiusFt
        dwellMs = intent?.getLongExtra(EXTRA_DWELL_MS, DEFAULT_DWELL_MS) ?: DEFAULT_DWELL_MS

        // Resume an existing watch after an OS restart instead of re-dropping the hook at the
        // current position. A persisted `watching` flag with a stored hook is authoritative.
        if (state.watching && state.anchor != null) {
            Log.i(TAG, "Resuming persisted watch (anchor=${state.anchor.latitude},${state.anchor.longitude}, radius=${state.radiusFt}ft)")
            WatchStore.setRadius(radius)
            startForeground(NOTIF_ID, buildNotification(WatchStore.snapshot()))
            startGps()
            startAlarmTicker()
            return START_STICKY
        }

        val seed = lastKnownFix()
        if (seed != null) {
            WatchStore.startWatch(seed, radius)
        } else {
            Log.w(TAG, "No last-known fix and no persisted anchor; watch started without a hook")
            WatchStore.setRadius(radius)
        }

        startForeground(NOTIF_ID, buildNotification(WatchStore.snapshot()))
        startGps()
        startAlarmTicker()
        return START_STICKY
    }

    override fun onDestroy() {
        stopAlarmTicker()
        stopGps()
        alarmPlayer.stop()
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        val fix = GeoFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = location.accuracy,
            timeMs = System.currentTimeMillis(),
            speedMps = if (location.hasSpeed()) location.speed else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f
        )
        val state = WatchStore.snapshot()
        if (!state.watching) {
            WatchStore.previewFix(fix)
            return
        }
        val anchor = state.anchor ?: return
        val distanceFt = haversineFt(
            anchor.latitude, anchor.longitude, fix.latitude, fix.longitude
        )
        val now = System.currentTimeMillis()
        val outside = distanceFt > state.radiusFt
        // outsideSinceMs is the wall-clock moment the boat first crossed the circle; it is
        // preserved while outside and cleared the moment the boat comes back inside. The alarm
        // decision itself is made by the ticker (see evaluateAlarm), not here, so it is not
        // dependent on how frequently GPS fixes arrive.
        val outsideSince = when {
            !outside -> null
            state.outsideSinceMs != null -> state.outsideSinceMs
            else -> now
        }
        WatchStore.onFix(fix, distanceFt, outsideSince)
        startForeground(NOTIF_ID, buildNotification(WatchStore.snapshot()))
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            WatchStore.setGpsEnabled(true)
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            WatchStore.setGpsEnabled(false)
        }
    }

    /**
     * Re-evaluate the alarm on a fixed wall-clock cadence rather than only on GPS fixes.
     * This keeps the dwell countdown correct when the GPS fix rate drops (e.g. poor sky):
     * the boat's last known position and the wall-clock outsideSinceMs are enough to know the
     * boat has been outside longer than the dwell, even before the next fix arrives.
     */
    private fun evaluateAlarm() {
        val state = WatchStore.snapshot()
        val now = System.currentTimeMillis()
        if (shouldFireDragAlarm(
                watching = state.watching,
                alreadyAlarming = state.alarming,
                outsideSinceMs = state.outsideSinceMs,
                nowMs = now,
                dwellMs = dwellMs,
            )
        ) {
            Log.i(TAG, "Alarm firing: outside for ${now - (state.outsideSinceMs ?: now)}ms (dwell=${dwellMs}ms)")
            WatchStore.setAlarming(true)
            alarmPlayer.start()
        }
        startForeground(NOTIF_ID, buildNotification(WatchStore.snapshot()))
    }

    private fun startAlarmTicker() {
        stopAlarmTicker()
        mainHandler.postDelayed(alarmCheckRunnable, ALARM_CHECK_INTERVAL_MS)
    }

    private fun stopAlarmTicker() {
        mainHandler.removeCallbacks(alarmCheckRunnable)
    }

    private fun startGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted; GPS updates not requested")
            return
        }
        stopGps()
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    this,
                    Looper.getMainLooper()
                )
            } else {
                Log.w(TAG, "GPS_PROVIDER not enabled; no location updates")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "requestLocationUpdates denied by system", e)
        }
    }

    private fun stopGps() {
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            Log.w(TAG, "removeUpdates failed (likely no active updates)", e)
        }
    }

    private fun stopWatchInternal() {
        stopAlarmTicker()
        alarmPlayer.stop()
        stopGps()
        WatchStore.stopWatch()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun lastKnownFix(): GeoFix? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return WatchStore.snapshot().boat
        }
        val loc = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            Log.w(TAG, "getLastKnownLocation denied", e)
            null
        }
        return if (loc != null) {
            GeoFix(loc.latitude, loc.longitude, loc.accuracy, System.currentTimeMillis())
        } else {
            WatchStore.snapshot().boat
        }
    }

    private fun buildNotification(state: WatchState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AnchorWatchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val silence = PendingIntent.getService(
            this,
            2,
            Intent(this, AnchorWatchService::class.java).setAction(ACTION_SILENCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dist = if (state.useFeet) {
            "${state.distanceFt.toInt()} ft"
        } else {
            "${state.distanceM.toInt()} m"
        }
        val radius = if (state.useFeet) {
            "${state.radiusFt} ft"
        } else {
            "${state.radiusM.toInt()} m"
        }

        return if (state.alarming) {
            NotificationCompat.Builder(this, AnchorWatchApp.CHANNEL_ALARM)
                .setSmallIcon(R.drawable.ic_stat_anchor)
                .setContentTitle(getString(R.string.alarm_notification_title))
                .setContentText("Boat is $dist from the hook (limit $radius)")
                .setContentIntent(open)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
                .addAction(0, "Silence", silence)
                .addAction(0, "Weigh anchor", stop)
                .build()
        } else {
            NotificationCompat.Builder(this, AnchorWatchApp.CHANNEL_WATCH)
                .setSmallIcon(R.drawable.ic_stat_anchor)
                .setContentTitle(getString(R.string.watch_notification_title))
                .setContentText("Swing $dist / $radius")
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .addAction(0, "Weigh anchor", stop)
                .build()
        }
    }

    companion object {
        const val ACTION_START = "com.jimgrok.anchorwatch.START"
        const val ACTION_STOP = "com.jimgrok.anchorwatch.STOP"
        const val ACTION_SILENCE = "com.jimgrok.anchorwatch.SILENCE"
        const val ACTION_TEST_ALARM = "com.jimgrok.anchorwatch.TEST_ALARM"
        const val EXTRA_RADIUS_FT = "radius_ft"
        const val EXTRA_DWELL_MS = "dwell_ms"
        const val DEFAULT_DWELL_MS = 8_000L
        private const val NOTIF_ID = 42
        private const val ALARM_CHECK_INTERVAL_MS = 1_000L
        private const val TAG = "AnchorWatchService"

        fun start(context: Context, radiusFt: Int, dwellMs: Long = DEFAULT_DWELL_MS) {
            val intent = Intent(context, AnchorWatchService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RADIUS_FT, radiusFt)
                putExtra(EXTRA_DWELL_MS, dwellMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AnchorWatchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun silence(context: Context) {
            val intent = Intent(context, AnchorWatchService::class.java).apply {
                action = ACTION_SILENCE
            }
            context.startService(intent)
        }

        fun testAlarm(context: Context) {
            val intent = Intent(context, AnchorWatchService::class.java).apply {
                action = ACTION_TEST_ALARM
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
