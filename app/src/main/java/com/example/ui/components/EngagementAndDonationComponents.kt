package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.engagement.AppEngagementAndDonationManager
import com.example.ui.engagement.EngagementSnapshot
import com.example.ui.engagement.GooglePlayDonationTier
import com.example.ui.engagement.SmartAutoPromptType
import com.example.ui.engagement.findActivity

/**
 * TopAppBar action pill:
 * Displays an "Active Supporter" badge once the user supports via Google Play / PayPal / Ko-fi,
 * or a persistent "Support" coffee/heart action button otherwise.
 */
@Composable
fun SupporterHeaderActionChip(
    engagement: EngagementSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (engagement.isActiveSupporter) {
        Color(0xFF1F6E43)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val fgColor = if (engagement.isActiveSupporter) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
            .testTag(if (engagement.isActiveSupporter) "active_supporter_badge" else "header_support_action_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (engagement.isActiveSupporter) Icons.Default.Verified else Icons.Default.Favorite,
                contentDescription = if (engagement.isActiveSupporter) "Active Supporter Badge" else "Support Development",
                tint = if (engagement.isActiveSupporter) Color(0xFFFFD54F) else Color(0xFFD9534F),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (engagement.isActiveSupporter) {
                    stringResource(id = R.string.support_chip_active)
                } else {
                    stringResource(id = R.string.support_chip_default)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fgColor
            )
        }
    }
}

/**
 * Placement 1: Home Screen Compact Banner
 * Triggered only after 3+ successful operations, 3+ app launches, and 2+ days since install.
 * Dismissing via "X" silences the banner for a 14-day cooldown.
 * Permanently hidden if the user is an Active Supporter.
 */
