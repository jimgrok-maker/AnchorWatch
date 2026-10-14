package com.jimgrok.anchorwatch.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsLostDecisionTest {
    private val last = 1_000_000L

    private fun action(
        sinceMs: Long,
        watching: Boolean = true,
        alreadyLost: Boolean = false,
        alarming: Boolean = false,
        silenced: Boolean = false,
        lastAccurateFixMs: Long = last,
    ) = gpsLostAction(watching, alreadyLost, alarming, silenced, lastAccurateFixMs, lastAccurateFixMs + sinceMs)

    @Test fun noneWhileFixesAreRecent() = assertEquals(GpsLostAction.NONE, action(GPS_LOST_MS))
    @Test fun warnsAfterLossThreshold() = assertEquals(GpsLostAction.WARN, action(GPS_LOST_MS + 1))
    @Test fun warnsOnlyOnce() = assertEquals(GpsLostAction.NONE, action(GPS_LOST_MS + 1, alreadyLost = true))
    @Test fun escalatesAfterSustainedLoss() =
        assertEquals(GpsLostAction.ESCALATE, action(GPS_LOST_ESCALATE_MS, alreadyLost = true))
    @Test fun escalatesEvenIfWarningWasMissed() = assertEquals(GpsLostAction.ESCALATE, action(GPS_LOST_ESCALATE_MS))
    @Test fun doesNotReEscalateWhileAlarming() =
        assertEquals(GpsLostAction.NONE, action(GPS_LOST_ESCALATE_MS, alreadyLost = true, alarming = true))
    @Test fun doesNotEscalateWhileSilenced() =
        assertEquals(GpsLostAction.NONE, action(GPS_LOST_ESCALATE_MS, alreadyLost = true, silenced = true))
    @Test fun noneWhenNotWatching() = assertEquals(GpsLostAction.NONE, action(GPS_LOST_ESCALATE_MS, watching = false))
    @Test fun noneWithoutClock() =
        assertEquals(GpsLostAction.NONE, gpsLostAction(true, false, false, false, 0L, last))

    @Test fun resumesAlarmOnlyWhenWatchingAndAlarming() {
        assertTrue(shouldResumeAlarm(watching = true, alarming = true))
        assertFalse(shouldResumeAlarm(watching = true, alarming = false))
        assertFalse(shouldResumeAlarm(watching = false, alarming = true))
    }
}
