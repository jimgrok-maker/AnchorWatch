package com.jimgrok.anchorwatch.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jimgrok.anchorwatch.AnchorWatchApp
import com.jimgrok.anchorwatch.MainActivity
import com.jimgrok.anchorwatch.R
import com.jimgrok.anchorwatch.alarm.AlarmPlayer
import com.jimgrok.anchorwatch.alarm.GPS_LOST_ESCALATE_MS
import com.jimgrok.anchorwatch.alarm.GPS_LOST_MS
import com.jimgrok.anchorwatch.alarm.GpsLostAction
import com.jimgrok.anchorwatch.alarm.SilenceAction
import com.jimgrok.anchorwatch.alarm.TEST_ALARM_DURATION_MS
import com.jimgrok.anchorwatch.alarm.gpsLostAction
import com.jimgrok.anchorwatch.alarm.shouldResumeAlarm
import com.jimgrok.anchorwatch.alarm.shouldStartTestAlarm
import com.jimgrok.anchorwatch.alarm.silenceAction
import com.jimgrok.anchorwatch.alarm.shouldFireDragAlarm
import com.jimgrok.anchorwatch.data.GeoFix
import com.jimgrok.anchorwatch.data.WatchState
import com.jimgrok.anchorwatch.data.WatchStore
import com.jimgrok.anchorwatch.data.haversineFt
import com.jimgrok.anchorwatch.data.isAccurateEnough
import com.jimgrok.anchorwatch.data.isFreshFix

