package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiCapabilityTier
import com.example.ai.AiFeatureType
import com.example.ai.UsageQuotaSnapshot
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.CriticalCrimsonContainer
import com.example.ui.theme.ExpiringAmber
import com.example.ui.theme.ExpiringAmberContainer
import com.example.ui.theme.FreshEmerald
import com.example.ui.theme.FreshEmeraldContainer

/**
 * Top Bar Pill showing live Free-Tier AI Capability Tier & Remaining Daily Credits.
 * Tapping opens the AI Value Receipt & Free-Tier Playbook Hub.
 */
@Composable
fun AiQuotaStatusPill(
    quota: UsageQuotaSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tier = quota.activeCapabilityTier
    val (bgColor, fgColor) = when (tier) {
        AiCapabilityTier.TIER_1_PRIMARY -> FreshEmeraldContainer to FreshEmerald
        AiCapabilityTier.TIER_2_ECO_LITE -> ExpiringAmberContainer to ExpiringAmber
        AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> CriticalCrimsonContainer to CriticalCrimson
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .clickable { onClick() }
            .testTag("ai_quota_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (tier) {
                    AiCapabilityTier.TIER_1_PRIMARY -> Icons.Default.AutoAwesome
                    AiCapabilityTier.TIER_2_ECO_LITE -> Icons.Default.Bolt
                    AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> Icons.Default.Shield
                },
                contentDescription = stringResource(id = R.string.value_receipt_title),
                tint = fgColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when (tier) {
                    AiCapabilityTier.TIER_1_PRIMARY -> stringResource(id = R.string.quota_pill_standard, quota.remainingCredits)
                    AiCapabilityTier.TIER_2_ECO_LITE -> stringResource(id = R.string.quota_pill_eco_lite, quota.remainingCredits)
                    AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> stringResource(id = R.string.quota_pill_local)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fgColor
            )
        }
    }
}

/**
 * Stage 1: At >= 80% Usage — The "Soft Nudge" Banner.
 * Prevents surprise paywall ambushes while the user is mid-task.
 */
@Composable
fun QuotaSoftNudgeBanner(
    quota: UsageQuotaSnapshot,
    onEnableEcoLite: () -> Unit,
    onOpenValueReceipt: () -> Unit
) {
    if (!quota.isSoftNudgeThreshold || quota.ecoLiteMode) return

    val percentInt = (quota.usageRatio * 100).toInt().coerceIn(80, 99)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quota_soft_nudge_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = ExpiringAmberContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = ExpiringAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "You've used $percentInt% of today's standard AI allowance (${quota.remainingCredits} checks left • Resets in ${quota.resetCountdownText}).",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF311F00)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Turn on Eco Lite Mode (Gemini 3.5 Flash-Lite) to use our high-speed lightweight AI and stretch your remaining quota 3× further.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4E3300)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onEnableEcoLite,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpiringAmber,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("soft_nudge_enable_eco_lite_button")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.quota_soft_nudge_switch), style = MaterialTheme.typography.labelMedium)
                }

                TextButton(onClick = onOpenValueReceipt) {
                    Text("View Quota Details", color = Color(0xFF311F00))
                }
            }
        }
    }
}

/**
 * Stage 2: On Rate-Limit (429) or 100% Soft Limit — Self-Serve Smart Quota Recovery Card.
 * Presents the 4 user suggestions in order of friction so user progress is never trapped.
 */
