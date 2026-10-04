package com.jimgrok.anchorwatch.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDecisionTest {
    private val dwell = 8_000L
    private val outsideSince = 1_000_000L

    @Test
    fun doesNotFireBeforeDwell() {
        assertFalse(fire(nowMs = outsideSince + dwell - 1))
    }

    @Test
    fun firesAtExactDwell() {
        assertTrue(fire(nowMs = outsideSince + dwell))
    }

    @Test
    fun firesAfterDwell() {
        assertTrue(fire(nowMs = outsideSince + dwell + 5_000))
    }

    @Test
    fun doesNotFireWhenNotWatching() {
        assertFalse(fire(watching = false, nowMs = outsideSince + dwell))
    }

    @Test
    fun doesNotRefireWhenAlreadyAlarming() {
        assertFalse(fire(alreadyAlarming = true, nowMs = outsideSince + dwell))
    }

    @Test
    fun doesNotFireWithoutOutsideSince() {
        assertFalse(fire(outsideSinceMs = null, nowMs = outsideSince + dwell))
    }

    @Test
    fun doesNotFireWhileSilencedForThisExcursion() {
        assertFalse(fire(silenced = true, nowMs = outsideSince + dwell))
    }

    private fun fire(
        watching: Boolean = true,
        alreadyAlarming: Boolean = false,
        silenced: Boolean = false,
        outsideSinceMs: Long? = outsideSince,
        nowMs: Long,
    ) = shouldFireDragAlarm(
        watching = watching,
        alreadyAlarming = alreadyAlarming,
        silenced = silenced,
        outsideSinceMs = outsideSinceMs,
        nowMs = nowMs,
        dwellMs = dwell,
    )
}
