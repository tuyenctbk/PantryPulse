package com.example.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Context.quotaDataStore by preferencesDataStore(name = "usage_quota_ledger")

data class UsageQuotaSnapshot(
    val dateIso: String = UsageQuotaManager.currentDateIso(),
    val visionUsed: Int = 0,
    val recipeUsed: Int = 0,
    val chatUsed: Int = 0,
    val audioUsed: Int = 0,
    val baseDailyAllowance: Int = 10,
    val bonusGraceCredits: Int = 0,
    val graceClaimed: Boolean = false,
    val ecoLiteMode: Boolean = false,
    val forceLocalOfflineMode: Boolean = false,
    val fallbackCascadeCount: Int = 0,
    val queuedScansCount: Int = 0
) {
    val totalAllowance: Int
        get() = baseDailyAllowance + bonusGraceCredits

    // In Eco Lite Mode, each interaction costs 1/3 as much effective budget,
    // stretching remaining allowance 3x further.
    val totalUsed: Int
        get() = visionUsed + recipeUsed + chatUsed + audioUsed

    val remainingCredits: Int
        get() = (totalAllowance - totalUsed).coerceAtLeast(0)

    val usageRatio: Float
        get() = if (totalAllowance > 0) {
            (totalUsed.toFloat() / totalAllowance.toFloat()).coerceIn(0f, 1f)
        } else 1f

    val isSoftNudgeThreshold: Boolean
        get() = usageRatio >= 0.80f && remainingCredits > 0

    val isExhausted: Boolean
        get() = remainingCredits <= 0

    val activeCapabilityTier: AiCapabilityTier
        get() = when {
            forceLocalOfflineMode || isExhausted -> AiCapabilityTier.TIER_3_LOCAL_FALLBACK
            ecoLiteMode || isSoftNudgeThreshold -> AiCapabilityTier.TIER_2_ECO_LITE
            else -> AiCapabilityTier.TIER_1_PRIMARY
        }

    val resetCountdownText: String
        get() = UsageQuotaManager.computeResetCountdown()
}

class UsageQuotaManager(private val appContext: Context) {

    private val dateIsoKey = stringPreferencesKey("date_iso")
    private val visionUsedKey = intPreferencesKey("vision_used")
    private val recipeUsedKey = intPreferencesKey("recipe_used")
    private val chatUsedKey = intPreferencesKey("chat_used")
    private val audioUsedKey = intPreferencesKey("audio_used")
    private val bonusGraceKey = intPreferencesKey("bonus_grace_credits")
    private val graceClaimedKey = booleanPreferencesKey("grace_claimed")
    private val ecoLiteModeKey = booleanPreferencesKey("eco_lite_mode")
    private val forceLocalKey = booleanPreferencesKey("force_local_offline_mode")
    private val cascadeCountKey = intPreferencesKey("fallback_cascade_count")
    private val queuedScansKey = intPreferencesKey("queued_scans_count")

    val quotaFlow: Flow<UsageQuotaSnapshot> = appContext.quotaDataStore.data.map { prefs ->
        val today = currentDateIso()
        val storedDate = prefs[dateIsoKey] ?: today
        val isNewDay = storedDate != today

        UsageQuotaSnapshot(
            dateIso = today,
            visionUsed = if (isNewDay) 0 else (prefs[visionUsedKey] ?: 0),
            recipeUsed = if (isNewDay) 0 else (prefs[recipeUsedKey] ?: 0),
            chatUsed = if (isNewDay) 0 else (prefs[chatUsedKey] ?: 0),
            audioUsed = if (isNewDay) 0 else (prefs[audioUsedKey] ?: 0),
            baseDailyAllowance = 10,
            bonusGraceCredits = if (isNewDay) 0 else (prefs[bonusGraceKey] ?: 0),
            graceClaimed = if (isNewDay) false else (prefs[graceClaimedKey] ?: false),
            ecoLiteMode = prefs[ecoLiteModeKey] ?: false,
            forceLocalOfflineMode = prefs[forceLocalKey] ?: false,
            fallbackCascadeCount = if (isNewDay) 0 else (prefs[cascadeCountKey] ?: 0),
            queuedScansCount = prefs[queuedScansKey] ?: 0
        )
    }

    suspend fun ensureDailyRollover() {
        val today = currentDateIso()
        appContext.quotaDataStore.edit { prefs ->
            val existingDate = prefs[dateIsoKey]
            if (existingDate == null) {
                prefs[dateIsoKey] = today
                prefs[visionUsedKey] = 0
                prefs[recipeUsedKey] = 0
                prefs[chatUsedKey] = 0
                prefs[audioUsedKey] = 0
            } else if (existingDate != today) {
                // Automatic Rollover Reset: reset usage counters & graceClaimed while preserving ecoLiteMode
                prefs[dateIsoKey] = today
                prefs[visionUsedKey] = 0
                prefs[recipeUsedKey] = 0
                prefs[chatUsedKey] = 0
                prefs[audioUsedKey] = 0
                prefs[bonusGraceKey] = 0
                prefs[graceClaimedKey] = false
                prefs[cascadeCountKey] = 0
            }
        }
    }

    suspend fun getSnapshot(): UsageQuotaSnapshot {
        ensureDailyRollover()
        return quotaFlow.first()
    }