@Composable
fun SmartQuotaRecoveryCard(
    feature: AiFeatureType,
    quota: UsageQuotaSnapshot,
    onClaimGraceCredits: () -> Unit,
    onRetryEcoLite: () -> Unit,
    onUseLocalFallback: () -> Unit,
    onOpenValueReceipt: () -> Unit
) {
    val localCopy = when (feature) {
        AiFeatureType.VISION_SCAN ->
            "Use Honor Pledge / On-Device Shelf Check-In & queue photo for cloud verification (0 cloud credits)."
        AiFeatureType.RECIPE_RANKING ->
            "Synthesize 3 Ranked Rescue Recipes immediately using the On-Device Chef Engine (0 cloud credits)."
        AiFeatureType.SOUS_CHEF_CHAT ->
            "Use the Instant Local Reflex Chef for context-aware cooking & storage answers (0 cloud credits)."
        AiFeatureType.VOICE_MEMO_PARSE ->
            "Save & parse spoken grocery transcript on-device using Local Culinary Parser (0 cloud credits)."
    }

    val localButtonLabel = when (feature) {
        AiFeatureType.VISION_SCAN -> "1-Tap Honor Check-In & Queue Photo (0 Credits)"
        AiFeatureType.RECIPE_RANKING -> "Run On-Device Chef Synthesis (0 Credits)"
        AiFeatureType.SOUS_CHEF_CHAT -> "Ask Local Reflex Chef (0 Credits)"
        AiFeatureType.VOICE_MEMO_PARSE -> "Parse Raw Transcript On-Device (0 Credits)"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_quota_recovery_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = CriticalCrimson,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.quota_recovery_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.quota_recovery_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Suggestion 1: Claim One-Time Grace Credits (+3 Free Credits)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "1. Claim One-Time Grace Credits (+3 Free Credits)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Mid-task? Claim +3 Free Grace Credits (once per day) to finish what you're working on right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onClaimGraceCredits,
                        enabled = !quota.graceClaimed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("claim_grace_credits_button")
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (quota.graceClaimed) "Grace Credits Already Claimed Today" else "Claim +3 Free Grace Credits"
                        )
                    }
                }
            }

            // Suggestion 2: Retry with Eco Lite AI (Fast & Quota-Friendly)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "2. Retry with Eco Lite AI (Fast & Quota-Friendly)",
                        style = MaterialTheme.typography.labelLarge,
                        color = ExpiringAmber
                    )
                    Text(
                        text = "Run this request using Eco Lite AI (Gemini 3.5 Flash-Lite) — uses a lighter, faster model tier with higher free availability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onRetryEcoLite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("retry_eco_lite_button")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run with Eco Lite AI ⚡")
                    }
                }
            }

            // Suggestion 3: Use the Zero-Quota Local Alternative (Never Lose Progress)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "3. Use Zero-Quota Local Alternative (0 Cloud Credits)",
                        style = MaterialTheme.typography.labelLarge,
                        color = FreshEmerald
                    )
                    Text(
                        text = localCopy,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onUseLocalFallback,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FreshEmerald,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("use_local_fallback_button")
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(localButtonLabel)
                    }
                }
            }

            // Suggestion 4: Visible Reset Countdown + Value Receipt link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Free allowance resets in ${quota.resetCountdownText} (midnight)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onOpenValueReceipt) {
                    Text("Value Receipt →", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Personal "AI Value Receipt", Capability Tier Control & Free-Tier Playbook Hub Dialog.
 */
@Composable
fun AiValueReceiptDialog(
    quota: UsageQuotaSnapshot,
    rescuedMealsCount: Int,
    savedDollarsTotal: Double,
    onToggleEcoLite: (Boolean) -> Unit,
    onToggleForceLocal: (Boolean) -> Unit,
    onClaimGraceCredits: () -> Unit,
    onSimulatePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showPrivacyPolicyDialog by rememberSaveable { mutableStateOf(false) }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.value_receipt_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Personal Value Receipt Card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "TODAY'S AI VALUE RECEIPT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${quota.visionUsed} shelf scans • ${quota.recipeUsed} rescue menus • ${quota.chatUsed} sous-chef chats • ${quota.audioUsed} voice memos",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Cumulative impact: $rescuedMealsCount meals rescued • $${String.format("%.2f", savedDollarsTotal)} saved from food waste",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // 2. Active Capability Tier & Quota Ledger Progress
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = quota.activeCapabilityTier.badgeLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${quota.remainingCredits}/${quota.totalAllowance} left",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Active Model: ${quota.activeCapabilityTier.modelDisplayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(quota.usageRatio)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            quota.isExhausted -> CriticalCrimson
                                            quota.isSoftNudgeThreshold -> ExpiringAmber
                                            else -> FreshEmerald
                                        }
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Automatic Rollover Reset in ${quota.resetCountdownText} (${quota.dateIso}) • Atomic Deduct-with-Refund & 45s Dedup Cache active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. Tier Switches & One-Time Grace Credits
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Eco Lite Mode (Tier 2 ⚡)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Route to Gemini 3.5 Flash-Lite to stretch quota 3× further",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = quota.ecoLiteMode,
                        onCheckedChange = onToggleEcoLite,
                        modifier = Modifier.testTag("switch_eco_lite_mode")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zero-Cost Local Mode (Tier 3 🛡️)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Use On-Device Culinary Synthesis & Local Reflex (0 cloud tokens)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = quota.forceLocalOfflineMode,
                        onCheckedChange = onToggleForceLocal,
                        modifier = Modifier.testTag("switch_force_local_mode")
                    )
                }

                Button(
                    onClick = onClaimGraceCredits,
                    enabled = !quota.graceClaimed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_claim_grace_button")
                ) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (quota.graceClaimed) {
                            "+3 Grace Credits Claimed Today"
                        } else {
                            "Claim +3 Free Grace Credits (1x/Day)"
                        }
                    )
                }

                HorizontalDivider()

                // 4. Interactive Playbook Simulator (Test Stage 1 80% & Stage 2 100% Recovery)
                Text(
                    text = "Preview Free-Tier Playbook Stages:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !quota.isSoftNudgeThreshold && !quota.isExhausted,
                        onClick = { onSimulatePreset("HEALTHY") },
                        label = { Text("Healthy (30%)") },
                        modifier = Modifier.testTag("simulate_healthy_chip")
                    )
                    FilterChip(
                        selected = quota.isSoftNudgeThreshold,
                        onClick = { onSimulatePreset("SOFT_NUDGE_80") },
                        label = { Text("Stage 1: 80% Soft Nudge") },
                        modifier = Modifier.testTag("simulate_soft_nudge_chip")
                    )
                    FilterChip(
                        selected = quota.isExhausted,
                        onClick = { onSimulatePreset("EXHAUSTED_100") },
                        label = { Text("Stage 2: 100% / 429 Recovery") },
                        modifier = Modifier.testTag("simulate_exhausted_chip")
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showPrivacyPolicyDialog = true },
                modifier = Modifier.testTag("open_privacy_policy_button")
            ) {
                Text(stringResource(id = R.string.privacy_policy_btn))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_done))
            }
        }
    )
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.privacy_policy_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Effective Date: November 15, 2026",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "1. Local-First Pantry & Inventory Storage\nPantryPulse stores your fridge inventory, expiry countdowns, owned appliances, spice rack preferences, and restock list locally on your device using Android Room Database and Jetpack DataStore. Your inventory works 100% offline.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "2. Camera & Photo Picker Usage\nWhen you use Fridge Vision or Receipt Scan, you may capture a photo via CameraX (android.permission.CAMERA) or select photos using the zero-permission Android Photo Picker. Images are downscaled on-device (<=896px) before being sent to Firebase AI Logic (Gemini) solely to identify ingredients and estimate freshness.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "3. Voice Dictation & Speech Output\nVoice-to-Inventory and Sous-Chef voice queries use Android's system Speech Recognizer (RecognizerIntent) only when you tap the microphone button. Hands-free recipe reading uses on-device Android TextToSpeech.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "4. Zero-Cost Local Mode & Data Control\nYou can enable Zero-Cost Local Mode (Tier 3) at any time so 100% of shelf check-ins, recipe synthesis, and Sous-Chef responses run locally on your device with zero cloud requests. You can delete any inventory item or clear chat history at any time.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "5. Contact\nFor privacy inquiries, contact: tuyenctbk@gmail.com",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_done))
            }
        }
    )
}
