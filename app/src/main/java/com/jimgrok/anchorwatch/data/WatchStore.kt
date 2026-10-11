package com.jimgrok.anchorwatch.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object WatchStore {
    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state.asStateFlow()

    private val trackBuffer = java.util.ArrayDeque<GeoFix>()
    private const val MAX_TRACK = 2500
    private val trackVersion = AtomicLong(0)
    val trackVersionLong: Long
        get() = trackVersion.get()

    private var prefs: SharedPreferences? = null
    private var initialized = false

    fun snapshot(): WatchState = _state.value

    fun trackPoints(): List<GeoFix> = ArrayList(trackBuffer)

    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences("anchor_watch", Context.MODE_PRIVATE)
        initialized = true
        restore()
    }

    fun setRadius(feet: Int) {
        _state.update { it.copy(radiusFt = feet.coerceIn(20, 500)) }
        persist(_state.value)
    }

    fun setUseFeet(useFeet: Boolean) {
        _state.update { it.copy(useFeet = useFeet) }
        persist(_state.value)
    }

    /** Start a real watch with a known hook position. */
    fun startWatch(anchor: GeoFix, radiusFt: Int) {
        trackBuffer.clear()
        trackBuffer.addLast(anchor)
        trackVersion.incrementAndGet()
        _state.value = WatchState(
            watching = true,
            alarming = false,
            silenced = false,
            gpsLost = false,
            anchor = anchor,
            boat = anchor,
            radiusFt = radiusFt,
            distanceFt = 0.0,
            outsideSinceMs = null,
            gpsEnabled = true,
            lastUpdateMs = anchor.timeMs,
            useFeet = _state.value.useFeet
        )
        persist(_state.value)
    }

    /** Service is running and waiting for the first accurate fix to drop the hook. */
    fun startWatchPending(radiusFt: Int) {
        trackBuffer.clear()
        trackVersion.incrementAndGet()
        _state.value = WatchState(
            watching = true,
            alarming = false,
            silenced = false,
            gpsLost = false,
            anchor = null,
            boat = _state.value.boat,
            radiusFt = radiusFt.coerceIn(20, 500),
            distanceFt = 0.0,
            outsideSinceMs = null,
            gpsEnabled = true,
            lastUpdateMs = System.currentTimeMillis(),
            useFeet = _state.value.useFeet
        )
        persist(_state.value)
    }

    fun stopWatch() {
        _state.update {
            it.copy(
                watching = false,
                alarming = false,
                silenced = false,
                gpsLost = false,
                outsideSinceMs = null
            )
        }
        trackBuffer.clear()
        trackVersion.incrementAndGet()
        persist(_state.value)
    }

    fun setAlarming(on: Boolean) {
        _state.update { it.copy(alarming = on) }
        persist(_state.value)
    }

    fun setGpsLost(on: Boolean) {
        _state.update { it.copy(gpsLost = on) }
        persist(_state.value)
    }

    /** Mute this excursion. A new dwell starts only after the boat is back inside. */
    fun silenceCurrentDrag() {
        _state.update { it.copy(alarming = false, silenced = true, outsideSinceMs = null) }
        persist(_state.value)
    }

    fun clearSilence() {
        _state.update { it.copy(silenced = false) }
        persist(_state.value)
    }

    fun setGpsEnabled(enabled: Boolean) {
        _state.update { it.copy(gpsEnabled = enabled) }
    }

    fun onFix(fix: GeoFix, distanceFt: Double, outsideSinceMs: Long?) {
        val watching = _state.value.watching
        if (watching) {
            val last = trackBuffer.peekLast()
            val movedEnough = last == null ||
                prefilterFtDelta(last.latitude, last.longitude, fix.latitude, fix.longitude) >= 3.0
            if (movedEnough) {
                trackBuffer.addLast(fix)
                while (trackBuffer.size > MAX_TRACK) {
                    trackBuffer.removeFirst()
                }
                trackVersion.incrementAndGet()
            }
        }
        _state.update {
            it.copy(
                boat = fix,
                distanceFt = distanceFt,
                outsideSinceMs = outsideSinceMs,
                lastUpdateMs = fix.timeMs,
                gpsLost = false
            )
        }
        persist(_state.value)
    }

    /** Update the boat position while waiting for the first good hook fix. */
    fun setPendingBoat(fix: GeoFix) {
        _state.update { it.copy(boat = fix, lastUpdateMs = fix.timeMs, gpsLost = false) }
        persist(_state.value)
    }

    fun previewFix(fix: GeoFix) {
        _state.update { current ->
            if (current.watching) current else current.copy(boat = fix, lastUpdateMs = fix.timeMs)
        }
        persist(_state.value)
    }

    private fun restore() {
        val p = prefs ?: return
        _state.value = WatchPrefsSnapshot(
            watching = p.getBoolean(K_WATCHING, false),
            alarming = p.getBoolean(K_ALARMING, false),
            silenced = p.getBoolean(K_SILENCED, false),
            gpsLost = p.getBoolean(K_GPS_LOST, false),
            anchorLat = p.getString(K_ANCHOR_LAT, null),
            anchorLon = p.getString(K_ANCHOR_LON, null),
            boatLat = p.getString(K_BOAT_LAT, null),
            boatLon = p.getString(K_BOAT_LON, null),
            radiusFt = p.getInt(K_RADIUS_FT, 100),
            outsideSinceMs = p.getLong(K_OUTSIDE_SINCE_MS, -1L),
            gpsEnabled = p.getBoolean(K_GPS_ENABLED, true),
            lastUpdateMs = p.getLong(K_LAST_UPDATE_MS, 0L),
            useFeet = p.getBoolean(K_USE_FEET, true)
        ).toWatchState()
        trackBuffer.clear()
        trackVersion.incrementAndGet()
    }

    private fun persist(state: WatchState) {
        val p = prefs ?: return
        val snap = state.toPrefsSnapshot()
        p.edit()
            .putBoolean(K_WATCHING, snap.watching)
            .putBoolean(K_ALARMING, snap.alarming)
            .putBoolean(K_SILENCED, snap.silenced)
            .putBoolean(K_GPS_LOST, snap.gpsLost)
            .putString(K_ANCHOR_LAT, snap.anchorLat)
            .putString(K_ANCHOR_LON, snap.anchorLon)
            .putString(K_BOAT_LAT, snap.boatLat)
            .putString(K_BOAT_LON, snap.boatLon)
            .putInt(K_RADIUS_FT, snap.radiusFt)
            .putLong(K_OUTSIDE_SINCE_MS, snap.outsideSinceMs)
            .putBoolean(K_GPS_ENABLED, snap.gpsEnabled)
            .putLong(K_LAST_UPDATE_MS, snap.lastUpdateMs)
            .putBoolean(K_USE_FEET, snap.useFeet)
            .apply()
    }

    private const val K_WATCHING = "watching"
    private const val K_ALARMING = "alarming"
    private const val K_SILENCED = "silenced"
    private const val K_GPS_LOST = "gps_lost"
    private const val K_ANCHOR_LAT = "anchor_lat"
    private const val K_ANCHOR_LON = "anchor_lon"
    private const val K_BOAT_LAT = "boat_lat"
    private const val K_BOAT_LON = "boat_lon"
    private const val K_RADIUS_FT = "radius_ft"
    private const val K_OUTSIDE_SINCE_MS = "outside_since_ms"
    private const val K_GPS_ENABLED = "gps_enabled"
    private const val K_LAST_UPDATE_MS = "last_update_ms"
    private const val K_USE_FEET = "use_feet"
}

