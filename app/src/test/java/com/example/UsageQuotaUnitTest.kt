package com.example

import com.example.ai.AiCapabilityTier
import com.example.ai.UsageQuotaSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageQuotaUnitTest {

    @Test
    fun defaultSnapshotStartsHealthyWithFullAllowance() {
        val snapshot = UsageQuotaSnapshot(dateIso = "2026-11-25")
        assertEquals(10, snapshot.totalAllowance)
        assertEquals(10, snapshot.remainingCredits)
        assertEquals(30, snapshot.remainingEcoLiteCalls)
        assertEquals(0f, snapshot.usageRatio, 0.001f)
        assertEquals(1f, snapshot.remainingRatio, 0.001f)
        assertFalse(snapshot.isSoftNudgeThreshold)
        assertFalse(snapshot.isExhausted)
        assertEquals(AiCapabilityTier.TIER_1_PRIMARY, snapshot.activeCapabilityTier)
    }

    @Test
    fun ecoLiteModeStretchesRemainingAllowanceThreeTimes() {
        // 2 Eco Lite calls consume 2 units out of 30 -> 28 Eco calls left, 10 ceil standard credits
        val afterTwoEcoCalls = UsageQuotaSnapshot(
            dateIso = "2026-11-25",
            chatUsed = 2,
            ecoLiteMode = true,
            creditUnitsUsed = 2
        )
        assertEquals(28, afterTwoEcoCalls.remainingEcoLiteCalls)
        assertEquals(10, afterTwoEcoCalls.remainingCredits)
        assertEquals(AiCapabilityTier.TIER_2_ECO_LITE, afterTwoEcoCalls.activeCapabilityTier)
        assertFalse(afterTwoEcoCalls.isExhausted)

        // 3 Eco Lite calls consume exactly 1 full daily credit (3 units) -> 9 credits / 27 Eco calls
        val afterThreeEcoCalls = afterTwoEcoCalls.copy(chatUsed = 3, creditUnitsUsed = 3)
        assertEquals(27, afterThreeEcoCalls.remainingEcoLiteCalls)
        assertEquals(9, afterThreeEcoCalls.remainingCredits)

        // 1 remaining Eco unit still keeps user non-exhausted
        val lastEcoUnit = UsageQuotaSnapshot(
            dateIso = "2026-11-25",
            chatUsed = 29,
            ecoLiteMode = true,
            creditUnitsUsed = 29
        )
        assertEquals(1, lastEcoUnit.remainingEcoLiteCalls)
        assertEquals(1, lastEcoUnit.remainingCredits)
        assertFalse(lastEcoUnit.isExhausted)
        assertEquals(AiCapabilityTier.TIER_2_ECO_LITE, lastEcoUnit.activeCapabilityTier)
    }

    @Test
    fun softNudgeAndExhaustionThresholdsTransitionCapabilityTiers() {
        val softNudge = UsageQuotaSnapshot(
            dateIso = "2026-11-25",
            visionUsed = 3,
            recipeUsed = 3,
            chatUsed = 2 // 8 of 10 standard credits = 80%
        )
        assertEquals(2, softNudge.remainingCredits)
        assertEquals(6, softNudge.remainingEcoLiteCalls)
        assertTrue(softNudge.isSoftNudgeThreshold)
        assertFalse(softNudge.isExhausted)
        assertEquals(AiCapabilityTier.TIER_2_ECO_LITE, softNudge.activeCapabilityTier)

        val exhausted = UsageQuotaSnapshot(
            dateIso = "2026-11-25",
            visionUsed = 4,
            recipeUsed = 3,
            chatUsed = 3
        )
        assertEquals(0, exhausted.remainingCredits)
        assertEquals(0, exhausted.remainingEcoLiteCalls)
        assertTrue(exhausted.isExhausted)
        assertEquals(AiCapabilityTier.TIER_3_LOCAL_FALLBACK, exhausted.activeCapabilityTier)

        // Claiming +3 bonus grace credits lifts exhausted state back to active
        val withGrace = exhausted.copy(bonusGraceCredits = 3, graceClaimed = true)
        assertEquals(13, withGrace.totalAllowance)
        assertEquals(3, withGrace.remainingCredits)
        assertEquals(9, withGrace.remainingEcoLiteCalls)
        assertFalse(withGrace.isExhausted)
    }

    @Test
    fun dailyRolloverResetsUsageAndPreservesEcoLitePreference() {
        val yesterday = UsageQuotaSnapshot(
            dateIso = "2026-11-24",
            visionUsed = 4,
            recipeUsed = 3,
            chatUsed = 2,
            audioUsed = 1,
            bonusGraceCredits = 3,
            graceClaimed = true,
            ecoLiteMode = true,
            forceLocalOfflineMode = false,
            fallbackCascadeCount = 2,
            queuedScansCount = 1,
            creditUnitsUsed = 30
        )

        val today = yesterday.rolloverForDate("2026-11-25")
        assertEquals("2026-11-25", today.dateIso)
        assertEquals(0, today.totalUsed)
        assertEquals(0, today.effectiveCreditUnitsUsed)
        assertEquals(10, today.remainingCredits)
        assertEquals(30, today.remainingEcoLiteCalls)
        assertEquals(0, today.bonusGraceCredits)
        assertFalse(today.graceClaimed)
        assertEquals(0, today.fallbackCascadeCount)
        // Preferences and queued work survive midnight rollover
        assertTrue(today.ecoLiteMode)
        assertEquals(1, today.queuedScansCount)
        assertEquals(AiCapabilityTier.TIER_2_ECO_LITE, today.activeCapabilityTier)
    }
}
