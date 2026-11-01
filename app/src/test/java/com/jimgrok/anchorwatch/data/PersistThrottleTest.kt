package com.jimgrok.anchorwatch.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistThrottleTest {
    private val base = WatchState(
        watching = true,
        anchor = GeoFix(41.5, -81.7, 4f, 1L),
        boat = GeoFix(41.5, -81.7, 4f, 1L),
        radiusFt = 100,
        lastUpdateMs = 1L,
        lastAccurateFixMs = 1L,
    ).toPrefsSnapshot()

    private val last = 1_000_000L

    private fun decide(next: WatchPrefsSnapshot, sinceMs: Long = 1_000L, prev: WatchPrefsSnapshot? = base) =
        shouldPersist(prev, next, last, last + sinceMs)

    @Test fun firstWriteAlwaysPersists() = assertTrue(decide(base, prev = null))

    @Test fun routineFixInsideIntervalIsSkipped() {
        val moved = base.copy(boatLat = "41.50001", lastUpdateMs = 2L, lastAccurateFixMs = 2L)
        assertFalse(decide(moved))
    }

    @Test fun routineFixAfterIntervalPersists() {
        val moved = base.copy(boatLat = "41.50001", lastUpdateMs = 2L)
        assertFalse(decide(moved, sinceMs = PERSIST_INTERVAL_MS - 1))
        assertTrue(decide(moved, sinceMs = PERSIST_INTERVAL_MS))
    }

    @Test fun watchStartStopPersistsImmediately() {
        assertTrue(decide(base.copy(watching = false)))
        assertTrue(decide(WatchState().toPrefsSnapshot(), prev = base))
    }

    @Test fun anchorSetPersistsImmediately() {
        val pending = base.copy(anchorLat = null, anchorLon = null)
        assertTrue(decide(base, prev = pending))
    }

    @Test fun alarmingSilencedGpsLostChangesPersistImmediately() {
        assertTrue(decide(base.copy(alarming = true)))
        assertTrue(decide(base.copy(silenced = true)))
        assertTrue(decide(base.copy(gpsLost = true)))
        assertTrue(decide(base.copy(gpsLost = true), prev = base.copy(gpsLost = false)))
    }

    @Test fun outsideSinceSetOrClearedPersistsImmediately() {
        val outside = base.copy(outsideSinceMs = 5_000L)
        assertTrue(decide(outside))
        assertTrue(decide(base, prev = outside))
    }

    @Test fun outsideSinceStayingSetIsRoutine() {
        val outside = base.copy(outsideSinceMs = 5_000L)
        assertFalse(decide(outside.copy(lastUpdateMs = 9L), prev = outside))
    }

    @Test fun settingsChangesPersistImmediately() {
        assertTrue(decide(base.copy(radiusFt = 120)))
        assertTrue(decide(base.copy(dwellMs = 10_000L)))
        assertTrue(decide(base.copy(useFeet = !base.useFeet)))
    }
}
