package com.jimgrok.anchorwatch.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object WatchStore {
    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var initialized = false

    fun snapshot(): WatchState = _state.value

    /**
     * Load the persisted watch state into memory. Call this from Application.onCreate so
     * the state is restored before the foreground service is (re)started by the OS.
     */
    fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences("anchor_watch", Context.MODE_PRIVATE)
        initialized = true
        restore()
    }

    fun setRadius(feet: Int) {
        _state.update { it.copy(radiusFt = feet.coerceIn(20, 600)) }
        persist(_state.value)
    }

    fun setUseFeet(useFeet: Boolean) {
        _state.update { it.copy(useFeet = useFeet) }
        persist(_state.value)
    }

    fun startWatch(anchor: GeoFix, radiusFt: Int) {
        _state.value = WatchState(
            watching = true,
            alarming = false,
            anchor = anchor,
            boat = anchor,
            track = listOf(anchor),
            radiusFt = radiusFt,
            distanceFt = 0.0,
            outsideSinceMs = null,
            gpsEnabled = true,
            lastUpdateMs = anchor.timeMs,
            useFeet = _state.value.useFeet
        )
        persist(_state.value)
    }

    fun stopWatch() {
        _state.update {
            it.copy(
                watching = false,
                alarming = false,
                outsideSinceMs = null
            )
        }
        persist(_state.value)
    }

    fun setAlarming(on: Boolean) {
        _state.update { it.copy(alarming = on) }
        persist(_state.value)
    }

    fun setGpsEnabled(enabled: Boolean) {
        _state.update { it.copy(gpsEnabled = enabled) }
    }

    fun onFix(fix: GeoFix, distanceFt: Double, outsideSinceMs: Long?) {
        _state.update { current ->
            val last = current.track.lastOrNull()
            val movedEnough = last == null ||
                haversineFt(last.latitude, last.longitude, fix.latitude, fix.longitude) >= 3.0
            val nextTrack = if (!current.watching) {
                current.track
            } else if (movedEnough) {
                (current.track + fix).takeLast(2500)
            } else {
                current.track
            }
            current.copy(
                boat = fix,
                track = nextTrack,
                distanceFt = distanceFt,
                outsideSinceMs = outsideSinceMs,
                lastUpdateMs = fix.timeMs
            )
        }
        // Persist on every fix (1 Hz): cheap, and keeps the hook, radius, dwell anchor and
        // outsideSinceMs across an OS process restart so the watch resumes, not restarts.
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
        val anchorLat = p.getString(K_ANCHOR_LAT, null)
        val anchorLon = p.getString(K_ANCHOR_LON, null)
        val boatLat = p.getString(K_BOAT_LAT, null)
        val boatLon = p.getString(K_BOAT_LON, null)
        _state.value = WatchState(
            watching = p.getBoolean(K_WATCHING, false),
            alarming = p.getBoolean(K_ALARMING, false),
            anchor = anchorLat?.let { lat ->
                anchorLon?.let { lon ->
                    GeoFix(lat.toDouble(), lon.toDouble(), 0f, 0L)
                }
            },
            boat = boatLat?.let { lat ->
                boatLon?.let { lon ->
                    GeoFix(lat.toDouble(), lon.toDouble(), 0f, 0L)
                }
            },
            track = emptyList(),
            radiusFt = p.getInt(K_RADIUS_FT, 100),
            distanceFt = 0.0,
            outsideSinceMs = p.getLong(K_OUTSIDE_SINCE_MS, -1L).takeIf { it >= 0 },
            gpsEnabled = p.getBoolean(K_GPS_ENABLED, true),
            lastUpdateMs = p.getLong(K_LAST_UPDATE_MS, 0L),
            useFeet = p.getBoolean(K_USE_FEET, true)
        )
    }

    private fun persist(state: WatchState) {
        val p = prefs ?: return
        p.edit()
            .putBoolean(K_WATCHING, state.watching)
            .putBoolean(K_ALARMING, state.alarming)
            .putString(K_ANCHOR_LAT, state.anchor?.latitude?.toString())
            .putString(K_ANCHOR_LON, state.anchor?.longitude?.toString())
            .putString(K_BOAT_LAT, state.boat?.latitude?.toString())
            .putString(K_BOAT_LON, state.boat?.longitude?.toString())
            .putInt(K_RADIUS_FT, state.radiusFt)
            .putLong(K_OUTSIDE_SINCE_MS, state.outsideSinceMs ?: -1L)
            .putBoolean(K_GPS_ENABLED, state.gpsEnabled)
            .putLong(K_LAST_UPDATE_MS, state.lastUpdateMs)
            .putBoolean(K_USE_FEET, state.useFeet)
            .apply()
    }

    private const val K_WATCHING = "watching"
    private const val K_ALARMING = "alarming"
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

fun haversineFt(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) *
        kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c * WatchState.M_TO_FT
}
