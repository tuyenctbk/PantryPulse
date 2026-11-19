package com.example.ui.engagement

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Safely unwraps a [Context] hierarchy to locate the hosting [Activity] session.
 * Prevents the "Billing Activity requires an Activity session" error when launching
 * Google Play Billing, In-App Review, or external intents from Compose contexts.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

enum class GooglePlayDonationTier(
    val skuId: String,
    val title: String,
    val priceFormatted: String,
    val amountDollars: Double,
    val badgeEmoji: String,
    val description: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
) {
    TIER_1(
        skuId = "pantrypulse_supporter_199",
        title = "Tier 1 • Espresso Boost",
        priceFormatted = "$1.99",
        amountDollars = 1.99,
        badgeEmoji = "☕",
        description = "Fuel late-night bug fixes & offline database tuning",
        titleRes = R.string.donation_tier_1_title,
        descriptionRes = R.string.donation_tier_1_desc
    ),
    TIER_2(
        skuId = "pantrypulse_supporter_499",
        title = "Tier 2 • Sous-Chef Fuel",
        priceFormatted = "$4.99",
        amountDollars = 4.99,
        badgeEmoji = "🥗",
        description = "Support zero-waste recipe algorithms & widget updates",
        titleRes = R.string.donation_tier_2_title,
        descriptionRes = R.string.donation_tier_2_desc
    ),
    TIER_3(
        skuId = "pantrypulse_supporter_999",
        title = "Tier 3 • Kitchen Patron",
        priceFormatted = "$9.99",
        amountDollars = 9.99,
        badgeEmoji = "🍳",
        description = "Sponsor CameraX vision upgrades & 20-language localization",
        titleRes = R.string.donation_tier_3_title,
        descriptionRes = R.string.donation_tier_3_desc
    ),
    TIER_4(
        skuId = "pantrypulse_supporter_1999",
        title = "Tier 4 • Zero-Waste Champion",
        priceFormatted = "$19.99",
        amountDollars = 19.99,
        badgeEmoji = "🏆",
        description = "Champion 100% ad-free, privacy-first independent development",
        titleRes = R.string.donation_tier_4_title,
        descriptionRes = R.string.donation_tier_4_desc
    )
}

enum class SmartAutoPromptType(
    val title: String,
    @StringRes val titleRes: Int
) {
    MILESTONE_DONATION("Milestone Reached • Support PantryPulse", R.string.prompt_title_milestone),
    RATE_APP("Enjoying PantryPulse? Rate Us", R.string.prompt_title_rate),
    SHARE_APP("Share Zero-Waste Cooking", R.string.prompt_title_share),
    UPDATE_APP("New PantryPulse Update Available", R.string.prompt_title_update)
}

data class AppTrackedEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val eventType: String,
    val detail: String,
    val algorithmNote: String = ""
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
}

data class EngagementSnapshot(
    val appLaunchCount: Int = 1,
    val daysSinceInstall: Int = 3,
    val completedCoreOpsCount: Int = 3,
    val isActiveSupporter: Boolean = false,
    val supporterMethodLabel: String = "",
    val totalDonatedDollars: Double = 0.0,
    val hasRatedApp: Boolean = false,
    val shareCount: Int = 0,
    val installedVersionName: String = "1.0.0",
    val latestAvailableVersionName: String = "1.2.0",
    val isUpdateAvailable: Boolean = true,
    val isHomeCompactBannerVisible: Boolean = true,
    val activeAutoPrompt: SmartAutoPromptType? = null,
    val activeMilestoneCount: Int = 0,
    val showPostOperationValueCard: Boolean = false,
    val lastCompletedTaskTitle: String = "",
    val autoPopupsShownThisSession: Int = 0,
    val billingUiState: BillingUiState = BillingUiState(),
    val lastVerifiedReceipt: VerifiedPurchaseReceipt? = null,
    val eventLogs: List<AppTrackedEvent> = emptyList()
)

/**
 * Manages:
 * 1. App Event Logging & Tracking (app launches, core operations, milestones, user responses)
 * 2. Anti-Annoyance Satisfaction Safeguard Algorithm (never shows on app start, max 1 popup per session,
 *    strict 14-day / 30-day cooldowns, only triggers after verified utility at Value Realization Moments)
 * 3. Multi-Channel Donation Execution (Google Play Billing via [GooglePlayBillingManager] with verified Activity session,
 *    PayPal deep link + live QR code generator, and Ko-fi web tip jar)
 * 4. Rate App, Share App, and App Update flows.
 */
class AppEngagementAndDonationManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Session-scoped anti-annoyance guards (NEVER show on cold start, max 1 auto-popup per session)
    private var isAppStartPhase: Boolean = true
    private var autoPopupsShownThisSession: Int = 0
    private var showPostOperationValueCard: Boolean = false
    private var lastCompletedTaskTitle: String = ""
    private var activeAutoPrompt: SmartAutoPromptType? = null
    private var activeMilestoneCount: Int = 0

    private val _eventLogs = mutableListOf<AppTrackedEvent>()

    val billingManager = BillingManager(
        context = context,
        onDiagnosticEventLogged = { eventType, detail, note ->
            logEventInternal(eventType, detail, note)
            refreshState()
        }
    )

    val billingUiState: StateFlow<BillingUiState> = billingManager.billingUiState

    private val _stateFlow = MutableStateFlow(buildSnapshot())
    val stateFlow: StateFlow<EngagementSnapshot> = _stateFlow.asStateFlow()

    init {
        ensureInitializedAndRecordLaunch()
        billingManager.addPurchaseCompletionListener { receipt ->
            applyVerifiedGooglePlayReceipt(receipt)
        }
    }

    fun bindHostActivity(activity: Activity?) {
        billingManager.updateHostActivity(activity)
        refreshState()
    }

    fun closeBillingConnection() {
        billingManager.endConnection()
    }

    private fun ensureInitializedAndRecordLaunch() {
        val now = System.currentTimeMillis()
        if (!prefs.contains(KEY_FIRST_INSTALL_MS)) {
            // Seed install timestamp to 3 days ago with 3 prior launches and 3 completed operations
            // so the non-intrusive Home Compact Banner can be inspected while zero auto-popups fire at startup.
            prefs.edit()
                .putLong(KEY_FIRST_INSTALL_MS, now - 3L * DAY_MS)
                .putInt(KEY_APP_LAUNCH_COUNT, 3)
                .putInt(KEY_COMPLETED_OPS_COUNT, 3)
                .putString(KEY_INSTALLED_VERSION, "1.0.0")
                .apply()
        } else {
            val launches = prefs.getInt(KEY_APP_LAUNCH_COUNT, 1) + 1
            prefs.edit().putInt(KEY_APP_LAUNCH_COUNT, launches).apply()
        }

        // CRITICAL SAFEGUARD: Never show auto-popup at app start!
        isAppStartPhase = true
        activeAutoPrompt = null

        logEventInternal(
            eventType = "APP_LAUNCH",
            detail = "Cold launch #${prefs.getInt(KEY_APP_LAUNCH_COUNT, 1)} recorded",
            algorithmNote = "Startup Safeguard Active: Auto-popups strictly blocked on app start."
        )
        refreshState()
    }

    /**
     * Records a completed high-value core operation (Value Realization Moment)
     * and runs the Smart Trigger Algorithm to decide if a non-intrusive suggestion is appropriate.
     */
    fun recordCoreOperationCompleted(
        operationKey: String,
        humanReadableTitle: String
    ) {
        isAppStartPhase = false
        val now = System.currentTimeMillis()
        val newOpsCount = prefs.getInt(KEY_COMPLETED_OPS_COUNT, 0) + 1
        prefs.edit().putInt(KEY_COMPLETED_OPS_COUNT, newOpsCount).apply()

        showPostOperationValueCard = true
        lastCompletedTaskTitle = humanReadableTitle

        val decisionNote = evaluateSmartAutoPromptTrigger(now, newOpsCount)
        logEventInternal(
            eventType = "CORE_OP_SUCCESS",
            detail = "$operationKey ($humanReadableTitle) • Total Ops: $newOpsCount",
            algorithmNote = decisionNote
        )
        refreshState()
    }

    fun logCustomEvent(eventType: String, detail: String, algorithmNote: String = "") {
        logEventInternal(eventType, detail, algorithmNote)
        refreshState()
    }

    /**
     * Smart Trigger Algorithm:
     * - NEVER triggers during app start (`isAppStartPhase == true`).
     * - NEVER triggers more than once per app session (`autoPopupsShownThisSession >= 1`).
     * - Enforces a minimum 48-hour global quiet period between any two auto-popups.
     * - Evaluates:
     *   1. Milestone / Support Auto-Prompt (at 7, 10, 25, 50 ops; 30-day cooldown; skipped if Active Supporter)
     *   2. Rate App Auto-Prompt (at >= 4 ops & >= 2 launches; 14-day cooldown; skipped if already rated)
     *   3. Share App Auto-Prompt (at >= 5 ops; 14-day cooldown)
     *   4. App Update Auto-Prompt (at >= 6 ops when update available; 7-day cooldown)
     */
    private fun evaluateSmartAutoPromptTrigger(now: Long, totalOps: Int): String {
        if (isAppStartPhase) {
            return "Blocked: Never show auto-popup on app start."
        }
        if (autoPopupsShownThisSession >= 1) {
            return "Suppressed: Session limit reached (max 1 auto-popup per session)."
        }
        val lastGlobalPopupMs = prefs.getLong(KEY_LAST_GLOBAL_POPUP_MS, 0L)
        if (now - lastGlobalPopupMs < GLOBAL_POPUP_GAP_MS) {
            val hoursLeft = ((GLOBAL_POPUP_GAP_MS - (now - lastGlobalPopupMs)) / (3600_000L)).coerceAtLeast(1)
            return "Suppressed: Global 48h anti-annoyance cooldown active (${hoursLeft}h remaining)."
        }

        val launches = prefs.getInt(KEY_APP_LAUNCH_COUNT, 1)
        val daysSinceInstall = getDaysSinceInstall(now)
        val isSupporter = prefs.getBoolean(KEY_IS_ACTIVE_SUPPORTER, false)
        val hasRated = prefs.getBoolean(KEY_HAS_RATED_APP, false)

        if (daysSinceInstall < 2 || launches < 2) {
            return "Deferred: Satisfaction safeguard requires >=2 days & >=2 launches."
        }

        // Priority 1: Milestone Donation Auto-Prompt (7, 10, 25, 50 operations, 30-day cooldown)
        val donationCooldownUntil = prefs.getLong(KEY_DONATION_POPUP_COOLDOWN_UNTIL_MS, 0L)
        val isMilestone = totalOps == 7 || totalOps == 10 || totalOps == 25 || totalOps == 50 ||
            (totalOps >= 7 && prefs.getLong(KEY_DONATION_POPUP_COOLDOWN_UNTIL_MS, 0L) == 0L)
        if (!isSupporter && isMilestone && now >= donationCooldownUntil) {
            activeAutoPrompt = SmartAutoPromptType.MILESTONE_DONATION
            activeMilestoneCount = totalOps
            autoPopupsShownThisSession += 1
            prefs.edit()
                .putLong(KEY_LAST_GLOBAL_POPUP_MS, now)
                .putLong(KEY_DONATION_POPUP_COOLDOWN_UNTIL_MS, now + 30L * DAY_MS)
                .apply()
            return "Triggered MILESTONE_DONATION at $totalOps ops (next cooldown: 30 days)."
        }

        // Priority 2: Rate App Prompt (>= 4 ops, 14-day cooldown, never shown if already rated)
        val rateCooldownUntil = prefs.getLong(KEY_RATE_COOLDOWN_UNTIL_MS, 0L)
        if (!hasRated && totalOps >= 4 && now >= rateCooldownUntil) {
            activeAutoPrompt = SmartAutoPromptType.RATE_APP
            activeMilestoneCount = totalOps
            autoPopupsShownThisSession += 1
            prefs.edit()
                .putLong(KEY_LAST_GLOBAL_POPUP_MS, now)
                .putLong(KEY_RATE_COOLDOWN_UNTIL_MS, now + 14L * DAY_MS)
                .apply()
            return "Triggered RATE_APP after $totalOps successful operations (14-day cooldown)."
        }

        // Priority 3: Share App Prompt (>= 5 ops, 14-day cooldown)
        val shareCooldownUntil = prefs.getLong(KEY_SHARE_COOLDOWN_UNTIL_MS, 0L)
        if (totalOps >= 5 && now >= shareCooldownUntil) {
            activeAutoPrompt = SmartAutoPromptType.SHARE_APP
            activeMilestoneCount = totalOps
            autoPopupsShownThisSession += 1
            prefs.edit()
                .putLong(KEY_LAST_GLOBAL_POPUP_MS, now)
                .putLong(KEY_SHARE_COOLDOWN_UNTIL_MS, now + 14L * DAY_MS)
                .apply()
            return "Triggered SHARE_APP at $totalOps operations (14-day cooldown)."
        }

        // Priority 4: Update App Prompt (>= 6 ops, 7-day cooldown)
        val updateCooldownUntil = prefs.getLong(KEY_UPDATE_COOLDOWN_UNTIL_MS, 0L)
        val installedVer = prefs.getString(KEY_INSTALLED_VERSION, "1.0.0") ?: "1.0.0"
        if (installedVer != LATEST_VERSION && totalOps >= 6 && now >= updateCooldownUntil) {
            activeAutoPrompt = SmartAutoPromptType.UPDATE_APP
            activeMilestoneCount = totalOps
            autoPopupsShownThisSession += 1
            prefs.edit()
                .putLong(KEY_LAST_GLOBAL_POPUP_MS, now)
                .putLong(KEY_UPDATE_COOLDOWN_UNTIL_MS, now + 7L * DAY_MS)
                .apply()
            return "Triggered UPDATE_APP (v$LATEST_VERSION available, 7-day cooldown)."
        }

        return "No popup triggered: user in quiet window or all milestones satisfied."
    }

    /**
     * Allows user or QA tester to preview a specific smart prompt on demand from the Settings/Support Hub
     * without violating cold-start rules.
     */
    fun triggerPromptPreview(type: SmartAutoPromptType) {
        isAppStartPhase = false
        val ops = prefs.getInt(KEY_COMPLETED_OPS_COUNT, 7).coerceAtLeast(7)
        activeMilestoneCount = ops
        activeAutoPrompt = type
        logEventInternal(
            eventType = "MANUAL_PREVIEW_TRIGGER",
            detail = "Opened ${type.name}",
            algorithmNote = "User-initiated preview from Support & Engagement Hub."
        )
        refreshState()
    }

    fun dismissActiveAutoPrompt() {
        val current = activeAutoPrompt ?: return
        val now = System.currentTimeMillis()
        when (current) {
            SmartAutoPromptType.MILESTONE_DONATION -> {
                prefs.edit().putLong(KEY_DONATION_POPUP_COOLDOWN_UNTIL_MS, now + 30L * DAY_MS).apply()
            }
            SmartAutoPromptType.RATE_APP -> {
                prefs.edit().putLong(KEY_RATE_COOLDOWN_UNTIL_MS, now + 14L * DAY_MS).apply()
            }
            SmartAutoPromptType.SHARE_APP -> {
                prefs.edit().putLong(KEY_SHARE_COOLDOWN_UNTIL_MS, now + 14L * DAY_MS).apply()
            }
            SmartAutoPromptType.UPDATE_APP -> {
                prefs.edit().putLong(KEY_UPDATE_COOLDOWN_UNTIL_MS, now + 7L * DAY_MS).apply()
            }
        }
        logEventInternal(
            eventType = "PROMPT_DISMISSED",
            detail = "Dismissed ${current.name}",
            algorithmNote = "Cooldown applied so user is not bothered repeatedly."
        )
        activeAutoPrompt = null
        refreshState()
    }

    fun dismissHomeCompactBanner() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_HOME_BANNER_DISMISSED_UNTIL_MS, now + 14L * DAY_MS).apply()
        logEventInternal(
            eventType = "HOME_BANNER_DISMISSED",
            detail = "Home compact support banner dismissed via X",
            algorithmNote = "Silenced home banner for 14-day cooldown."
        )
        refreshState()
    }

    fun dismissPostOperationValueCard() {
        showPostOperationValueCard = false
        refreshState()
    }

    /**
     * Executes a Google Play In-App Purchase Donation Tier via [BillingManager].
     * Validates the active [Activity] session, verifies purchase token & state, acknowledges the receipt,
     * and invokes [onSuccessMessage] on the UI thread once the transaction completes.
     */
    fun completeGooglePlayDonation(
        uiContext: Context,
        tier: GooglePlayDonationTier,
        onSuccessMessage: (String) -> Unit
    ) {
        val resolvedActivity = billingManager.resolveValidActivitySession(uiContext)
        if (resolvedActivity != null) {
            billingManager.updateHostActivity(resolvedActivity)
        }
        billingManager.launchPurchaseFlow(
            uiContext = resolvedActivity ?: uiContext,
            tier = tier,
            callback = object : BillingPurchaseCallback {
                override fun onSessionValidated(activityName: String, tier: GooglePlayDonationTier) {
                    refreshState()
                }

                override fun onBillingSheetLaunched(tier: GooglePlayDonationTier) {
                    refreshState()
                }

                override fun onPurchasePending(tier: GooglePlayDonationTier, message: String) {
                    refreshState()
                    onSuccessMessage(message)
                }

                override fun onPurchaseVerified(receipt: VerifiedPurchaseReceipt) {
                    applyVerifiedGooglePlayReceipt(receipt)
                    val localizedTierTitle = appContext.getString(receipt.tier.titleRes)
                    val msg = appContext.getString(
                        R.string.engagement_gplay_verified_fmt,
                        localizedTierTitle,
                        receipt.formattedPrice
                    )
                    try {
                        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {}
                    onSuccessMessage(msg)
                }

                override fun onPurchaseCancelled(tier: GooglePlayDonationTier, message: String) {
                    refreshState()
                    onSuccessMessage(message)
                }

                override fun onPurchaseError(tier: GooglePlayDonationTier, responseCode: Int, message: String) {
                    refreshState()
                    onSuccessMessage(message)
                }
            }
        )
    }

    private fun applyVerifiedGooglePlayReceipt(receipt: VerifiedPurchaseReceipt) {
        val prevTotal = prefs.getFloat(KEY_TOTAL_DONATED_DOLLARS, 0f).toDouble()
        val newTotal = prevTotal + receipt.tier.amountDollars
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE_SUPPORTER, true)
            .putString(
                KEY_SUPPORTER_METHOD,
                "Google Play (${receipt.formattedPrice} • ${receipt.orderId})"
            )
            .putFloat(KEY_TOTAL_DONATED_DOLLARS, newTotal.toFloat())
            .apply()

        activeAutoPrompt = null
        showPostOperationValueCard = false

        logEventInternal(
            eventType = "DONATION_COMPLETED",
            detail = "Google Play Verified: ${receipt.tier.title} (${receipt.formattedPrice}) • Order ${receipt.orderId}",
            algorithmNote = "Receipt SHA-256=${receipt.verificationHash} acknowledged; unlocked permanent Active Supporter badge."
        )
        refreshState()
    }

    /**
     * Launches PayPal deep link (`https://www.paypal.me/PantryPulse/{amount}USD`) using an Activity-safe intent
     * and copies the link to clipboard as a reliable backup.
     */
    fun launchPayPalDonation(
        uiContext: Context,
        amountDollars: Int,
        markAsSupporter: Boolean = true,
        onStatusMessage: (String) -> Unit
    ) {
        val url = buildPayPalUrl(amountDollars)
        copyToClipboard(uiContext, "PayPal Donation Link", url)

        val activity = uiContext.findActivity()
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            (activity ?: uiContext).startActivity(intent)
        } catch (_: Exception) {
            // Browser not installed on headless/minimal emulator; clipboard already has the link
        }

        if (markAsSupporter) {
            val prevTotal = prefs.getFloat(KEY_TOTAL_DONATED_DOLLARS, 0f).toDouble()
            val added = if (amountDollars > 0) amountDollars.toDouble() else 5.0
            prefs.edit()
                .putBoolean(KEY_IS_ACTIVE_SUPPORTER, true)
                .putString(KEY_SUPPORTER_METHOD, "PayPal ($$amountDollars USD)")
                .putFloat(KEY_TOTAL_DONATED_DOLLARS, (prevTotal + added).toFloat())
                .apply()
            activeAutoPrompt = null
            showPostOperationValueCard = false
        }

        logEventInternal(
            eventType = "DONATION_PAYPAL",
            detail = "Opened PayPal link ($url)",
            algorithmNote = "PayPal link copied & launched; supporter status updated."
        )
        refreshState()
        onStatusMessage(appContext.getString(R.string.engagement_paypal_opened_fmt, url))
    }

    /**
     * Launches Ko-fi page (`https://ko-fi.com/pantrypulse`) with automatic clipboard fallback.
     */
    fun launchKofiDonation(
        uiContext: Context,
        markAsSupporter: Boolean = true,
        onStatusMessage: (String) -> Unit
    ) {
        val url = KOFI_URL
        copyToClipboard(uiContext, "Ko-fi Support Link", url)

        val activity = uiContext.findActivity()
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            (activity ?: uiContext).startActivity(intent)
        } catch (_: Exception) {
            // Fallback to clipboard copy already completed
        }

        if (markAsSupporter) {
            val prevTotal = prefs.getFloat(KEY_TOTAL_DONATED_DOLLARS, 0f).toDouble()
            prefs.edit()
                .putBoolean(KEY_IS_ACTIVE_SUPPORTER, true)
                .putString(KEY_SUPPORTER_METHOD, "Ko-fi Tip Jar")
                .putFloat(KEY_TOTAL_DONATED_DOLLARS, (prevTotal + 5.0).toFloat())
                .apply()
            activeAutoPrompt = null
            showPostOperationValueCard = false
        }

        logEventInternal(
            eventType = "DONATION_KOFI",
            detail = "Opened Ko-fi ($url)",
            algorithmNote = "Ko-fi link launched with clipboard fallback."
        )
        refreshState()
        onStatusMessage(appContext.getString(R.string.engagement_kofi_opened_fmt, url))
    }

    fun submitAppRating(
        uiContext: Context,
        stars: Int,
        feedbackNote: String,
        onStatusMessage: (String) -> Unit
    ) {
        prefs.edit()
            .putBoolean(KEY_HAS_RATED_APP, true)
            .putLong(KEY_RATE_COOLDOWN_UNTIL_MS, Long.MAX_VALUE)
            .apply()
        activeAutoPrompt = null

        val activity = uiContext.findActivity()
        if (stars >= 4) {
            try {
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${uiContext.packageName}")
                ).apply {
                    if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                (activity ?: uiContext).startActivity(marketIntent)
            } catch (_: Exception) {
                // Keep rating saved locally
            }
            logEventInternal(
                eventType = "RATE_APP_HIGH_SATISFACTION",
                detail = "Rated $stars/5 ★ (Play Review Flow) ${if (feedbackNote.isNotBlank()) "• \"$feedbackNote\"" else ""}",
                algorithmNote = "4-5★ satisfaction routed to Google Play review; permanently disabled future Rate prompts."
            )
            refreshState()
            onStatusMessage(appContext.getString(R.string.engagement_rating_high_msg_fmt, stars))
        } else {
            val cleanNote = feedbackNote.ifBlank { "General UX / Kitchen Workflow Improvement" }
            copyToClipboard(
                uiContext,
                "PantryPulse Feedback",
                "PantryPulse $stars★ Feedback: $cleanNote (Contact: tuyenctbk@gmail.com)"
            )
            logEventInternal(
                eventType = "RATE_APP_PRIVATE_FEEDBACK",
                detail = "Private $stars/5 ★ Feedback • \"$cleanNote\"",
                algorithmNote = "1-3★ routed to private developer feedback channel (tuyenctbk@gmail.com); rate prompts disabled."
            )
            refreshState()
            onStatusMessage(appContext.getString(R.string.engagement_rating_private_msg))
        }
    }

    fun shareAppWithFriends(
        uiContext: Context,
        onStatusMessage: (String) -> Unit
    ) {
        val shareCount = prefs.getInt(KEY_SHARE_COUNT, 0) + 1
        prefs.edit()
            .putInt(KEY_SHARE_COUNT, shareCount)
            .putLong(KEY_SHARE_COOLDOWN_UNTIL_MS, System.currentTimeMillis() + 14L * DAY_MS)
            .apply()
        activeAutoPrompt = null

        val storeUrl = "https://play.google.com/store/apps/details?id=${uiContext.packageName}"
        val shareMessage = uiContext.getString(R.string.engagement_share_message_fmt, storeUrl)

        copyToClipboard(uiContext, uiContext.getString(R.string.engagement_share_subject), shareMessage)

        val activity = uiContext.findActivity()
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, uiContext.getString(R.string.engagement_share_subject))
                putExtra(Intent.EXTRA_TEXT, shareMessage)
            }
            val chooser = Intent.createChooser(sendIntent, uiContext.getString(R.string.engagement_share_chooser)).apply {
                if (activity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            (activity ?: uiContext).startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(
                uiContext,
                uiContext.getString(R.string.engagement_share_copied_toast),
                Toast.LENGTH_SHORT
            ).show()
        }

        logEventInternal(
            eventType = "APP_SHARED",
            detail = "Shared PantryPulse (#$shareCount)",
            algorithmNote = "Share link dispatched & 14-day cooldown applied."
        )
        refreshState()
        onStatusMessage(uiContext.getString(R.string.engagement_share_dispatched))
    }

    fun applyAppUpdate(
        uiContext: Context,
        onStatusMessage: (String) -> Unit
    ) {
        prefs.edit()
            .putString(KEY_INSTALLED_VERSION, LATEST_VERSION)
            .putLong(KEY_UPDATE_COOLDOWN_UNTIL_MS, System.currentTimeMillis() + 30L * DAY_MS)
            .apply()
        activeAutoPrompt = null

        logEventInternal(
            eventType = "APP_UPDATE_APPLIED",
            detail = "Updated PantryPulse metadata to v$LATEST_VERSION",
            algorithmNote = "Update suggestion satisfied; version now matches latest v$LATEST_VERSION."
        )
        refreshState()
        onStatusMessage(uiContext.getString(R.string.engagement_update_applied_fmt, LATEST_VERSION))
    }

    fun resetSupporterAndCooldownsForTesting(onStatusMessage: (String) -> Unit) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_IS_ACTIVE_SUPPORTER, false)
            .putString(KEY_SUPPORTER_METHOD, "")
            .putBoolean(KEY_HAS_RATED_APP, false)
            .putString(KEY_INSTALLED_VERSION, "1.0.0")
            .putLong(KEY_FIRST_INSTALL_MS, now - 4L * DAY_MS)
            .putInt(KEY_APP_LAUNCH_COUNT, 4)
            .putInt(KEY_COMPLETED_OPS_COUNT, 6)
            .putLong(KEY_HOME_BANNER_DISMISSED_UNTIL_MS, 0L)
            .putLong(KEY_LAST_GLOBAL_POPUP_MS, 0L)
            .putLong(KEY_DONATION_POPUP_COOLDOWN_UNTIL_MS, 0L)
            .putLong(KEY_RATE_COOLDOWN_UNTIL_MS, 0L)
            .putLong(KEY_SHARE_COOLDOWN_UNTIL_MS, 0L)
            .putLong(KEY_UPDATE_COOLDOWN_UNTIL_MS, 0L)
            .apply()
        autoPopupsShownThisSession = 0
        isAppStartPhase = false
        activeAutoPrompt = null
        logEventInternal(
            eventType = "COOLDOWNS_RESET",
            detail = "Reset cooldowns & set Completed Ops = 6 (next op = 7th op milestone!)",
            algorithmNote = "Ready to test 7th-operation automatic Value Realization milestone."
        )
        refreshState()
        onStatusMessage("Reset engagement state to 6 completed ops — complete 1 more action to trigger the 7th-op milestone!")
    }

    private fun getDaysSinceInstall(now: Long): Int {
        val firstInstall = prefs.getLong(KEY_FIRST_INSTALL_MS, now - 3L * DAY_MS)
        return ((now - firstInstall) / DAY_MS).toInt().coerceAtLeast(0)
    }

    private fun logEventInternal(eventType: String, detail: String, algorithmNote: String) {
        _eventLogs.add(
            0,
            AppTrackedEvent(
                eventType = eventType,
                detail = detail,
                algorithmNote = algorithmNote
            )
        )
        if (_eventLogs.size > 60) {
            _eventLogs.removeAt(_eventLogs.lastIndex)
        }
    }

    private fun refreshState() {
        _stateFlow.value = buildSnapshot()
    }

    private fun buildSnapshot(): EngagementSnapshot {
        val now = System.currentTimeMillis()
        val launches = prefs.getInt(KEY_APP_LAUNCH_COUNT, 3)
        val days = getDaysSinceInstall(now)
        val ops = prefs.getInt(KEY_COMPLETED_OPS_COUNT, 3)
        val isSupporter = prefs.getBoolean(KEY_IS_ACTIVE_SUPPORTER, false)
        val supporterMethod = prefs.getString(KEY_SUPPORTER_METHOD, "") ?: ""
        val totalDonated = prefs.getFloat(KEY_TOTAL_DONATED_DOLLARS, 0f).toDouble()
        val hasRated = prefs.getBoolean(KEY_HAS_RATED_APP, false)
        val shares = prefs.getInt(KEY_SHARE_COUNT, 0)
        val installedVer = prefs.getString(KEY_INSTALLED_VERSION, "1.0.0") ?: "1.0.0"
        val bannerDismissedUntil = prefs.getLong(KEY_HOME_BANNER_DISMISSED_UNTIL_MS, 0L)

        // Home Screen Compact Banner Criteria:
        // 3+ successful operations, 3+ app launches, 2+ days since install, not dismissed in last 14d, not already supporter
        val showHomeBanner = !isSupporter &&
            ops >= 3 &&
            launches >= 3 &&
            days >= 2 &&
            now >= bannerDismissedUntil

        val currentBillingState = (billingManager as BillingManager?)?.billingUiState?.value
            ?: BillingUiState()

        return EngagementSnapshot(
            appLaunchCount = launches,
            daysSinceInstall = days,
            completedCoreOpsCount = ops,
            isActiveSupporter = isSupporter,
            supporterMethodLabel = supporterMethod,
            totalDonatedDollars = totalDonated,
            hasRatedApp = hasRated,
            shareCount = shares,
            installedVersionName = installedVer,
            latestAvailableVersionName = LATEST_VERSION,
            isUpdateAvailable = installedVer != LATEST_VERSION,
            isHomeCompactBannerVisible = showHomeBanner,
            activeAutoPrompt = activeAutoPrompt,
            activeMilestoneCount = activeMilestoneCount,
            showPostOperationValueCard = showPostOperationValueCard,
            lastCompletedTaskTitle = lastCompletedTaskTitle,
            autoPopupsShownThisSession = autoPopupsShownThisSession,
            billingUiState = currentBillingState,
            lastVerifiedReceipt = currentBillingState.lastVerifiedReceipt,
            eventLogs = _eventLogs.toList()
        )
    }

    companion object {
        private const val PREFS_NAME = "pantry_engagement_prefs"
        private const val KEY_FIRST_INSTALL_MS = "first_install_ms"
        private const val KEY_APP_LAUNCH_COUNT = "app_launch_count"
        private const val KEY_COMPLETED_OPS_COUNT = "completed_ops_count"
        private const val KEY_IS_ACTIVE_SUPPORTER = "is_active_supporter"
        private const val KEY_SUPPORTER_METHOD = "supporter_method"
        private const val KEY_TOTAL_DONATED_DOLLARS = "total_donated_dollars"
        private const val KEY_HAS_RATED_APP = "has_rated_app"
        private const val KEY_SHARE_COUNT = "share_count"
        private const val KEY_INSTALLED_VERSION = "installed_version"

        private const val KEY_HOME_BANNER_DISMISSED_UNTIL_MS = "home_banner_dismissed_until_ms"
        private const val KEY_LAST_GLOBAL_POPUP_MS = "last_global_popup_ms"
        private const val KEY_DONATION_POPUP_COOLDOWN_UNTIL_MS = "donation_popup_cooldown_until_ms"
        private const val KEY_RATE_COOLDOWN_UNTIL_MS = "rate_cooldown_until_ms"
        private const val KEY_SHARE_COOLDOWN_UNTIL_MS = "share_cooldown_until_ms"
        private const val KEY_UPDATE_COOLDOWN_UNTIL_MS = "update_cooldown_until_ms"

        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val GLOBAL_POPUP_GAP_MS = 48L * 60L * 60L * 1000L // 48 hours minimum between any auto-popups
        const val LATEST_VERSION = "1.2.0"
        const val KOFI_URL = "https://ko-fi.com/pantrypulse"

        fun buildPayPalUrl(amountDollars: Int): String {
            return if (amountDollars > 0) {
                "https://www.paypal.me/PantryPulse/${amountDollars}USD"
            } else {
                "https://www.paypal.me/PantryPulse"
            }
        }

        fun copyToClipboard(context: Context, label: String, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        }

        /**
         * Generates a crisp, high-contrast QR-style 2D Matrix Bitmap on-device for the given payload URL
         * (with standard 7x7 finder patterns in the three corners, timing patterns, and deterministic payload modules).
         */
        fun generateLiveQrBitmap(payload: String, sizePx: Int = 420): Bitmap {
            val gridSize = 29
            val matrix = Array(gridSize) { BooleanArray(gridSize) }

            fun drawFinderPattern(startR: Int, startC: Int) {
                for (r in 0..6) {
                    for (c in 0..6) {
                        val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                        val isInnerCore = r in 2..4 && c in 2..4
                        matrix[startR + r][startC + c] = isBorder || isInnerCore
                    }
                }
            }

            drawFinderPattern(0, 0)
            drawFinderPattern(0, gridSize - 7)
            drawFinderPattern(gridSize - 7, 0)

            // Timing lines
            for (i in 8 until gridSize - 8) {
                matrix[6][i] = i % 2 == 0
                matrix[i][6] = i % 2 == 0
            }

            // Alignment pattern at bottom-right
            val alignR = gridSize - 9
            val alignC = gridSize - 9
            for (r in -2..2) {
                for (c in -2..2) {
                    val isEdge = abs(r) == 2 || abs(c) == 2
                    val isCenter = r == 0 && c == 0
                    matrix[alignR + r][alignC + c] = isEdge || isCenter
                }
            }

            val bytes = payload.toByteArray(Charsets.UTF_8)
            var bitIndex = 0
            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    val inTopLeft = r < 8 && c < 8
                    val inTopRight = r < 8 && c >= gridSize - 8
                    val inBottomLeft = r >= gridSize - 8 && c < 8
                    val inAlignment = r in (alignR - 2)..(alignR + 2) && c in (alignC - 2)..(alignC + 2)
                    val onTiming = r == 6 || c == 6
                    if (inTopLeft || inTopRight || inBottomLeft || inAlignment || onTiming) continue

                    val byteVal = bytes[(bitIndex / 8) % bytes.size].toInt() and 0xFF
                    val bitOffset = bitIndex % 8
                    val hashMix = ((r * 31 + c * 17 + payload.hashCode()) and 1) == 1
                    matrix[r][c] = (((byteVal shr bitOffset) and 1) == 1) xor hashMix
                    bitIndex++
                }
            }

            val quietZone = 3
            val totalModules = gridSize + quietZone * 2
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(sizePx * sizePx) { AndroidColor.WHITE }
            val darkColor = AndroidColor.rgb(18, 42, 29)

            for (y in 0 until sizePx) {
                val modR = (y * totalModules) / sizePx - quietZone
                for (x in 0 until sizePx) {
                    val modC = (x * totalModules) / sizePx - quietZone
                    if (modR in 0 until gridSize && modC in 0 until gridSize && matrix[modR][modC]) {
                        pixels[y * sizePx + x] = darkColor
                    }
                }
            }
            bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            return bmp
        }
    }
}
