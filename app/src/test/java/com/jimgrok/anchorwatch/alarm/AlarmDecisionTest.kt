package com.jimgrok.anchorwatch.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDecisionTest {
    private val dwell = 8_000L
    private val outsideSince = 1_000_000L

    @Test
    fun doesNotFireBeforeDwell() {
        assertFalse(
            shouldFireDragAlarm(
                watching = true,
                alreadyAlarming = false,
                outsideSinceMs = outsideSince,
                nowMs = outsideSince + dwell - 1,
                dwellMs = dwell,
            )
        )
    }

    @Test
    fun firesAtExactDwell() {
        assertTrue(
            shouldFireDragAlarm(
                watching = true,
                alreadyAlarming = false,
                outsideSinceMs = outsideSince,
                nowMs = outsideSince + dwell,
                dwellMs = dwell,
            )
        )
    }

    @Test
    fun firesAfterDwell() {
        assertTrue(
            shouldFireDragAlarm(
                watching = true,
                alreadyAlarming = false,
                outsideSinceMs = outsideSince,
                nowMs = outsideSince + dwell + 5_000,
                dwellMs = dwell,
            )
        )
    }

    @Test
    fun doesNotFireWhenNotWatching() {
        assertFalse(
            shouldFireDragAlarm(
                watching = false,
                alreadyAlarming = false,
                outsideSinceMs = outsideSince,
                nowMs = outsideSince + dwell,
                dwellMs = dwell,
            )
        )
    }

    @Test
    fun doesNotRefireWhenAlreadyAlarming() {
        assertFalse(
            shouldFireDragAlarm(
                watching = true,
                alreadyAlarming = true,
                outsideSinceMs = outsideSince,
                nowMs = outsideSince + dwell,
                dwellMs = dwell,
            )
        )
    }

    @Test
    fun doesNotFireWithoutOutsideSince() {
        assertFalse(
            shouldFireDragAlarm(
                watching = true,
                alreadyAlarming = false,
                outsideSinceMs = null,
                nowMs = outsideSince + dwell,
                dwellMs = dwell,
            )
        )
    }
}