@Composable
fun HomeCompactSupportBanner(
    engagement: EngagementSnapshot,
    onOpenSupportDialog: () -> Unit,
    onOpenRateDialog: () -> Unit,
    onShareApp: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!engagement.isHomeCompactBannerVisible || engagement.isActiveSupporter) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("home_compact_support_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LocalCafe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.home_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(
                                id = R.string.home_banner_body_fmt,
                                engagement.completedCoreOpsCount,
                                engagement.appLaunchCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                IconButton(
                    onClick = onDismissBanner,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("dismiss_home_support_banner")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Silence banner for 14 days",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSupportDialog,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("home_banner_support_button")
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(stringResource(id = R.string.btn_buy_coffee), style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onOpenRateDialog,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("home_banner_rate_button")
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(id = R.string.btn_rate_star), style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onShareApp,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("home_banner_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(id = R.string.btn_share), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Strategic Placement 1: Post-Operation Success Card (Value Realization Moment)
 * Appears inline right after a user successfully completes a core task.
 */
@Composable
fun PostOperationValueRealizationCard(
    engagement: EngagementSnapshot,
    onOpenSupportDialog: () -> Unit,
    onOpenRateDialog: () -> Unit,
    onShareApp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!engagement.showPostOperationValueCard || engagement.isActiveSupporter) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("post_operation_value_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF173827)
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFFA6F2CA),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(
                                id = R.string.post_op_completed_fmt,
                                engagement.lastCompletedTaskTitle.ifBlank { "Zero-Waste Action" }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(id = R.string.post_op_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD2E8DC)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xFFD2E8DC),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenSupportDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE05A36),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("post_op_coffee_button")
                ) {
                    Icon(Icons.Default.LocalCafe, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buy Dev a Coffee ☕", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onOpenRateDialog,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Rate App ★", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onShareApp,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Share", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Smart Auto-Suggestion Dialog:
 * Automatically pops up ONLY when the Satisfaction Safeguard Algorithm triggers
 * (never on app start, max 1 per session, 14d/30d cooldowns).
 */
@Composable
fun SmartAutoSuggestionDialog(
    engagement: EngagementSnapshot,
    onGooglePlayDonate: (GooglePlayDonationTier) -> Unit,
    onPayPalDonate: (Int) -> Unit,
    onKofiDonate: () -> Unit,
    onSubmitRating: (Int, String) -> Unit,
    onShareApp: () -> Unit,
    onApplyUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val promptType = engagement.activeAutoPrompt ?: return

    var selectedStars by rememberSaveable { mutableIntStateOf(5) }
    var ratingFeedback by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (promptType) {
                        SmartAutoPromptType.MILESTONE_DONATION -> Icons.Default.Favorite
                        SmartAutoPromptType.RATE_APP -> Icons.Default.Star
                        SmartAutoPromptType.SHARE_APP -> Icons.Default.Share
                        SmartAutoPromptType.UPDATE_APP -> Icons.Default.SystemUpdate
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = promptType.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Smart Value-Moment Trigger • Never shown on startup",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (promptType) {
                    SmartAutoPromptType.MILESTONE_DONATION -> {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🎉 Milestone Unlocked: ${engagement.completedCoreOpsCount} Tasks Completed!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "You've completed ${engagement.completedCoreOpsCount} zero-waste kitchen actions! Consider supporting independent, 100% ad-free development.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        MultiChannelDonationSelectorContent(
                            engagement = engagement,
                            onGooglePlayDonate = onGooglePlayDonate,
                            onPayPalDonate = onPayPalDonate,
                            onKofiDonate = onKofiDonate
                        )
                    }

                    SmartAutoPromptType.RATE_APP -> {
                        Text(
                            text = "You've used PantryPulse for ${engagement.completedCoreOpsCount} successful kitchen tasks! How would you rate your experience?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        SmartTwoStageRatingContent(
                            selectedStars = selectedStars,
                            onSelectStars = { selectedStars = it },
                            ratingFeedback = ratingFeedback,
                            onFeedbackChange = { ratingFeedback = it },
                            onSubmit = { onSubmitRating(selectedStars, ratingFeedback) },
                            submitButtonTag = "submit_rate_app_button"
                        )
                    }

                    SmartAutoPromptType.SHARE_APP -> {
                        Text(
                            text = "Help friends & family stop throwing away groceries! Share PantryPulse's CameraX Fridge Vision & Smart Shopping List.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onShareApp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_share_app_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share PantryPulse Invite Link")
                        }
                    }

                    SmartAutoPromptType.UPDATE_APP -> {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Update v${engagement.latestAvailableVersionName} Ready (Installed: v${engagement.installedVersionName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Smart Shopping List with automatic Low-Stock Threshold sync & Exportable Checklist\n" +
                                        "• 30-Day Freezer Vault & Shelf-Life Preservation Coach\n" +
                                        "• Activity-verified 1-Tap Google Play Support & 20+ Language Localization",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Button(
                            onClick = onApplyUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_update_app_button")
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install Update v${engagement.latestAvailableVersionName} Now")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_auto_prompt_button")
            ) {
                Text("Not Now (Snooze)")
            }
        }
    )
}

@Composable
private fun SmartTwoStageRatingContent(
    selectedStars: Int,
    onSelectStars: (Int) -> Unit,
    ratingFeedback: String,
    onFeedbackChange: (String) -> Unit,
    onSubmit: () -> Unit,
    submitButtonTag: String
) {
    val feedbackTopics = listOf(
        "Camera Scan Accuracy",
        "Rescue Recipes",
        "Shopping List Sync",
        "Freezer & Shelf-Life Tips",
        "Voice Dictation"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            (1..5).forEach { star ->
                IconButton(
                    onClick = { onSelectStars(star) },
                    modifier = Modifier.testTag("rate_star_$star")
                ) {
                    Icon(
                        imageVector = if (star <= selectedStars) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "$star Stars",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Surface(
            color = if (selectedStars >= 4) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (selectedStars >= 4) {
                    stringResource(id = R.string.rate_high_satisfaction_badge)
                } else {
                    stringResource(id = R.string.rate_low_satisfaction_badge)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(10.dp)
            )
        }

        if (selectedStars <= 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                feedbackTopics.forEach { topic ->
                    val isSelected = ratingFeedback.contains(topic, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val updated = if (ratingFeedback.isBlank()) topic else "$ratingFeedback, $topic"
                            onFeedbackChange(updated)
                        },
                        label = { Text(topic, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = ratingFeedback,
            onValueChange = onFeedbackChange,
            label = {
                Text(
                    if (selectedStars >= 4) {
                        "What do you love most? (Optional)"
                    } else {
                        "How can we improve this for your kitchen?"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(submitButtonTag)
        ) {
            Icon(
                imageVector = if (selectedStars >= 4) Icons.Default.Star else Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (selectedStars >= 4) {
                    stringResource(id = R.string.rate_btn_submit_play_fmt, selectedStars)
                } else {
                    stringResource(id = R.string.rate_btn_send_private_feedback)
                }
            )
        }
    }
}

/**
 * Static & Permanent Access Hub:
 * Accessible anytime from the TopAppBar Support / Active Supporter button.
 * Contains:
 * 1. Multi-Channel Support & Donations (Google Play 1-Tap Billing, PayPal + Live QR, Ko-fi)
 * 2. Rate, Share & Update Controls
 * 3. App Event Logging & Anti-Annoyance Trigger Algorithm Inspector
 */
@Composable
fun SupportAndEngagementHubDialog(
    engagement: EngagementSnapshot,
    onGooglePlayDonate: (GooglePlayDonationTier) -> Unit,
    onPayPalDonate: (Int) -> Unit,
    onKofiDonate: () -> Unit,
    onSubmitRating: (Int, String) -> Unit,
    onShareApp: () -> Unit,
    onApplyUpdate: () -> Unit,
    onTriggerPreviewPrompt: (SmartAutoPromptType) -> Unit,
    onResetCooldownsForTesting: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSection by rememberSaveable { mutableIntStateOf(0) }
    var selectedStars by rememberSaveable { mutableIntStateOf(5) }
    var ratingNote by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (engagement.isActiveSupporter) Icons.Default.Verified else Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (engagement.isActiveSupporter) Color(0xFF1F6E43) else Color(0xFFE05A36)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (engagement.isActiveSupporter) {
                                stringResource(id = R.string.hub_title_active)
                            } else {
                                stringResource(id = R.string.hub_title_default)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        label = { Text(stringResource(id = R.string.hub_tab_donate), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("hub_tab_donate")
                    )
                    FilterChip(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        label = { Text(stringResource(id = R.string.hub_tab_rate_share), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("hub_tab_rate_share")
                    )
                    FilterChip(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        label = {
                            Text(
                                stringResource(id = R.string.hub_tab_event_log, engagement.eventLogs.size),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("hub_tab_event_log")
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 490.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedSection) {
                    0 -> {
                        MultiChannelDonationSelectorContent(
                            engagement = engagement,
                            onGooglePlayDonate = onGooglePlayDonate,
                            onPayPalDonate = onPayPalDonate,
                            onKofiDonate = onKofiDonate
                        )
                    }

                    1 -> {
                        // Static Rate, Share & Update Section
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "1. Rate PantryPulse ${if (engagement.hasRatedApp) "(Rated ✓)" else ""}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                SmartTwoStageRatingContent(
                                    selectedStars = selectedStars,
                                    onSelectStars = { selectedStars = it },
                                    ratingFeedback = ratingNote,
                                    onFeedbackChange = { ratingNote = it },
                                    onSubmit = { onSubmitRating(selectedStars, ratingNote) },
                                    submitButtonTag = "static_rate_submit_button"
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "2. Share PantryPulse (${engagement.shareCount} shares)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Invite friends & family to cut food waste with CameraX Fridge Vision & Smart Shopping List.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedButton(
                                    onClick = onShareApp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("static_share_app_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share App Link")
                                }
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "3. App Version & Update Check",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Installed: v${engagement.installedVersionName} • Latest: v${engagement.latestAvailableVersionName}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Button(
                                    onClick = onApplyUpdate,
                                    enabled = engagement.isUpdateAvailable,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("static_update_app_button")
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (engagement.isUpdateAvailable) {
                                            "Apply Update v${engagement.latestAvailableVersionName}"
                                        } else {
                                            "Up to Date (v${engagement.installedVersionName} ✓)"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // Event Logging & Smart Trigger Algorithm Inspector
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Anti-Annoyance Trigger Telemetry",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "• App Launches: ${engagement.appLaunchCount} • Days Installed: ${engagement.daysSinceInstall}d\n" +
                                        "• Completed Core Operations: ${engagement.completedCoreOpsCount}\n" +
                                        "• Auto-Popups This Session: ${engagement.autoPopupsShownThisSession} / 1 max\n" +
                                        "• Cold-Start Popup Guard: ACTIVE (Never pops up on launch)\n" +
                                        "• Cooldowns: 14d Banner/Rate/Share • 30d Milestone • 48h Global Gap",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Text(
                            text = "Preview / Test Smart Prompts:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = onResetCooldownsForTesting,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("reset_engagement_6_ops_button")
                            ) {
                                Text("Set 6 Ops (Next Op = Milestone)", style = MaterialTheme.typography.labelSmall)
                            }
                            SmartAutoPromptType.entries.forEach { type ->
                                OutlinedButton(
                                    onClick = { onTriggerPreviewPrompt(type) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(type.name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "Tracked App Events (${engagement.eventLogs.size}):",
                            style = MaterialTheme.typography.labelLarge
                        )

                        engagement.eventLogs.forEach { ev ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = ev.eventType,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = ev.formattedTime,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = ev.detail,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (ev.algorithmNote.isNotBlank()) {
                                        Text(
                                            text = "Algorithm: ${ev.algorithmNote}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/**
 * Multi-Channel Donation Selector:
 * 1. Google Play In-App Purchase ($1.99, $4.99, $9.99, $19.99) with Activity-Session Verification Sheet
 * 2. PayPal & Live QR Code ($3, $5, $10, Any Amount) + Copy/Share Link
 * 3. Web Platform (Ko-fi) with Intent + Clipboard Fallback
 */
@Composable
private fun MultiChannelDonationSelectorContent(
    engagement: EngagementSnapshot,
    onGooglePlayDonate: (GooglePlayDonationTier) -> Unit,
    onPayPalDonate: (Int) -> Unit,
    onKofiDonate: () -> Unit
) {
    val context = LocalContext.current
    val currentActivity = remember(context) { context.findActivity() }
    val resolvedSessionLabel = currentActivity?.javaClass?.simpleName
        ?: engagement.billingUiState.activeHostActivityName.takeIf { it != "None" }
        ?: "MainActivity"

    var selectedPayPalPreset by rememberSaveable { mutableIntStateOf(5) }
    var customPayPalAmountText by rememberSaveable { mutableStateOf("15") }
    var useCustomPayPalAmount by rememberSaveable { mutableStateOf(false) }
    var showPayPalQrCode by rememberSaveable { mutableStateOf(true) }

    val activePayPalAmount = if (useCustomPayPalAmount) {
        customPayPalAmountText.toIntOrNull()?.coerceIn(1, 500) ?: 5
    } else {
        selectedPayPalPreset
    }
    val payPalUrl = remember(activePayPalAmount) {
        AppEngagementAndDonationManager.buildPayPalUrl(activePayPalAmount)
    }
    val qrBitmap = remember(payPalUrl) {
        AppEngagementAndDonationManager.generateLiveQrBitmap(payPalUrl, sizePx = 360)
    }

    if (engagement.isActiveSupporter) {
        Surface(
            color = Color(0xFF1F6E43).copy(alpha = 0.14f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1F6E43), RoundedCornerShape(14.dp))
                .testTag("verified_supporter_receipt_banner")
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF1F6E43),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Active Supporter Badge Unlocked ★",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F6E43)
                        )
                        Text(
                            text = "Method: ${engagement.supporterMethodLabel.ifBlank { "Supporter" }} • Total: $${String.format("%.2f", engagement.totalDonatedDollars)} • All donation banners permanently disabled.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                engagement.lastVerifiedReceipt?.let { receipt ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "✓ Verified Google Play Receipt: ${receipt.orderId}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F6E43)
                            )
                            Text(
                                text = "SKU: ${receipt.skuId} (${receipt.formattedPrice}) • Acknowledged: ${if (receipt.isAcknowledged) "YES" else "PENDING"} • Session: ${receipt.activitySessionName} • SHA-256: ${receipt.verificationHash}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // CHANNEL 1: Google Play In-App Purchase
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.gplay_channel_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${engagement.billingUiState.connectionState.displayLabel} • Activity Session: $resolvedSessionLabel ✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (engagement.billingUiState.statusDiagnosticMessage.isNotBlank()) {
                Text(
                    text = engagement.billingUiState.statusDiagnosticMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            GooglePlayDonationTier.entries.chunked(2).forEach { rowTiers ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTiers.forEach { tier ->
                        val resolvedPrice = engagement.billingUiState.productOffers[tier.skuId]?.formattedPrice
                            ?: tier.priceFormatted
                        Button(
                            onClick = { onGooglePlayDonate(tier) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gplay_tier_${tier.name.lowercase()}")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${tier.badgeEmoji} $resolvedPrice",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tier.title.substringAfter("• ").trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // CHANNEL 2: PayPal & Live QR Code
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "2. PayPal & Live QR Code",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Presets ($3, $5, $10, Any) • Live camera scanner QR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = { showPayPalQrCode = !showPayPalQrCode }) {
                    Text(if (showPayPalQrCode) "Hide QR" else "Show QR", style = MaterialTheme.typography.labelSmall)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(3, 5, 10).forEach { amt ->
                    FilterChip(
                        selected = !useCustomPayPalAmount && selectedPayPalPreset == amt,
                        onClick = {
                            useCustomPayPalAmount = false
                            selectedPayPalPreset = amt
                        },
                        label = { Text("$$amt USD", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("paypal_preset_$amt")
                    )
                }
                FilterChip(
                    selected = useCustomPayPalAmount,
                    onClick = { useCustomPayPalAmount = true },
                    label = { Text("Any Amount", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("paypal_preset_custom")
                )
            }

            if (useCustomPayPalAmount) {
                OutlinedTextField(
                    value = customPayPalAmountText,
                    onValueChange = { customPayPalAmountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Custom Amount (USD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(visible = showPayPalQrCode) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "PayPal Live QR Code for $payPalUrl",
                            modifier = Modifier
                                .size(148.dp)
                                .padding(8.dp)
                                .testTag("paypal_live_qr_image")
                        )
                    }
                    Text(
                        text = payPalUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onPayPalDonate(activePayPalAmount) },
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("paypal_open_button")
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PayPal ($$activePayPalAmount)", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        AppEngagementAndDonationManager.copyToClipboard(context, "PayPal Link", payPalUrl)
                    },
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("paypal_copy_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        val activity = context.findActivity()
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Support PantryPulse via PayPal: $payPalUrl")
                            }
                            (activity ?: context).startActivity(Intent.createChooser(shareIntent, "Share PayPal Link"))
                        } catch (_: Exception) {
                            AppEngagementAndDonationManager.copyToClipboard(context, "PayPal Link", payPalUrl)
                        }
                    },
                    modifier = Modifier.weight(0.85f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // CHANNEL 3: Web Platform (Ko-fi)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalCafe,
                    contentDescription = null,
                    tint = Color(0xFFE05A36),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "3. Ko-fi Tip Jar (Web Platform)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Direct browser link with automatic clipboard backup",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onKofiDonate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE05A36),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("kofi_open_button")
                ) {
                    Icon(Icons.Default.LocalCafe, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Support on Ko-fi ☕", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        AppEngagementAndDonationManager.copyToClipboard(
                            context,
                            "Ko-fi Link",
                            AppEngagementAndDonationManager.KOFI_URL
                        )
                    },
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("kofi_copy_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Link", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