class AnchorWatchService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var alarmPlayer: AlarmPlayer

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    /** True while the transient "Test alarm" is sounding; never persisted. */
    private var testActive = false
    private var lastNotification: NotificationModel? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val testStopRunnable = Runnable { endTest() }
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
        val action = intent?.action
        val state = WatchStore.snapshot()

        // Every start (including STOP/SILENCE sent through PendingIntent.getForegroundService)
        // must reach startForeground promptly, before any branching, or Android kills the app.
        val inForeground = enterForeground(action, state)

        when (action) {
            ACTION_STOP -> {
                stopWatchInternal()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SILENCE -> return handleSilence()
            ACTION_TEST_ALARM -> {
                if (inForeground) return handleTestAlarm()
                if (state.watching) return START_STICKY // never drop a running watch over a test
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!inForeground) return abandonStart(state)

        // A null intent is a sticky restart; never arm a new watch for it if none was running.
        if (intent == null && !state.watching) {
            Log.w(TAG, "Sticky restart with no persisted watch; stopping")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        endTest(stopIfIdle = false)
        val radius = intent?.getIntExtra(EXTRA_RADIUS_FT, state.radiusFt) ?: state.radiusFt
        // A dwell sent with START wins; otherwise keep the stored one (e.g. after a sticky restart).
        if (intent?.hasExtra(EXTRA_DWELL_MS) == true) {
            WatchStore.setDwellMs(intent.getLongExtra(EXTRA_DWELL_MS, WatchState.DEFAULT_DWELL_MS))
        }

        if (state.watching && (state.anchor != null || state.alarming)) {
            Log.i(TAG, "Resuming persisted watch (anchor=${state.anchor?.latitude},${state.anchor?.longitude}, radius=${state.radiusFt}ft, alarming=${state.alarming})")
            WatchStore.setRadius(radius)
            if (state.lastAccurateFixMs <= 0) WatchStore.markAccurateFixClock(System.currentTimeMillis())
            if (shouldResumeAlarm(state.watching, state.alarming)) {
                Log.w(TAG, "Persisted watch was alarming; restarting the alarm")
                alarmPlayer.start()
            }
            updateNotification(WatchStore.snapshot())
            startGps()
            startAlarmTicker()
            return START_STICKY
        }

        val seed = lastKnownFix()
        if (seed != null && isAccurateEnough(seed, radius)) {
            WatchStore.startWatch(seed, radius)
        } else {
            Log.i(TAG, "No accurate fix yet; waiting for the first good GPS fix to drop the hook")
            WatchStore.startWatchPending(radius)
        }

        updateNotification(WatchStore.snapshot())
        startGps()
        startAlarmTicker()
        return START_STICKY
    }

    override fun onDestroy() {
        stopAlarmTicker()
        mainHandler.removeCallbacks(testStopRunnable)
        stopGps()
        alarmPlayer.stop()
        notificationManager.cancel(TEST_NOTIF_ID)
        WatchStore.flush()
        super.onDestroy()
    }

    /**
     * Promote to foreground with the notification that fits [action]. Returns false (after
     * logging) if Android refuses, e.g. ForegroundServiceStartNotAllowedException on API 31+
     * when a start arrives while the app may not start foreground services.
     */
    private fun enterForeground(action: String?, state: WatchState): Boolean {
        val testWhileIdle = !state.watching &&
            (action == ACTION_TEST_ALARM || (action == ACTION_SILENCE && testActive))
        val model = notificationModel(state)
        val notification = if (testWhileIdle) buildTestNotification() else buildNotification(state, model)
        return try {
            startForeground(NOTIF_ID, notification)
            lastNotification = if (testWhileIdle) null else model
            true
        } catch (e: IllegalStateException) {
            val notAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            Log.e(TAG, if (notAllowed) "Foreground service start not allowed (action=$action)" else "startForeground failed (action=$action)", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "startForeground rejected, missing permission? (action=$action)", e)
            false
        }
    }

    /**
     * We could not become a foreground service, so the watch cannot be kept alive. Do not leave
     * persisted state claiming a watch is running; tell the user to start it again.
     */
    private fun abandonStart(state: WatchState): Int {
        if (state.watching) {
            WatchStore.stopWatch()
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationManager.notify(
                DROPPED_NOTIF_ID,
                NotificationCompat.Builder(this, AnchorWatchApp.CHANNEL_ALARM)
                    .setSmallIcon(R.drawable.ic_stat_anchor)
                    .setContentTitle("Anchor watch stopped")
                    .setContentText("Android would not keep the watch running in the background. Open AnchorWatch and start it again.")
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .build()
            )
        }
        stopSelf()
        return START_NOT_STICKY
    }

    private fun handleSilence(): Int {
        val state = WatchStore.snapshot()
        return when (silenceAction(state.watching, state.alarming, testActive)) {
            SilenceAction.END_TEST -> {
                endTest()
                if (state.watching) START_STICKY else START_NOT_STICKY
            }
            SilenceAction.SILENCE_WATCH -> {
                endTest()
                WatchStore.silenceCurrentDrag()
                alarmPlayer.stop()
                updateNotification(WatchStore.snapshot())
                START_STICKY
            }
            SilenceAction.NONE -> {
                // Stale Silence with nothing running: we only started to satisfy startForeground.
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                START_NOT_STICKY
            }
        }
    }

    /**
     * Play the alarm for [TEST_ALARM_DURATION_MS] with a "Test alarm" notification. This is
     * transient: nothing is written to WatchStore, and an active watch is left alone.
     */
    private fun handleTestAlarm(): Int {
        val state = WatchStore.snapshot()
        if (!shouldStartTestAlarm(state.watching, state.alarming)) return START_STICKY
        testActive = true
        alarmPlayer.start()
        // Idle: the test notification is already the foreground one. Watching: show it beside
        // the watch notification instead of replacing it.
        if (state.watching) notificationManager.notify(TEST_NOTIF_ID, buildTestNotification())
        mainHandler.removeCallbacks(testStopRunnable)
        mainHandler.postDelayed(testStopRunnable, TEST_ALARM_DURATION_MS)
        return if (state.watching) START_STICKY else START_NOT_STICKY
    }

    /**
     * Stop a running test alarm. With no watch active the service has nothing left to do, so it
     * stops too, unless [stopIfIdle] is false (a watch is about to start).
     */
    private fun endTest(stopIfIdle: Boolean = true) {
        if (!testActive) return
        testActive = false
        mainHandler.removeCallbacks(testStopRunnable)
        val state = WatchStore.snapshot()
        // If a real alarm began meanwhile it shares the player and must keep sounding.
        if (!(state.watching && state.alarming)) alarmPlayer.stop()
        notificationManager.cancel(TEST_NOTIF_ID)
        if (stopIfIdle && !state.watching) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
        if (state.anchor == null) {
            if (isAccurateEnough(fix, state.radiusFt)) {
                Log.i(TAG, "First accurate fix; dropping the hook")
                WatchStore.startWatch(fix, state.radiusFt)
            } else {
                WatchStore.setPendingBoat(fix)
            }
            updateNotification(WatchStore.snapshot())
            return
        }
        val anchor = state.anchor
        val distanceFt = haversineFt(
            anchor.latitude, anchor.longitude, fix.latitude, fix.longitude
        )
        val now = System.currentTimeMillis()
        val accurate = isAccurateEnough(fix, state.radiusFt)
        val outside = accurate && distanceFt > state.radiusFt
        if (!outside && state.silenced && accurate) {
            WatchStore.clearSilence()
        }
        val silenced = if (!outside) false else state.silenced
        val outsideSince = when {
            !accurate -> state.outsideSinceMs
            !outside -> null
            silenced -> null
            state.outsideSinceMs != null -> state.outsideSinceMs
            else -> now
        }
        WatchStore.onFix(fix, distanceFt, outsideSince, accurate)
        updateNotification(WatchStore.snapshot())
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            WatchStore.setGpsEnabled(true)
            // Re-register in case the earlier request was dropped while GPS was off.
            if (WatchStore.snapshot().watching) startGps()
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) WatchStore.setGpsEnabled(false)
    }

    private fun evaluateAlarm() {
        val before = WatchStore.snapshot()
        val now = System.currentTimeMillis()
        when (gpsLostAction(
            watching = before.watching,
            alreadyLost = before.gpsLost,
            alarming = before.alarming,
            silenced = before.silenced,
            lastAccurateFixMs = before.lastAccurateFixMs,
            nowMs = now,
        )) {
            GpsLostAction.WARN -> {
                Log.w(TAG, "GPS lost: no accurate fix for ${now - before.lastAccurateFixMs}ms")
                WatchStore.setGpsLost(true)
                alarmPlayer.warnOnce()
            }
            GpsLostAction.ESCALATE -> {
                Log.w(TAG, "GPS lost for ${now - before.lastAccurateFixMs}ms; escalating to full alarm")
                WatchStore.setGpsLost(true)
                WatchStore.setAlarming(true)
                alarmPlayer.start()
            }
            GpsLostAction.NONE -> Unit
        }
        val state = WatchStore.snapshot()
        if (shouldFireDragAlarm(
                watching = state.watching,
                alreadyAlarming = state.alarming,
                silenced = state.silenced,
                outsideSinceMs = state.outsideSinceMs,
                nowMs = now,
                dwellMs = state.dwellMs,
            )
        ) {
            Log.i(TAG, "Alarm firing: outside for ${now - (state.outsideSinceMs ?: now)}ms (dwell=${state.dwellMs}ms)")
            WatchStore.setAlarming(true)
            alarmPlayer.start()
        }
        updateNotification(WatchStore.snapshot())
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
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.w(TAG, "GPS_PROVIDER disabled; registering anyway so fixes arrive once it is enabled")
                WatchStore.setGpsEnabled(false)
            }
            // stopGps() above removes any prior registration, so this never double-registers.
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                this,
                Looper.getMainLooper()
            )
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
        testActive = false
        mainHandler.removeCallbacks(testStopRunnable)
        alarmPlayer.stop()
        stopGps()
        WatchStore.stopWatch()
        notificationManager.cancel(TEST_NOTIF_ID)
        lastNotification = null
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
        } catch (e: SecurityException) {
            Log.w(TAG, "getLastKnownLocation denied", e)
            null
        }
        return if (loc != null && isFreshFix(loc.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos())) {
            GeoFix(loc.latitude, loc.longitude, loc.accuracy, loc.time)
        } else {
            WatchStore.snapshot().boat
        }
    }

    /** Update the ongoing notification only when what it displays has changed. */
    private fun updateNotification(state: WatchState) {
        val model = notificationModel(state)
        if (model == lastNotification) return
        lastNotification = model
        notificationManager.notify(NOTIF_ID, buildNotification(state, model))
    }

    private fun serviceIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getForegroundService(
            this, requestCode, Intent(this, AnchorWatchService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun buildTestNotification(): Notification =
        NotificationCompat.Builder(this, AnchorWatchApp.CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentTitle("Test alarm")
            .setContentText("Playing the alarm sound for ${TEST_ALARM_DURATION_MS / 1000} s. This is only a test.")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Silence", serviceIntent(2, ACTION_SILENCE))
            .build()

    private fun buildNotification(state: WatchState, model: NotificationModel): Notification {
        val open = openAppIntent()
        val stop = serviceIntent(1, ACTION_STOP)
        val silence = serviceIntent(2, ACTION_SILENCE)
        val dist = model.distance
        val radius = model.radius
        return when (model.kind) {
            NotificationKind.DRAGGING, NotificationKind.GPS_LOST_ALARM -> {
                val gpsLost = model.kind == NotificationKind.GPS_LOST_ALARM
                NotificationCompat.Builder(this, AnchorWatchApp.CHANNEL_ALARM)
                    .setSmallIcon(R.drawable.ic_stat_anchor)
                    .setContentTitle(if (gpsLost) "GPS lost" else getString(R.string.alarm_notification_title))
                    .setContentText(
                        if (gpsLost) "No accurate GPS fix for ${GPS_LOST_ESCALATE_MS / 60_000} min. Check the boat."
                        else "Boat is $dist from the hook (limit $radius)"
                    )
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(open, true)
                    .addAction(0, "Silence", silence)
                    .build()
            }
            NotificationKind.GPS_LOST -> {
                NotificationCompat.Builder(this, AnchorWatchApp.CHANNEL_ALARM)
                    .setSmallIcon(R.drawable.ic_stat_anchor)
                    .setContentTitle("GPS lost")
                    .setContentText("No accurate fix for ${GPS_LOST_MS / 1000}s. Check the sky and the GPS lamp.")
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .addAction(0, "Stop watch", stop)
                    .build()
            }
            NotificationKind.WAITING -> {
                NotificationCompat.Builder(this, AnchorWatchApp.CHANNEL_WATCH)
                    .setSmallIcon(R.drawable.ic_stat_anchor)
                    .setContentTitle(getString(R.string.watch_notification_title))
                    .setContentText("Waiting for a good GPS fix to drop the hook")
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .addAction(0, "Stop watch", stop)
                    .build()
            }
            NotificationKind.SILENCED, NotificationKind.WATCHING -> {
                NotificationCompat.Builder(this, AnchorWatchApp.CHANNEL_WATCH)
                    .setSmallIcon(R.drawable.ic_stat_anchor)
                    .setContentTitle(getString(R.string.watch_notification_title))
                    .setContentText(
                        if (model.kind == NotificationKind.SILENCED) "Silenced until back inside  /  $dist / $radius"
                        else "Swing $dist / $radius"
                    )
                    .setContentIntent(open)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .addAction(0, "Stop watch", stop)
                    .build()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.jimgrok.anchorwatch.START"
        const val ACTION_STOP = "com.jimgrok.anchorwatch.STOP"
        const val ACTION_SILENCE = "com.jimgrok.anchorwatch.SILENCE"
        const val ACTION_TEST_ALARM = "com.jimgrok.anchorwatch.TEST_ALARM"
        const val EXTRA_RADIUS_FT = "radius_ft"
        const val EXTRA_DWELL_MS = "dwell_ms"
        const val DEFAULT_DWELL_MS = WatchState.DEFAULT_DWELL_MS
        private const val NOTIF_ID = 42
        private const val TEST_NOTIF_ID = 43
        private const val DROPPED_NOTIF_ID = 44
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
            // onStartCommand always calls startForeground first, so the foreground variant is safe.
            ContextCompat.startForegroundService(
                context,
                Intent(context, AnchorWatchService::class.java).setAction(ACTION_STOP)
            )
        }

        fun silence(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AnchorWatchService::class.java).setAction(ACTION_SILENCE)
            )
        }

        fun testAlarm(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AnchorWatchService::class.java).setAction(ACTION_TEST_ALARM)
            )
        }
    }
}
