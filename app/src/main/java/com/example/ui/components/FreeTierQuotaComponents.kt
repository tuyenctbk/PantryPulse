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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
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
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .testTag("ai_quota_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 6.dp),
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
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (compact) {
                    when (tier) {
                        AiCapabilityTier.TIER_1_PRIMARY -> stringResource(id = R.string.quota_pill_compact_standard_fmt, quota.remainingCredits)
                        AiCapabilityTier.TIER_2_ECO_LITE -> stringResource(id = R.string.quota_pill_compact_eco_fmt, quota.remainingCredits)
                        AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> stringResource(id = R.string.quota_pill_compact_local)
                    }
                } else {
                    when (tier) {
                        AiCapabilityTier.TIER_1_PRIMARY -> stringResource(id = R.string.quota_pill_standard, quota.remainingCredits)
                        AiCapabilityTier.TIER_2_ECO_LITE -> stringResource(id = R.string.quota_pill_eco_lite, quota.remainingCredits)
                        AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> stringResource(id = R.string.quota_pill_local)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fgColor,
                maxLines = 1
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
                        text = stringResource(
                            id = R.string.quota_soft_nudge_usage_fmt,
                            percentInt,
                            quota.remainingCredits,
                            quota.resetCountdownText
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF311F00)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(id = R.string.quota_soft_nudge_body),
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
                    Text(stringResource(id = R.string.quota_view_details), color = Color(0xFF311F00))
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
        AiFeatureType.VISION_SCAN -> stringResource(id = R.string.quota_local_copy_vision)
        AiFeatureType.RECIPE_RANKING -> stringResource(id = R.string.quota_local_copy_recipes)
        AiFeatureType.SOUS_CHEF_CHAT -> stringResource(id = R.string.quota_local_copy_chat)
        AiFeatureType.VOICE_MEMO_PARSE -> stringResource(id = R.string.quota_local_copy_voice)
        AiFeatureType.SHELF_LIFE_PRESERVATION -> stringResource(id = R.string.quota_local_copy_preservation)
    }

    val localButtonLabel = when (feature) {
        AiFeatureType.VISION_SCAN -> stringResource(id = R.string.quota_local_btn_vision)
        AiFeatureType.RECIPE_RANKING -> stringResource(id = R.string.quota_local_btn_recipes)
        AiFeatureType.SOUS_CHEF_CHAT -> stringResource(id = R.string.quota_local_btn_chat)
        AiFeatureType.VOICE_MEMO_PARSE -> stringResource(id = R.string.quota_local_btn_voice)
        AiFeatureType.SHELF_LIFE_PRESERVATION -> stringResource(id = R.string.quota_local_btn_preservation)
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
                        text = stringResource(id = R.string.quota_grace_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(id = R.string.quota_grace_body),
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
                            if (quota.graceClaimed) {
                                stringResource(id = R.string.quota_grace_claimed_today)
                            } else {
                                stringResource(id = R.string.quota_grace_claim_btn)
                            }
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
                        text = stringResource(id = R.string.quota_eco_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = ExpiringAmber
                    )
                    Text(
                        text = stringResource(id = R.string.quota_eco_body),
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
                        Text(stringResource(id = R.string.quota_eco_run_btn))
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
                        text = stringResource(id = R.string.quota_local_section_title),
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
                        text = stringResource(id = R.string.quota_reset_midnight_fmt, quota.resetCountdownText),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = onOpenValueReceipt) {
                    Text(stringResource(id = R.string.quota_value_receipt_link), style = MaterialTheme.typography.labelSmall)
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
    hasByokKey: Boolean = false,
    byokKeyPreview: String = "",
    onSaveByokKey: (String) -> Unit = {},
    onClearByokKey: () -> Unit = {},
    onPurchaseCreditPack: (Int) -> Unit = {},
    onToggleEcoLite: (Boolean) -> Unit,
    onToggleForceLocal: (Boolean) -> Unit,
    onClaimGraceCredits: () -> Unit,
    onSimulatePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showPrivacyPolicyDialog by rememberSaveable { mutableStateOf(false) }
    var byokInput by rememberSaveable { mutableStateOf("") }

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
                            text = stringResource(id = R.string.receipt_today_header),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                id = R.string.receipt_breakdown_fmt,
                                quota.visionUsed,
                                quota.recipeUsed,
                                quota.chatUsed,
                                quota.audioUsed
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(
                                id = R.string.receipt_cumulative_fmt,
                                rescuedMealsCount,
                                savedDollarsTotal
                            ),
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
                                text = stringResource(id = quota.activeCapabilityTier.badgeLabelRes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (quota.ecoLiteMode && !quota.isExhausted) {
                                    stringResource(
                                        id = R.string.receipt_remaining_eco_fmt,
                                        quota.remainingCredits,
                                        quota.totalAllowance,
                                        quota.remainingEcoLiteCalls
                                    )
                                } else {
                                    stringResource(
                                        id = R.string.receipt_remaining_fmt,
                                        quota.remainingCredits,
                                        quota.totalAllowance
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = stringResource(
                                id = R.string.receipt_active_model_fmt,
                                quota.activeCapabilityTier.modelDisplayName
                            ),
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
                            text = stringResource(
                                id = R.string.receipt_rollover_footer_fmt,
                                quota.resetCountdownText,
                                quota.dateIso
                            ),
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
                            text = stringResource(id = R.string.receipt_eco_lite_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(id = R.string.receipt_eco_lite_subtitle),
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
                            text = stringResource(id = R.string.receipt_local_mode_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(id = R.string.receipt_local_mode_subtitle),
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
                            stringResource(id = R.string.receipt_grace_claimed_short)
                        } else {
                            stringResource(id = R.string.receipt_grace_claim_short)
                        }
                    )
                }

                HorizontalDivider()

                // 3b. Zero-Cost Power Upgrades: Encrypted BYOK + Non-Expiring Credit Packs
                Text(
                    text = stringResource(id = R.string.byok_section_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.byok_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasByokKey) {
                                stringResource(id = R.string.byok_card_active_fmt, byokKeyPreview)
                            } else {
                                stringResource(id = R.string.byok_card_inactive)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasByokKey) FreshEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = byokInput,
                            onValueChange = { byokInput = it },
                            label = { Text(stringResource(id = R.string.byok_input_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("byok_api_key_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onSaveByokKey(byokInput)
                                    byokInput = ""
                                },
                                enabled = byokInput.trim().length >= 16,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("byok_save_key_button")
                            ) {
                                Text(stringResource(id = R.string.byok_save_btn))
                            }
                            if (hasByokKey) {
                                OutlinedButton(
                                    onClick = onClearByokKey,
                                    modifier = Modifier.testTag("byok_clear_key_button")
                                ) {
                                    Text(stringResource(id = R.string.byok_clear_btn))
                                }
                            }
                        }
                    }
                }

                // Non-expiring Consumable AI Credit Packs
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.credit_packs_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (quota.purchasedCreditBalance > 0) {
                            Text(
                                text = stringResource(id = R.string.credit_pack_balance_fmt, quota.purchasedCreditBalance),
                                style = MaterialTheme.typography.labelMedium,
                                color = FreshEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onPurchaseCreditPack(30) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("buy_credit_pack_30_button")
                            ) {
                                Text(
                                    text = stringResource(id = R.string.credit_pack_30_btn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            OutlinedButton(
                                onClick = { onPurchaseCreditPack(100) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("buy_credit_pack_100_button")
                            ) {
                                Text(
                                    text = stringResource(id = R.string.credit_pack_100_btn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
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
                    text = stringResource(id = R.string.privacy_effective_date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.privacy_sec_1),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(id = R.string.privacy_sec_2),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(id = R.string.privacy_sec_3),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(id = R.string.privacy_sec_4),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(id = R.string.privacy_sec_5),
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
