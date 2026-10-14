package com.jimgrok.anchorwatch.data

/** Cached fixes older than this are not trusted to drop the hook. */
const val MAX_LAST_KNOWN_FIX_AGE_MS = 10_000L

/** True when a fix stamped at [fixElapsedNanos] (elapsedRealtimeNanos clock) is recent enough. */
fun isFreshFix(
    fixElapsedNanos: Long,
    nowElapsedNanos: Long,
    maxAgeMs: Long = MAX_LAST_KNOWN_FIX_AGE_MS,
): Boolean {
    if (fixElapsedNanos <= 0) return false
    val ageMs = (nowElapsedNanos - fixElapsedNanos) / 1_000_000L
    return ageMs in 0..maxAgeMs
}
