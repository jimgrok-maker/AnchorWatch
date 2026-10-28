package com.jimgrok.anchorwatch.alarm

/** No accurate fix for this long raises the one-shot GPS-lost warning. */
const val GPS_LOST_MS = 45_000L

/** Sustained loss of accurate fixes for this long escalates to the full looping alarm. */
const val GPS_LOST_ESCALATE_MS = 120_000L

enum class GpsLostAction { NONE, WARN, ESCALATE }

/**
 * Decide what the 1s ticker should do about missing accurate GPS fixes.
 * Applies while a watch is running, including while waiting for the first fix to drop the hook.
 * [lastAccurateFixMs] is the wall-clock time of the last fix good enough to trust (or the watch
 * start time if none yet). The full alarm is not re-raised while already alarming or silenced.
 */
fun gpsLostAction(
    watching: Boolean,
    alreadyLost: Boolean,
    alarming: Boolean,
    silenced: Boolean,
    lastAccurateFixMs: Long,
    nowMs: Long,
): GpsLostAction {
    if (!watching || lastAccurateFixMs <= 0) return GpsLostAction.NONE
    val sinceMs = nowMs - lastAccurateFixMs
    return when {
        sinceMs >= GPS_LOST_ESCALATE_MS && !alarming && !silenced -> GpsLostAction.ESCALATE
        sinceMs > GPS_LOST_MS && !alreadyLost -> GpsLostAction.WARN
        else -> GpsLostAction.NONE
    }
}

/** A persisted alarming state must restart the siren when the service is recreated. */
fun shouldResumeAlarm(watching: Boolean, alarming: Boolean): Boolean = watching && alarming
