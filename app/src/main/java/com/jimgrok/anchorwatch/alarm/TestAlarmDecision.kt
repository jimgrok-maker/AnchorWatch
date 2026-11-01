package com.jimgrok.anchorwatch.alarm

/** The "Test alarm" sound auto-stops after this long if not silenced. */
const val TEST_ALARM_DURATION_MS = 10_000L

/** A test never starts over a real alarm that is already sounding. */
fun shouldStartTestAlarm(watching: Boolean, alarming: Boolean): Boolean = !(watching && alarming)

enum class SilenceAction {
    /** Stop only the transient test alarm; the watch state is not touched. */
    END_TEST,
    /** Silence the real drag alarm for this excursion. */
    SILENCE_WATCH,
    /** Nothing to silence. */
    NONE,
}

/**
 * What a Silence request means. A test alarm is silenced without touching the watch, unless a
 * real alarm is sounding too, in which case the real alarm is what gets silenced.
 */
fun silenceAction(watching: Boolean, alarming: Boolean, testActive: Boolean): SilenceAction = when {
    testActive && !(watching && alarming) -> SilenceAction.END_TEST
    watching -> SilenceAction.SILENCE_WATCH
    else -> SilenceAction.NONE
}
