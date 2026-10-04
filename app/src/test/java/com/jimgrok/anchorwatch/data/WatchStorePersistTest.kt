package com.jimgrok.anchorwatch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchStorePersistTest {
    @Test
    fun roundTripKeepsHookRadiusAndDwell() {
        val original = WatchState(
            watching = true,
            alarming = true,
            anchor = GeoFix(41.5, -81.7, 4f, 123L),
            boat = GeoFix(41.5001, -81.7002, 6f, 456L),
            radiusFt = 150,
            distanceFt = 42.0,
            outsideSinceMs = 9_000L,
            gpsEnabled = false,
            lastUpdateMs = 456L,
            useFeet = false,
        )

        val restored = original.toPrefsSnapshot().toWatchState()

        assertEquals(true, restored.watching)
        assertEquals(true, restored.alarming)
        assertEquals(41.5, restored.anchor?.latitude ?: 0.0, 0.0000001)
        assertEquals(-81.7, restored.anchor?.longitude ?: 0.0, 0.0000001)
        assertEquals(41.5001, restored.boat?.latitude ?: 0.0, 0.0000001)
        assertEquals(-81.7002, restored.boat?.longitude ?: 0.0, 0.0000001)
        assertEquals(150, restored.radiusFt)
        assertEquals(9_000L, restored.outsideSinceMs)
        assertEquals(false, restored.gpsEnabled)
        assertEquals(456L, restored.lastUpdateMs)
        assertEquals(false, restored.useFeet)
        assertEquals(0.0, restored.distanceFt, 0.0)
    }

    @Test
    fun missingAnchorAndSentinelDwellRestoreAsNull() {
        val snap = WatchPrefsSnapshot(
            watching = false,
            alarming = false,
            anchorLat = null,
            anchorLon = null,
            boatLat = null,
            boatLon = null,
            radiusFt = 100,
            outsideSinceMs = -1L,
            gpsEnabled = true,
            lastUpdateMs = 0L,
            useFeet = true,
        )

        val restored = snap.toWatchState()

        assertNull(restored.anchor)
        assertNull(restored.boat)
        assertNull(restored.outsideSinceMs)
    }

    @Test
    fun partialAnchorCoordinatesAreDropped() {
        val snap = WatchState(
            anchor = GeoFix(41.0, -81.0, 1f, 1L),
        ).toPrefsSnapshot().copy(anchorLon = null)

        assertNull(snap.toWatchState().anchor)
    }
}
