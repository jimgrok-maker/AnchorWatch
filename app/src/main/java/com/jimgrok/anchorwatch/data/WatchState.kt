package com.jimgrok.anchorwatch.data

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val timeMs: Long,
    val speedMps: Float = 0f,
    val bearing: Float = 0f
)

data class WatchState(
    val watching: Boolean = false,
    val alarming: Boolean = false,
    val anchor: GeoFix? = null,
    val boat: GeoFix? = null,
    val track: List<GeoFix> = emptyList(),
    val radiusFt: Int = 100,
    val distanceFt: Double = 0.0,
    val outsideSinceMs: Long? = null,
    val gpsEnabled: Boolean = true,
    val lastUpdateMs: Long = 0L,
    val useFeet: Boolean = true
) {
    val radiusM: Double get() = radiusFt * FT_TO_M
    val distanceM: Double get() = distanceFt * FT_TO_M

    companion object {
        const val FT_TO_M = 0.3048
        const val M_TO_FT = 3.280839895
    }
}