internal data class WatchPrefsSnapshot(
    val watching: Boolean,
    val alarming: Boolean,
    val silenced: Boolean = false,
    val gpsLost: Boolean = false,
    val anchorLat: String?,
    val anchorLon: String?,
    val boatLat: String?,
    val boatLon: String?,
    val radiusFt: Int,
    val outsideSinceMs: Long,
    val gpsEnabled: Boolean,
    val lastUpdateMs: Long,
    val useFeet: Boolean,
)

internal fun WatchState.toPrefsSnapshot(): WatchPrefsSnapshot = WatchPrefsSnapshot(
    watching = watching,
    alarming = alarming,
    silenced = silenced,
    gpsLost = gpsLost,
    anchorLat = anchor?.latitude?.toString(),
    anchorLon = anchor?.longitude?.toString(),
    boatLat = boat?.latitude?.toString(),
    boatLon = boat?.longitude?.toString(),
    radiusFt = radiusFt,
    outsideSinceMs = outsideSinceMs ?: -1L,
    gpsEnabled = gpsEnabled,
    lastUpdateMs = lastUpdateMs,
    useFeet = useFeet,
)

internal fun WatchPrefsSnapshot.toWatchState(): WatchState = WatchState(
    watching = watching,
    alarming = alarming,
    silenced = silenced,
    gpsLost = gpsLost,
    anchor = parseStoredFix(anchorLat, anchorLon),
    boat = parseStoredFix(boatLat, boatLon),
    radiusFt = radiusFt,
    distanceFt = 0.0,
    outsideSinceMs = outsideSinceMs.takeIf { it >= 0 },
    gpsEnabled = gpsEnabled,
    lastUpdateMs = lastUpdateMs,
    useFeet = useFeet,
)

private fun parseStoredFix(lat: String?, lon: String?): GeoFix? {
    val la = lat?.toDoubleOrNull() ?: return null
    val lo = lon?.toDoubleOrNull() ?: return null
    return GeoFix(la, lo, 0f, 0L)
}

fun haversineFt(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) *
        cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    return r * c * WatchState.M_TO_FT
}

/** True when this fix is tight enough to drop the hook or count toward a drag. */
fun isAccurateEnough(fix: GeoFix, radiusFt: Int): Boolean {
    if (fix.accuracyM <= 0f) return false
    val radiusM = radiusFt * WatchState.FT_TO_M
    val relative = (radiusM * 0.4).toFloat()
    val limit = minOf(WatchState.MAX_ACCURACY_M, relative.coerceAtLeast(8f))
    return fix.accuracyM <= limit
}

/** Equirectangular approximate feet distance used as a 3 ft move gate before appending to the track. */
private fun prefilterFtDelta(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
    return sqrt(dLat * dLat + dLon * dLon) * 6371000.0 * WatchState.M_TO_FT
}
