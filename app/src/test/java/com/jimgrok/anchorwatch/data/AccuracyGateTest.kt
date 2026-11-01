package com.jimgrok.anchorwatch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccuracyGateTest {
    private fun fix(accuracyM: Float) = GeoFix(0.0, 0.0, accuracyM, 0L)

    @Test fun limitIsFortyPercentOfRadiusInTheMiddle() {
        // 100 ft = 30.48 m -> 12.19 m
        assertEquals(12.19f, accuracyLimitM(100), 0.01f)
    }

    @Test fun limitNeverExceedsHalfTheRadiusAboveTheFloor() {
        for (ft in 41..500) {
            val radiusM = ft * WatchState.FT_TO_M
            assertTrue("radius $ft ft", accuracyLimitM(ft) <= radiusM * 0.5 + 1e-4)
        }
    }

    @Test fun smallRadiiUseTheFloor() {
        assertEquals(WatchState.ACCURACY_FLOOR_M, accuracyLimitM(20), 0f)
        assertEquals(WatchState.ACCURACY_FLOOR_M, accuracyLimitM(30), 0f)
    }

    @Test fun limitIsCappedAtAbsoluteCeiling() {
        assertEquals(WatchState.MAX_ACCURACY_M, accuracyLimitM(500), 0f)
    }

    @Test fun limitNeverDecreasesAsRadiusGrows() {
        var prev = 0f
        for (ft in 20..500) {
            val limit = accuracyLimitM(ft)
            assertTrue(limit >= prev)
            prev = limit
        }
    }

    @Test fun errorLargerThanFloorIsRejectedAtSmallRadius() {
        // Previously an 8 m error was accepted on a 20 ft (6 m) circle.
        assertFalse(isAccurateEnough(fix(8f), 20))
        assertTrue(isAccurateEnough(fix(4f), 20))
    }

    @Test fun zeroOrNegativeAccuracyIsRejected() {
        assertFalse(isAccurateEnough(fix(0f), 100))
        assertFalse(isAccurateEnough(fix(-1f), 100))
    }

    @Test fun smallRadiusHintThreshold() {
        assertTrue(isRadiusTooSmallForGps(20))
        assertTrue(isRadiusTooSmallForGps(49))
        assertFalse(isRadiusTooSmallForGps(50))
        assertFalse(isRadiusTooSmallForGps(100))
    }
}
