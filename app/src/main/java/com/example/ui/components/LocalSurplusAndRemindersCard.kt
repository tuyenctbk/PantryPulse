package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.notifications.DailyExpiryReminderScheduler
import kotlinx.coroutines.launch

/**
 * Local Morning Expiry Alerts card (0 cloud cost, Play-Policy compliant runtime POST_NOTIFICATIONS).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailyExpiryAlertsCard(
    expiringCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { DailyExpiryReminderScheduler.getSettings(context) }
    var alertsEnabled by remember { mutableStateOf(initial.enabled) }
    var reminderHour by remember { mutableIntStateOf(initial.reminderHour) }
    var pendingActionAfterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingActionAfterGrant?.invoke()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.expiry_alert_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
        pendingActionAfterGrant = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingActionAfterGrant = action
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        action()
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("daily_expiry_alerts_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.expiry_alert_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                id = R.string.expiry_alert_card_subtitle_fmt,
                                expiringCount,
                                formatHourLabel(reminderHour)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = alertsEnabled,
                    onCheckedChange = { wantsEnabled ->
                        if (wantsEnabled) {
                            runWithNotificationPermission {
                                DailyExpiryReminderScheduler.setDailyReminderEnabled(context, true, reminderHour)
                                alertsEnabled = true
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.expiry_alert_enabled_toast_fmt, formatHourLabel(reminderHour)),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            DailyExpiryReminderScheduler.setDailyReminderEnabled(context, false, reminderHour)
                            alertsEnabled = false
                        }
                    },
                    modifier = Modifier.testTag("daily_expiry_alerts_switch")
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(8, 9, 18).forEach { hr ->
                    FilterChip(
                        selected = reminderHour == hr,
                        onClick = {
                            reminderHour = hr
                            if (alertsEnabled) {
                                DailyExpiryReminderScheduler.setDailyReminderEnabled(context, true, hr)
                            }
                        },
                        label = { Text(formatHourLabel(hr)) },
                        modifier = Modifier.testTag("expiry_alert_hour_$hr")
                    )
                }
                OutlinedButton(
                    onClick = {
                        runWithNotificationPermission {
                            scope.launch {
                                val posted = DailyExpiryReminderScheduler.postImmediateExpirySummaryNotification(context)
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (posted) R.string.expiry_alert_test_sent else R.string.expiry_alert_permission_denied
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.testTag("send_test_expiry_alert_button")
                ) {
                    Text(stringResource(id = R.string.expiry_alert_test_btn))
                }
            }
        }
    }
}

/**
 * Zero-cost Google Maps geo: deep-link finder for nearby community fridges, food banks,
 * and surplus farmers markets (0 Maps SDK billing, 0 location permission needed).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalSurplusMapsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("local_surplus_maps_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.surplus_maps_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(id = R.string.surplus_maps_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { launchZeroCostMapsSearch(context, "community fridge near me") },
                    label = { Text(stringResource(id = R.string.surplus_maps_chip_fridge)) },
                    leadingIcon = {
                        Icon(Icons.Default.VolunteerActivism, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("maps_find_community_fridge_chip")
                )
                AssistChip(
                    onClick = { launchZeroCostMapsSearch(context, "food bank donation near me") },
                    label = { Text(stringResource(id = R.string.surplus_maps_chip_food_bank)) },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("maps_find_food_bank_chip")
                )
                AssistChip(
                    onClick = { launchZeroCostMapsSearch(context, "farmers market produce near me") },
                    label = { Text(stringResource(id = R.string.surplus_maps_chip_markets)) },
                    leadingIcon = {
                        Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("maps_find_farmers_market_chip")
                )
            }
        }
    }
}

private fun formatHourLabel(hour24: Int): String = when {
    hour24 == 0 -> "12 AM"
    hour24 < 12 -> "$hour24 AM"
    hour24 == 12 -> "12 PM"
    else -> "${hour24 - 12} PM"
}

private fun launchZeroCostMapsSearch(context: Context, query: String) {
    val encoded = Uri.encode(query)
    val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(geoIntent)
    } catch (_: Exception) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(webIntent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.surplus_maps_no_app_toast),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
