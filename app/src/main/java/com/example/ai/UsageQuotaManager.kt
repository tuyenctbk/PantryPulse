package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Context.quotaDataStore by preferencesDataStore(name = "usage_quota_ledger")

private const val LOCAL_MIRROR_PREFS_NAME = "usage_quota_local_mirror"

/**
 * Immutable snapshot of the user's daily Gemini API quota ledger and Eco Lite state.
 *
 * Budget Accounting:
 * - 1 standard daily credit = [UNITS_PER_CREDIT] (3) budget units.
 * - Standard (Tier 1) interactions consume 3 units (1.0 daily credit).
 * - Eco Lite (Tier 2) interactions consume 1 unit (1/3 daily credit), stretching
 *   remaining allowance 3x further.
 */
data class UsageQuotaSnapshot(
    val dateIso: String = UsageQuotaManager.currentDateIso(),
    val visionUsed: Int = 0,
    val recipeUsed: Int = 0,
    val chatUsed: Int = 0,
    val audioUsed: Int = 0,
    val baseDailyAllowance: Int = DEFAULT_BASE_DAILY_ALLOWANCE,
    val bonusGraceCredits: Int = 0,
    val purchasedCreditBalance: Int = 0,
    val graceClaimed: Boolean = false,
    val ecoLiteMode: Boolean = false,
    val forceLocalOfflineMode: Boolean = false,
    val fallbackCascadeCount: Int = 0,
    val queuedScansCount: Int = 0,
    val creditUnitsUsed: Int = UNSPECIFIED_CREDIT_UNITS,
    val lastMirrorSyncedEpochMs: Long = 0L
) {
    val totalAllowance: Int
        get() = baseDailyAllowance + bonusGraceCredits + purchasedCreditBalance

    val totalAllowanceUnits: Int
        get() = totalAllowance * UNITS_PER_CREDIT

    val totalUsed: Int
        get() = visionUsed + recipeUsed + chatUsed + audioUsed

    val effectiveCreditUnitsUsed: Int
        get() = if (creditUnitsUsed >= 0) {
            creditUnitsUsed
        } else {
            totalUsed * UNITS_PER_CREDIT
        }

    val remainingCreditUnits: Int
        get() = (totalAllowanceUnits - effectiveCreditUnitsUsed).coerceAtLeast(0)

    /**
     * Remaining standard daily credits. Uses ceiling division on budget units so
     * partial Eco Lite remainder (1–2 units) still shows 1 credit available rather than 0.
     */
    val remainingCredits: Int
        get() = if (remainingCreditUnits <= 0) {
            0
        } else {
            (remainingCreditUnits + UNITS_PER_CREDIT - 1) / UNITS_PER_CREDIT
        }

    /**
     * Remaining individual Gemini calls when running in Eco Lite Mode (3x stretch).
     */
    val remainingEcoLiteCalls: Int
        get() = remainingCreditUnits

    val remainingRatio: Float
        get() = if (totalAllowanceUnits > 0) {
            (remainingCreditUnits.toFloat() / totalAllowanceUnits.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val usageRatio: Float
        get() = (1f - remainingRatio).coerceIn(0f, 1f)

    val isSoftNudgeThreshold: Boolean
        get() = usageRatio >= 0.80f && remainingCreditUnits > 0

    val isExhausted: Boolean
        get() = remainingCreditUnits <= 0

    val activeCapabilityTier: AiCapabilityTier
        get() = when {
            forceLocalOfflineMode || isExhausted -> AiCapabilityTier.TIER_3_LOCAL_FALLBACK
            ecoLiteMode || isSoftNudgeThreshold -> AiCapabilityTier.TIER_2_ECO_LITE
            else -> AiCapabilityTier.TIER_1_PRIMARY
        }

    val resetCountdownText: String
        get() = UsageQuotaManager.computeResetCountdown()

    /**
     * Returns a rolled-over snapshot if [targetDateIso] differs from [dateIso],
     * resetting daily usage counters and one-time grace credits while preserving
     * user preferences ([ecoLiteMode], [forceLocalOfflineMode], [queuedScansCount]).
     */
    fun rolloverForDate(targetDateIso: String): UsageQuotaSnapshot {
        if (dateIso == targetDateIso) return this
        return copy(
            dateIso = targetDateIso,
            visionUsed = 0,
            recipeUsed = 0,
            chatUsed = 0,
            audioUsed = 0,
            bonusGraceCredits = 0,
            graceClaimed = false,
            fallbackCascadeCount = 0,
            creditUnitsUsed = 0
        )
    }

    companion object {
        const val DEFAULT_BASE_DAILY_ALLOWANCE = 10
        const val UNITS_PER_CREDIT = 3
        const val STANDARD_CALL_UNIT_COST = 3
        const val ECO_LITE_CALL_UNIT_COST = 1
        const val UNSPECIFIED_CREDIT_UNITS = -1
    }
}

/**
 * Tracks daily Gemini API usage with:
 * 1. Preferences DataStore (`usage_quota_ledger`) as the durable local ledger.
 * 2. Synchronous cold-start local cache mirror (`SharedPreferences` + in-memory `StateFlow`)
 *    so the UI renders accurate remaining daily credits and Eco Lite toggle immediately on launch.
 * 3. Automatic midnight rollover, atomic deduct-with-refund, Eco Lite 3x stretch accounting,
 *    and a `mirrorRemoteSnapshot` entry point for cloud/Firestore sync.
 */
class UsageQuotaManager(private val appContext: Context) {

    private val dateIsoKey = stringPreferencesKey("date_iso")
    private val visionUsedKey = intPreferencesKey("vision_used")
    private val recipeUsedKey = intPreferencesKey("recipe_used")
    private val chatUsedKey = intPreferencesKey("chat_used")
    private val audioUsedKey = intPreferencesKey("audio_used")
    private val bonusGraceKey = intPreferencesKey("bonus_grace_credits")
    private val purchasedCreditsKey = intPreferencesKey("purchased_credit_balance")
    private val graceClaimedKey = booleanPreferencesKey("grace_claimed")
    private val ecoLiteModeKey = booleanPreferencesKey("eco_lite_mode")
    private val forceLocalKey = booleanPreferencesKey("force_local_offline_mode")
    private val cascadeCountKey = intPreferencesKey("fallback_cascade_count")
    private val queuedScansKey = intPreferencesKey("queued_scans_count")
    private val creditUnitsUsedKey = intPreferencesKey("credit_units_used")
    private val lastMirrorSyncedKey = longPreferencesKey("last_mirror_synced_ms")

    private val mirrorPrefs: SharedPreferences =
        appContext.getSharedPreferences(LOCAL_MIRROR_PREFS_NAME, Context.MODE_PRIVATE)

    private val _localMirrorState = MutableStateFlow(readColdStartMirror())

    /** Synchronous snapshot from the local mirror for zero-flicker cold-start UI hydration. */
    val localMirrorSnapshot: UsageQuotaSnapshot
        get() = _localMirrorState.value

    /** Reactive StateFlow backed by the local mirror cache. */
    val localMirrorState: StateFlow<UsageQuotaSnapshot> = _localMirrorState.asStateFlow()

    /**
     * Durable Preferences DataStore stream that automatically applies daily rollover
     * and keeps the cold-start local mirror synchronized on every emission.
     */
    val quotaFlow: Flow<UsageQuotaSnapshot> = appContext.quotaDataStore.data
        .map { prefs -> snapshotFromPreferences(prefs, currentDateIso()) }
        .onEach { snapshot -> updateLocalMirror(snapshot) }

    suspend fun ensureDailyRollover() {
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            val existingDate = prefs[dateIsoKey]
            if (existingDate == null) {
                prefs[dateIsoKey] = today
                prefs[visionUsedKey] = 0
                prefs[recipeUsedKey] = 0
                prefs[chatUsedKey] = 0
                prefs[audioUsedKey] = 0
                prefs[creditUnitsUsedKey] = 0
                prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
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
                prefs[creditUnitsUsedKey] = 0
                prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
            }
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    suspend fun getSnapshot(): UsageQuotaSnapshot {
        ensureDailyRollover()
        return quotaFlow.first()
    }

    /**
     * Mirrors an authoritative remote/cloud quota snapshot (e.g., from Firestore)
     * into both Preferences DataStore and the synchronous cold-start cache.
     */
    suspend fun mirrorRemoteSnapshot(remote: UsageQuotaSnapshot) {
        val today = currentDateIso()
        val normalized = remote.rolloverForDate(today)
        val syncNow = System.currentTimeMillis()
        val updated = appContext.quotaDataStore.edit { prefs ->
            prefs[dateIsoKey] = normalized.dateIso
            prefs[visionUsedKey] = normalized.visionUsed
            prefs[recipeUsedKey] = normalized.recipeUsed
            prefs[chatUsedKey] = normalized.chatUsed
            prefs[audioUsedKey] = normalized.audioUsed
            prefs[bonusGraceKey] = normalized.bonusGraceCredits
            prefs[purchasedCreditsKey] = normalized.purchasedCreditBalance
            prefs[graceClaimedKey] = normalized.graceClaimed
            prefs[ecoLiteModeKey] = normalized.ecoLiteMode
            prefs[forceLocalKey] = normalized.forceLocalOfflineMode
            prefs[cascadeCountKey] = normalized.fallbackCascadeCount
            prefs[queuedScansKey] = normalized.queuedScansCount
            prefs[creditUnitsUsedKey] = normalized.effectiveCreditUnitsUsed
            prefs[lastMirrorSyncedKey] = syncNow
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    /**
     * Atomic Deduct-with-Refund: Only charge quota on successful AI output,
     * and allow refunding via [refundInteraction] if a call fails.
     *
     * When running in Eco Lite Mode (or after an auto-cascade to Tier 2), charges
     * 1 budget unit instead of 3 (stretching daily credits 3x further).
     */
    suspend fun recordSuccessfulInteraction(
        feature: AiFeatureType,
        usedFallbackCascade: Boolean = false,
        usedEcoLite: Boolean? = null
    ) {
        ensureDailyRollover()
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            val currentUnits = resolveCurrentCreditUnits(prefs)
            val ecoActive = usedEcoLite ?: (usedFallbackCascade || (prefs[ecoLiteModeKey] ?: false))
            val unitCost = if (ecoActive) {
                UsageQuotaSnapshot.ECO_LITE_CALL_UNIT_COST
            } else {
                UsageQuotaSnapshot.STANDARD_CALL_UNIT_COST
            }

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
            prefs[creditUnitsUsedKey] = currentUnits + unitCost
            if (usedFallbackCascade) {
                prefs[cascadeCountKey] = (prefs[cascadeCountKey] ?: 0) + 1
            }
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    suspend fun refundInteraction(
        feature: AiFeatureType,
        usedEcoLite: Boolean? = null
    ) {
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            val currentUnits = resolveCurrentCreditUnits(prefs)
            val ecoActive = usedEcoLite ?: (prefs[ecoLiteModeKey] ?: false)
            val unitRefund = if (ecoActive) {
                UsageQuotaSnapshot.ECO_LITE_CALL_UNIT_COST
            } else {
                UsageQuotaSnapshot.STANDARD_CALL_UNIT_COST
            }

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
            prefs[creditUnitsUsedKey] = (currentUnits - unitRefund).coerceAtLeast(0)
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    suspend fun recordAutoCascade(incrementCount: Boolean = false) {
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            if (incrementCount) {
                prefs[cascadeCountKey] = (prefs[cascadeCountKey] ?: 0) + 1
            }
            prefs[ecoLiteModeKey] = true
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    suspend fun claimBonusGraceCredits(): Boolean {
        val snap = getSnapshot()
        if (snap.graceClaimed) return false
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            prefs[graceClaimedKey] = true
            prefs[bonusGraceKey] = (prefs[bonusGraceKey] ?: 0) + 3
            prefs[forceLocalKey] = false
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
        return true
    }

    /**
     * Grants non-expiring consumable AI credits (e.g., +30 or +100 credit packs)
     * that persist across daily rollovers.
     */
    suspend fun grantPurchasedCreditPack(credits: Int) {
        if (credits <= 0) return
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            prefs[purchasedCreditsKey] = (prefs[purchasedCreditsKey] ?: 0) + credits
            prefs[forceLocalKey] = false
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    suspend fun setEcoLiteMode(enabled: Boolean) {
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            prefs[ecoLiteModeKey] = enabled
            if (enabled) {
                prefs[forceLocalKey] = false
            }
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    suspend fun toggleEcoLiteMode(): Boolean {
        val target = !localMirrorSnapshot.ecoLiteMode
        setEcoLiteMode(target)
        return target
    }

    suspend fun setForceLocalOfflineMode(enabled: Boolean) {
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            prefs[forceLocalKey] = enabled
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    suspend fun incrementQueuedScans() {
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            prefs[queuedScansKey] = (prefs[queuedScansKey] ?: 0) + 1
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    /**
     * Helper to let users test Stage 1 (80% Soft Nudge), Stage 2 (100% Exhausted Recovery),
     * or Healthy Tier 1 state directly from the Value Receipt & Quota dialog.
     */
    suspend fun simulateQuotaState(preset: String) {
        val today = currentDateIso()
        val updated = appContext.quotaDataStore.edit { prefs ->
            prefs[dateIsoKey] = today
            when (preset) {
                "HEALTHY" -> {
                    prefs[visionUsedKey] = 1
                    prefs[recipeUsedKey] = 1
                    prefs[chatUsedKey] = 1
                    prefs[audioUsedKey] = 0
                    prefs[creditUnitsUsedKey] = 3 * UsageQuotaSnapshot.UNITS_PER_CREDIT
                    prefs[bonusGraceKey] = 0
                    prefs[graceClaimedKey] = false
                    prefs[ecoLiteModeKey] = false
                    prefs[forceLocalKey] = false
                }
                "SOFT_NUDGE_80" -> {
                    prefs[visionUsedKey] = 3
                    prefs[recipeUsedKey] = 3
                    prefs[chatUsedKey] = 2 // 8 / 10 = 80% used
                    prefs[audioUsedKey] = 0
                    prefs[creditUnitsUsedKey] = 8 * UsageQuotaSnapshot.UNITS_PER_CREDIT
                    prefs[bonusGraceKey] = 0
                    prefs[graceClaimedKey] = false
                    prefs[ecoLiteModeKey] = false
                    prefs[forceLocalKey] = false
                }
                "EXHAUSTED_100" -> {
                    prefs[visionUsedKey] = 4
                    prefs[recipeUsedKey] = 3
                    prefs[chatUsedKey] = 3 // 10 / 10 = 100% used
                    prefs[audioUsedKey] = 0
                    prefs[creditUnitsUsedKey] = 10 * UsageQuotaSnapshot.UNITS_PER_CREDIT
                    prefs[bonusGraceKey] = 0
                    prefs[graceClaimedKey] = false
                    prefs[ecoLiteModeKey] = false
                    prefs[forceLocalKey] = false
                }
            }
            prefs[lastMirrorSyncedKey] = System.currentTimeMillis()
        }
        updateLocalMirror(snapshotFromPreferences(updated, today))
    }

    private fun resolveCurrentCreditUnits(prefs: Preferences): Int {
        val storedUnits = prefs[creditUnitsUsedKey]
        if (storedUnits != null && storedUnits >= 0) return storedUnits
        val legacyTotal = (prefs[visionUsedKey] ?: 0) +
            (prefs[recipeUsedKey] ?: 0) +
            (prefs[chatUsedKey] ?: 0) +
            (prefs[audioUsedKey] ?: 0)
        return legacyTotal * UsageQuotaSnapshot.UNITS_PER_CREDIT
    }

    private fun snapshotFromPreferences(prefs: Preferences, today: String): UsageQuotaSnapshot {
        val storedDate = prefs[dateIsoKey] ?: today
        val isNewDay = storedDate != today
        val vision = if (isNewDay) 0 else (prefs[visionUsedKey] ?: 0)
        val recipe = if (isNewDay) 0 else (prefs[recipeUsedKey] ?: 0)
        val chat = if (isNewDay) 0 else (prefs[chatUsedKey] ?: 0)
        val audio = if (isNewDay) 0 else (prefs[audioUsedKey] ?: 0)
        val storedUnits = if (isNewDay) {
            0
        } else {
            prefs[creditUnitsUsedKey]
                ?: ((vision + recipe + chat + audio) * UsageQuotaSnapshot.UNITS_PER_CREDIT)
        }

        return UsageQuotaSnapshot(
            dateIso = today,
            visionUsed = vision,
            recipeUsed = recipe,
            chatUsed = chat,
            audioUsed = audio,
            baseDailyAllowance = UsageQuotaSnapshot.DEFAULT_BASE_DAILY_ALLOWANCE,
            bonusGraceCredits = if (isNewDay) 0 else (prefs[bonusGraceKey] ?: 0),
            purchasedCreditBalance = prefs[purchasedCreditsKey] ?: 0,
            graceClaimed = if (isNewDay) false else (prefs[graceClaimedKey] ?: false),
            ecoLiteMode = prefs[ecoLiteModeKey] ?: false,
            forceLocalOfflineMode = prefs[forceLocalKey] ?: false,
            fallbackCascadeCount = if (isNewDay) 0 else (prefs[cascadeCountKey] ?: 0),
            queuedScansCount = prefs[queuedScansKey] ?: 0,
            creditUnitsUsed = storedUnits,
            lastMirrorSyncedEpochMs = prefs[lastMirrorSyncedKey] ?: 0L
        )
    }

    private fun readColdStartMirror(): UsageQuotaSnapshot {
        val today = currentDateIso()
        val storedDate = mirrorPrefs.getString(MIRROR_DATE_ISO, null) ?: today
        val base = UsageQuotaSnapshot(
            dateIso = storedDate,
            visionUsed = mirrorPrefs.getInt(MIRROR_VISION_USED, 0),
            recipeUsed = mirrorPrefs.getInt(MIRROR_RECIPE_USED, 0),
            chatUsed = mirrorPrefs.getInt(MIRROR_CHAT_USED, 0),
            audioUsed = mirrorPrefs.getInt(MIRROR_AUDIO_USED, 0),
            baseDailyAllowance = UsageQuotaSnapshot.DEFAULT_BASE_DAILY_ALLOWANCE,
            bonusGraceCredits = mirrorPrefs.getInt(MIRROR_BONUS_GRACE, 0),
            purchasedCreditBalance = mirrorPrefs.getInt(MIRROR_PURCHASED_CREDITS, 0),
            graceClaimed = mirrorPrefs.getBoolean(MIRROR_GRACE_CLAIMED, false),
            ecoLiteMode = mirrorPrefs.getBoolean(MIRROR_ECO_LITE, false),
            forceLocalOfflineMode = mirrorPrefs.getBoolean(MIRROR_FORCE_LOCAL, false),
            fallbackCascadeCount = mirrorPrefs.getInt(MIRROR_CASCADE_COUNT, 0),
            queuedScansCount = mirrorPrefs.getInt(MIRROR_QUEUED_SCANS, 0),
            creditUnitsUsed = mirrorPrefs.getInt(MIRROR_CREDIT_UNITS, UsageQuotaSnapshot.UNSPECIFIED_CREDIT_UNITS),
            lastMirrorSyncedEpochMs = mirrorPrefs.getLong(MIRROR_LAST_SYNCED_MS, 0L)
        )
        return base.rolloverForDate(today)
    }

    private fun updateLocalMirror(snapshot: UsageQuotaSnapshot) {
        _localMirrorState.value = snapshot
        mirrorPrefs.edit()
            .putString(MIRROR_DATE_ISO, snapshot.dateIso)
            .putInt(MIRROR_VISION_USED, snapshot.visionUsed)
            .putInt(MIRROR_RECIPE_USED, snapshot.recipeUsed)
            .putInt(MIRROR_CHAT_USED, snapshot.chatUsed)
            .putInt(MIRROR_AUDIO_USED, snapshot.audioUsed)
            .putInt(MIRROR_BONUS_GRACE, snapshot.bonusGraceCredits)
            .putInt(MIRROR_PURCHASED_CREDITS, snapshot.purchasedCreditBalance)
            .putBoolean(MIRROR_GRACE_CLAIMED, snapshot.graceClaimed)
            .putBoolean(MIRROR_ECO_LITE, snapshot.ecoLiteMode)
            .putBoolean(MIRROR_FORCE_LOCAL, snapshot.forceLocalOfflineMode)
            .putInt(MIRROR_CASCADE_COUNT, snapshot.fallbackCascadeCount)
            .putInt(MIRROR_QUEUED_SCANS, snapshot.queuedScansCount)
            .putInt(MIRROR_CREDIT_UNITS, snapshot.effectiveCreditUnitsUsed)
            .putLong(MIRROR_LAST_SYNCED_MS, snapshot.lastMirrorSyncedEpochMs)
            .apply()
    }

    companion object {
        private const val MIRROR_DATE_ISO = "mirror_date_iso"
        private const val MIRROR_VISION_USED = "mirror_vision_used"
        private const val MIRROR_RECIPE_USED = "mirror_recipe_used"
        private const val MIRROR_CHAT_USED = "mirror_chat_used"
        private const val MIRROR_AUDIO_USED = "mirror_audio_used"
        private const val MIRROR_BONUS_GRACE = "mirror_bonus_grace"
        private const val MIRROR_PURCHASED_CREDITS = "mirror_purchased_credits"
        private const val MIRROR_GRACE_CLAIMED = "mirror_grace_claimed"
        private const val MIRROR_ECO_LITE = "mirror_eco_lite"
        private const val MIRROR_FORCE_LOCAL = "mirror_force_local"
        private const val MIRROR_CASCADE_COUNT = "mirror_cascade_count"
        private const val MIRROR_QUEUED_SCANS = "mirror_queued_scans"
        private const val MIRROR_CREDIT_UNITS = "mirror_credit_units"
        private const val MIRROR_LAST_SYNCED_MS = "mirror_last_synced_ms"

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
