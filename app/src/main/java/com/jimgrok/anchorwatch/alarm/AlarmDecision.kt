package com.jimgrok.anchorwatch.alarm

/**
 * Wall-clock dwell decision used by the 1s alarm ticker.
 * A drag alarm fires only when a watch is running, not already alarming, and the boat has
 * been outside the swing circle for at least [dwellMs].
 */
fun shouldFireDragAlarm(
    watching: Boolean,
    alreadyAlarming: Boolean,
    outsideSinceMs: Long?,
    nowMs: Long,
    dwellMs: Long,
): Boolean {
    if (!watching || alreadyAlarming) return false
    val since = outsideSinceMs ?: return false
    return nowMs - since >= dwellMs
}
