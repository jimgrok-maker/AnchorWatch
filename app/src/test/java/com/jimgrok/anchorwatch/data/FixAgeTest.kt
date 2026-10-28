package com.jimgrok.anchorwatch.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixAgeTest {
    private val now = 500_000_000_000L
    private fun ms(v: Long) = v * 1_000_000L

    @Test fun acceptsRecentFix() = assertTrue(isFreshFix(now - ms(2_000), now))
    @Test fun acceptsFixAtLimit() = assertTrue(isFreshFix(now - ms(MAX_LAST_KNOWN_FIX_AGE_MS), now))
    @Test fun rejectsStaleFix() = assertFalse(isFreshFix(now - ms(MAX_LAST_KNOWN_FIX_AGE_MS + 1), now))
    @Test fun rejectsFutureFix() = assertFalse(isFreshFix(now + ms(1_000), now))
    @Test fun rejectsMissingTimestamp() = assertFalse(isFreshFix(0L, now))
}