    /**
     * Atomic Deduct-with-Refund: Only charge quota on successful AI output,
     * and allow refunding via refundInteraction(feature) if a call fails.
     */
    suspend fun recordSuccessfulInteraction(feature: AiFeatureType, usedFallbackCascade: Boolean = false) {
        ensureDailyRollover()
        appContext.quotaDataStore.edit { prefs ->
            when (feature) {
                AiFeatureType.VISION_SCAN -> {
                    prefs[visionUsedKey] = (prefs[visionUsedKey] ?: 0) + 1
                }
                AiFeatureType.RECIPE_RANKING,
                AiFeatureType.SHELF_LIFE_PRESERVATION -> {
                    prefs[recipeUsedKey] = (prefs[recipeUsedKey] ?: 0) + 1
                }
                AiFeatureType.SOUS_CHEF_CHAT -> {
                    prefs[chatUsedKey] = (prefs[chatUsedKey] ?: 0) + 1
                }
                AiFeatureType.VOICE_MEMO_PARSE -> {
                    prefs[audioUsedKey] = (prefs[audioUsedKey] ?: 0) + 1
                }
            }
            if (usedFallbackCascade) {
                prefs[cascadeCountKey] = (prefs[cascadeCountKey] ?: 0) + 1
            }
        }
    }

    suspend fun refundInteraction(feature: AiFeatureType) {
        appContext.quotaDataStore.edit { prefs ->
            when (feature) {
                AiFeatureType.VISION_SCAN -> {
                    prefs[visionUsedKey] = ((prefs[visionUsedKey] ?: 1) - 1).coerceAtLeast(0)
                }
                AiFeatureType.RECIPE_RANKING,
                AiFeatureType.SHELF_LIFE_PRESERVATION -> {
                    prefs[recipeUsedKey] = ((prefs[recipeUsedKey] ?: 1) - 1).coerceAtLeast(0)
                }
                AiFeatureType.SOUS_CHEF_CHAT -> {
                    prefs[chatUsedKey] = ((prefs[chatUsedKey] ?: 1) - 1).coerceAtLeast(0)
                }
                AiFeatureType.VOICE_MEMO_PARSE -> {
                    prefs[audioUsedKey] = ((prefs[audioUsedKey] ?: 1) - 1).coerceAtLeast(0)
                }
            }
        }
    }

    suspend fun recordAutoCascade(incrementCount: Boolean = false) {
        appContext.quotaDataStore.edit { prefs ->
            if (incrementCount) {
                prefs[cascadeCountKey] = (prefs[cascadeCountKey] ?: 0) + 1
            }
            prefs[ecoLiteModeKey] = true
        }
    }

    suspend fun claimBonusGraceCredits(): Boolean {
        val snap = getSnapshot()
        if (snap.graceClaimed) return false
        appContext.quotaDataStore.edit { prefs ->
            prefs[graceClaimedKey] = true
            prefs[bonusGraceKey] = (prefs[bonusGraceKey] ?: 0) + 3
            prefs[forceLocalKey] = false
        }
        return true
    }

    suspend fun setEcoLiteMode(enabled: Boolean) {
        appContext.quotaDataStore.edit { prefs ->
            prefs[ecoLiteModeKey] = enabled
            if (enabled) {
                prefs[forceLocalKey] = false
            }
        }
    }

    suspend fun setForceLocalOfflineMode(enabled: Boolean) {
        appContext.quotaDataStore.edit { prefs ->
            prefs[forceLocalKey] = enabled
        }
    }

    suspend fun incrementQueuedScans() {
        appContext.quotaDataStore.edit { prefs ->
            prefs[queuedScansKey] = (prefs[queuedScansKey] ?: 0) + 1
        }
    }

    /**
     * Helper to let users test Stage 1 (80% Soft Nudge), Stage 2 (100% Exhausted Recovery),
     * or Healthy Tier 1 state directly from the Value Receipt & Quota dialog.
     */
    suspend fun simulateQuotaState(preset: String) {
        val today = currentDateIso()
        appContext.quotaDataStore.edit { prefs ->
            prefs[dateIsoKey] = today
            when (preset) {
                "HEALTHY" -> {
                    prefs[visionUsedKey] = 1
                    prefs[recipeUsedKey] = 1
                    prefs[chatUsedKey] = 1
                    prefs[bonusGraceKey] = 0
                    prefs[graceClaimedKey] = false
                    prefs[ecoLiteModeKey] = false
                    prefs[forceLocalKey] = false
                }
                "SOFT_NUDGE_80" -> {
                    prefs[visionUsedKey] = 3
                    prefs[recipeUsedKey] = 3
                    prefs[chatUsedKey] = 2 // 8 / 10 = 80% used
                    prefs[bonusGraceKey] = 0
                    prefs[graceClaimedKey] = false
                    prefs[ecoLiteModeKey] = false
                    prefs[forceLocalKey] = false
                }
                "EXHAUSTED_100" -> {
                    prefs[visionUsedKey] = 4
                    prefs[recipeUsedKey] = 3
                    prefs[chatUsedKey] = 3 // 10 / 10 = 100% used
                    prefs[bonusGraceKey] = 0
                    prefs[graceClaimedKey] = false
                    prefs[forceLocalKey] = false
                }
            }
        }
    }

    companion object {
        fun currentDateIso(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date())
        }

        fun computeResetCountdown(): String {
            val now = Calendar.getInstance()
            val midnight = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMs = (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
            val hours = diffMs / (1000 * 60 * 60)
            val mins = (diffMs / (1000 * 60)) % 60
            return "${hours}h ${mins}m"
        }
    }
}
