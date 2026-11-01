package com.jimgrok.anchorwatch.data

/** Routine fix/tick updates are written to prefs at most this often. */
const val PERSIST_INTERVAL_MS = 30_000L

/**
 * Decide whether [next] must be written to prefs now. State transitions (watch start/stop, anchor
 * or radius/dwell/units change, alarming/silenced/gpsLost change, outsideSince set or cleared)
 * are always written so a restart can resume safely; everything else (boat position, fix times)
 * is written at most every [intervalMs].
 */
internal fun shouldPersist(
    prev: WatchPrefsSnapshot?,
    next: WatchPrefsSnapshot,
    lastPersistMs: Long,
    nowMs: Long,
    intervalMs: Long = PERSIST_INTERVAL_MS,
): Boolean {
    if (prev == null || isTransition(prev, next)) return true
    return nowMs - lastPersistMs >= intervalMs
}

private fun isTransition(a: WatchPrefsSnapshot, b: WatchPrefsSnapshot): Boolean =
    a.watching != b.watching ||
        a.alarming != b.alarming ||
        a.silenced != b.silenced ||
        a.gpsLost != b.gpsLost ||
        a.anchorLat != b.anchorLat ||
        a.anchorLon != b.anchorLon ||
        a.radiusFt != b.radiusFt ||
        a.dwellMs != b.dwellMs ||
        a.useFeet != b.useFeet ||
        (a.outsideSinceMs >= 0) != (b.outsideSinceMs >= 0)
