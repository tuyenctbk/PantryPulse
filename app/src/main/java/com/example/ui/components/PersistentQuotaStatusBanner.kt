package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiCapabilityTier
import com.example.ai.UsageQuotaSnapshot
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.CriticalCrimsonContainer
import com.example.ui.theme.ExpiringAmber
import com.example.ui.theme.ExpiringAmberContainer
import com.example.ui.theme.FreshEmerald
import com.example.ui.theme.FreshEmeraldContainer

/**
 * Persistent daily Gemini API quota status banner shown across the main app shell.
 * Displays remaining daily credits (synced from the local DataStore mirror), active capability
 * tier, midnight reset countdown, animated credit gauge, and an inline Eco Lite mode toggle.
 */
@Composable
fun PersistentQuotaStatusBanner(
    quota: UsageQuotaSnapshot,
    onToggleEcoLite: (Boolean) -> Unit,
    onOpenValueReceipt: () -> Unit,
    modifier: Modifier = Modifier,
    hasByokKey: Boolean = false
) {
    val tier = if (hasByokKey) AiCapabilityTier.TIER_1_PRIMARY else quota.activeCapabilityTier

    val targetContainerColor = when (tier) {
        AiCapabilityTier.TIER_1_PRIMARY -> MaterialTheme.colorScheme.surfaceContainerLow
        AiCapabilityTier.TIER_2_ECO_LITE -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f)
        AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
    }
    val targetBorderColor = when (tier) {
        AiCapabilityTier.TIER_1_PRIMARY -> MaterialTheme.colorScheme.outlineVariant
        AiCapabilityTier.TIER_2_ECO_LITE -> ExpiringAmber.copy(alpha = 0.45f)
        AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> CriticalCrimson.copy(alpha = 0.45f)
    }
    val targetAccentColor = when (tier) {
        AiCapabilityTier.TIER_1_PRIMARY -> FreshEmerald
        AiCapabilityTier.TIER_2_ECO_LITE -> ExpiringAmber
        AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> CriticalCrimson
    }
    val targetBadgeBg = when (tier) {
        AiCapabilityTier.TIER_1_PRIMARY -> FreshEmeraldContainer
        AiCapabilityTier.TIER_2_ECO_LITE -> ExpiringAmberContainer
        AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> CriticalCrimsonContainer
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 240),
        label = "quota_banner_container"
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 240),
        label = "quota_banner_border"
    )
    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 240),
        label = "quota_banner_accent"
    )
    val badgeBgColor by animateColorAsState(
        targetValue = targetBadgeBg,
        animationSpec = tween(durationMillis = 240),
        label = "quota_banner_badge_bg"
    )
    val animatedRemainingRatio by animateFloatAsState(
        targetValue = if (hasByokKey) 1f else quota.remainingRatio,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "quota_remaining_ratio"
    )

    val headlineText = when {
        hasByokKey -> stringResource(id = R.string.quota_banner_byok_headline)
        quota.isExhausted -> stringResource(
            id = R.string.quota_banner_exhausted_fmt,
            quota.totalAllowance
        )
        quota.forceLocalOfflineMode -> stringResource(
            id = R.string.quota_banner_local_forced_fmt,
            quota.remainingCredits,
            quota.totalAllowance
        )
        quota.ecoLiteMode -> stringResource(
            id = R.string.quota_banner_eco_credits_fmt,
            quota.remainingCredits,
            quota.totalAllowance,
            quota.remainingEcoLiteCalls
        )
        else -> stringResource(
            id = R.string.quota_banner_credits_fmt,
            quota.remainingCredits,
            quota.totalAllowance
        )
    }

    val tierSubtitle = when {
        hasByokKey -> stringResource(id = R.string.quota_banner_byok_tier)
        tier == AiCapabilityTier.TIER_1_PRIMARY -> stringResource(id = R.string.quota_banner_tier_standard)
        tier == AiCapabilityTier.TIER_2_ECO_LITE -> stringResource(id = R.string.quota_banner_tier_eco)
        else -> stringResource(id = R.string.quota_banner_tier_local)
    }
    val resetSubtitle = stringResource(id = R.string.quota_banner_reset_fmt, quota.resetCountdownText)
    val detailsContentDesc = stringResource(id = R.string.cd_quota_banner_open_details)
    val ecoSwitchContentDesc = stringResource(id = R.string.cd_quota_banner_eco_switch)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("persistent_quota_status_banner"),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left + Center: Remaining Daily Credits, Reset Countdown & Animated Credit Gauge
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = detailsContentDesc) { onOpenValueReceipt() }
                    .tvFocusHighlight(shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .testTag("persistent_quota_details_area"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(badgeBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (tier) {
                            AiCapabilityTier.TIER_1_PRIMARY -> Icons.Default.AutoAwesome
                            AiCapabilityTier.TIER_2_ECO_LITE -> Icons.Default.Bolt
                            AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> Icons.Default.Shield
                        },
                        contentDescription = detailsContentDesc,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = headlineText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("persistent_quota_credits_text")
                    )

                    Text(
                        text = "$tierSubtitle • $resetSubtitle",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedRemainingRatio)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(accentColor)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Subtle vertical divider separating credit telemetry from the Eco Lite switch
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Right: Direct Inline Eco Lite Mode Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (quota.ecoLiteMode) ExpiringAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = stringResource(id = R.string.quota_banner_eco_toggle_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (quota.ecoLiteMode) {
                                ExpiringAmber
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                    }
                }

                Switch(
                    checked = quota.ecoLiteMode,
                    onCheckedChange = onToggleEcoLite,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = ExpiringAmber,
                        checkedBorderColor = ExpiringAmber,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .tvFocusHighlight(shape = RoundedCornerShape(50))
                        .semantics { contentDescription = ecoSwitchContentDesc }
                        .testTag("persistent_banner_eco_lite_toggle")
                )
            }
        }
    }
}
