package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.KitchenSetupState
import com.example.data.PantryItemEntity
import com.example.data.WeeklyMealPlanner
import com.example.ui.theme.FreshEmerald

/**
 * Weekly Zero-Waste Meal Plan (3, 5, or 7 days) built deterministically from expiring fridge items
 * with 1-tap Smart Shopping List syncing for minimal missing staples.
 */
@Composable
fun WeeklyMealPlanCard(
    pantryItems: List<PantryItemEntity>,
    kitchenSetup: KitchenSetupState,
    onSyncMissingStaplesToShopping: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedDays by rememberSaveable { mutableIntStateOf(5) }

    val plan = remember(pantryItems, kitchenSetup, selectedDays) {
        WeeklyMealPlanner.buildZeroWastePlan(
            pantryItems = pantryItems,
            kitchenSetup = kitchenSetup,
            daysCount = selectedDays
        )
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weekly_meal_plan_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("toggle_weekly_meal_plan_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.weekly_plan_title_fmt, selectedDays),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                id = R.string.weekly_plan_subtitle_fmt,
                                plan.totalAtRiskDollarsRescued,
                                plan.consolidatedMissingStaples.size
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(id = R.string.cd_expand_weekly_plan)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 7).forEach { count ->
                            FilterChip(
                                selected = selectedDays == count,
                                onClick = { selectedDays = count },
                                label = { Text(stringResource(id = R.string.weekly_plan_days_chip_fmt, count)) },
                                modifier = Modifier.testTag("weekly_plan_days_$count")
                            )
                        }
                    }

                    if (plan.days.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(id = R.string.weekly_plan_empty_pantry_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    plan.days.forEach { day ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = day.dayLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(
                                            id = R.string.weekly_plan_prep_appliance_fmt,
                                            day.prepMinutes,
                                            day.appliance
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = day.mealTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = day.urgencySummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FreshEmerald
                                )
                                if (day.rescuedIngredients.isNotEmpty()) {
                                    Text(
                                        text = stringResource(
                                            id = R.string.weekly_plan_uses_fmt,
                                            day.rescuedIngredients.joinToString(", ")
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (plan.consolidatedMissingStaples.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = { onSyncMissingStaplesToShopping(plan.consolidatedMissingStaples) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_weekly_plan_shopping_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    id = R.string.weekly_plan_sync_shopping_fmt,
                                    plan.consolidatedMissingStaples.size
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
