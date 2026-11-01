package com.jimgrok.anchorwatch.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAlarmDecisionTest {
    @Test fun testStartsWhenIdle() = assertTrue(shouldStartTestAlarm(watching = false, alarming = false))
    @Test fun testStartsWhileWatchingQuietly() = assertTrue(shouldStartTestAlarm(watching = true, alarming = false))
    @Test fun testDoesNotStartOverRealAlarm() = assertFalse(shouldStartTestAlarm(watching = true, alarming = true))

    @Test fun silenceEndsIdleTestOnly() =
        assertEquals(SilenceAction.END_TEST, silenceAction(watching = false, alarming = false, testActive = true))

    @Test fun silenceDuringTestWhileWatchingDoesNotTouchWatch() =
        assertEquals(SilenceAction.END_TEST, silenceAction(watching = true, alarming = false, testActive = true))

    @Test fun silenceWithRealAlarmSilencesTheWatch() {
        assertEquals(SilenceAction.SILENCE_WATCH, silenceAction(watching = true, alarming = true, testActive = true))
        assertEquals(SilenceAction.SILENCE_WATCH, silenceAction(watching = true, alarming = true, testActive = false))
    }

    @Test fun silenceWhileWatchingWithoutTest() =
        assertEquals(SilenceAction.SILENCE_WATCH, silenceAction(watching = true, alarming = false, testActive = false))

    @Test fun silenceWhenIdleDoesNothing() =
        assertEquals(SilenceAction.NONE, silenceAction(watching = false, alarming = false, testActive = false))
}
