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
    val silenced: Boolean = false,
    val gpsLost: Boolean = false,
    val anchor: GeoFix? = null,
    val boat: GeoFix? = null,
    val radiusFt: Int = 100,
    val distanceFt: Double = 0.0,
    val outsideSinceMs: Long? = null,
    val gpsEnabled: Boolean = true,
    val lastUpdateMs: Long = 0L,
    /** Wall-clock time of the last fix accurate enough to trust; drives GPS-lost. */
    val lastAccurateFixMs: Long = 0L,
    val useFeet: Boolean = true
) {
    val radiusM: Double get() = radiusFt * FT_TO_M
    val distanceM: Double get() = distanceFt * FT_TO_M
    val waitingForHook: Boolean get() = watching && anchor == null

    companion object {
        const val FT_TO_M = 0.3048
        const val M_TO_FT = 3.280839895
        /** Absolute accuracy ceiling for dropping the hook or counting a fix as a drag. */
        const val MAX_ACCURACY_M = 30f
        /** Smallest accuracy limit used, however small the radius. */
        const val ACCURACY_FLOOR_M = 5f
        /** Accepted fix error as a fraction of the swing radius. */
        const val ACCURACY_RADIUS_FRACTION = 0.4f
        /** Below this radius GPS error is a large part of the circle; the UI recommends more. */
        const val MIN_RECOMMENDED_RADIUS_FT = 50
    }
}
