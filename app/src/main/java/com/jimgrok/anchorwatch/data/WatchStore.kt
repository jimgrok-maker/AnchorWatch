package com.jimgrok.anchorwatch.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object WatchStore {
    private val _state = MutableStateFlow(WatchState())
    val state: StateFlow<WatchState> = _state.asStateFlow()

    fun snapshot(): WatchState = _state.value

    fun setRadius(feet: Int) {
        _state.update { it.copy(radiusFt = feet.coerceIn(20, 600)) }
    }

    fun setUseFeet(useFeet: Boolean) {
        _state.update { it.copy(useFeet = useFeet) }
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
    }

    fun stopWatch() {
        _state.update {
            it.copy(
                watching = false,
                alarming = false,
                outsideSinceMs = null
            )
        }
    }

    fun setAlarming(on: Boolean) {
        _state.update { it.copy(alarming = on) }
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
    }

    fun previewFix(fix: GeoFix) {
        _state.update { current ->
            if (current.watching) current else current.copy(boat = fix, lastUpdateMs = fix.timeMs)
        }
    }
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
