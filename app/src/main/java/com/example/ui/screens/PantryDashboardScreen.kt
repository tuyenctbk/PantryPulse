package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.FreshnessStatus
import com.example.data.KitchenSetupState
import com.example.data.PantryCategory
import com.example.data.PantryItemEntity
import com.example.data.RestockItemEntity
import com.example.ui.PantrySortOrder
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.CriticalCrimsonContainer
import com.example.ui.theme.ExpiringAmber
import com.example.ui.theme.ExpiringAmberContainer
import com.example.ui.theme.FreshEmerald
import com.example.ui.theme.FreshEmeraldContainer
import kotlin.math.roundToInt

@Composable
fun PantryDashboardScreen(
    items: List<PantryItemEntity>,
    restockItems: List<RestockItemEntity>,
    selectedCategory: PantryCategory?,
    searchQuery: String,
    sortOrder: PantrySortOrder,
    kitchenSetup: KitchenSetupState,
    onSelectCategory: (PantryCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (PantrySortOrder) -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToManualInput: () -> Unit,
    onNavigateToRecipes: () -> Unit,
    onAddManualItem: (String, PantryCategory, String, Int, String) -> Unit,
    onUpdateItem: (PantryItemEntity, String, PantryCategory, String, Int, String) -> Unit,
    onAdjustDays: (PantryItemEntity, Int) -> Unit,
    onMarkUsedUp: (PantryItemEntity) -> Unit,
    onDeleteItem: (PantryItemEntity) -> Unit,
    onRestockToFridge: (RestockItemEntity) -> Unit,
    onDismissRestock: (RestockItemEntity) -> Unit,
    onPinOrRefreshWidget: () -> Unit
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<PantryItemEntity?>(null) }

    val filteredItems = remember(items, selectedCategory, searchQuery, sortOrder) {
        val byCat = if (selectedCategory == null) items else items.filter { it.parsedCategory == selectedCategory }
        val bySearch = if (searchQuery.isBlank()) {
            byCat
        } else {
            val q = searchQuery.trim()
            byCat.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.visualConditionNote.contains(q, ignoreCase = true) ||
                    it.parsedCategory.displayName.contains(q, ignoreCase = true)
            }
        }
        when (sortOrder) {
            PantrySortOrder.URGENCY -> bySearch.sortedWith(
                compareBy<PantryItemEntity> { it.daysUntilExpiry }.thenByDescending { it.estimatedValueDollars }
            )
            PantrySortOrder.VALUE_HIGH -> bySearch.sortedByDescending { it.estimatedValueDollars }
            PantrySortOrder.RECENT -> bySearch.sortedByDescending { it.addedTimestamp }
        }
    }

    val expiringItems = remember(items) { items.filter { it.daysUntilExpiry <= 3 } }
    val criticalCount = remember(items) { items.count { it.daysUntilExpiry <= 1 } }
    val soonCount = remember(items) { items.count { it.daysUntilExpiry in 2..3 } }
    val freshCount = remember(items) { items.count { it.daysUntilExpiry > 3 } }
    val atRiskDollars = remember(expiringItems) { expiringItems.sumOf { it.estimatedValueDollars } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pantry_dashboard_list"),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                HeroPantryBanner(
                    expiringCount = expiringItems.size,
                    criticalCount = criticalCount,
                    rescuedMeals = kitchenSetup.rescuedMealsCount,
                    savedDollars = kitchenSetup.savedDollarsTotal,
                    onSnapFridge = onNavigateToScan,
                    onPlanMeals = onNavigateToRecipes
                )
            }

            if (items.isNotEmpty()) {
                item {
                    FreshnessPulseBreakdownCard(
                        criticalCount = criticalCount,
                        soonCount = soonCount,
                        freshCount = freshCount,
                        atRiskDollars = atRiskDollars,
                        onRescueNow = onNavigateToRecipes
                    )
                }
            }

            item {
                ExpiringWidgetPreviewCard(
                    expiringItems = expiringItems,
                    onPinOrRefreshWidget = onPinOrRefreshWidget
                )
            }

            if (restockItems.isNotEmpty()) {
                item {
                    SmartRestockCard(
                        restockItems = restockItems,
                        onRestockToFridge = onRestockToFridge,
                        onDismissRestock = onDismissRestock
                    )
                }
            }

            item {
                SearchAndSortSection(
                    searchQuery = searchQuery,
                    sortOrder = sortOrder,
                    onSearchQueryChange = onSearchQueryChange,
                    onSortOrderChange = onSortOrderChange
                )
            }

            item {
                CategoryFilterRow(
                    selectedCategory = selectedCategory,
                    totalCount = items.size,
                    onSelectCategory = onSelectCategory
                )
            }

            if (filteredItems.isEmpty()) {
                item {
                    EmptyPantryCard(
                        hasSearchFilter = searchQuery.isNotBlank() || selectedCategory != null,
                        onClearFilters = {
                            onSearchQueryChange("")
                            onSelectCategory(null)
                        },
                        onSnapFridge = onNavigateToScan,
                        onAddManual = onNavigateToManualInput
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    PantryIngredientCard(
                        item = item,
                        onAdjustDays = { delta -> onAdjustDays(item, delta) },
                        onEdit = { editingItem = item },
                        onMarkUsedUp = { onMarkUsedUp(item) },
                        onDelete = { onDeleteItem(item) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNavigateToManualInput,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_ingredient_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.fab_manual_add))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = R.string.fab_manual_add), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showAddDialog) {
        IngredientFormDialog(
            title = stringResource(id = R.string.dialog_add_item_title),
            confirmLabel = stringResource(id = R.string.dialog_save_to_pantry),
            initialName = "",
            initialQuantity = "1 pack",
            initialCategory = PantryCategory.PRODUCE,
            initialDays = 2,
            initialNote = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, category, qty, days, note ->
                onAddManualItem(name, category, qty, days, note)
                showAddDialog = false
            }
        )
    }

    editingItem?.let { target ->
        IngredientFormDialog(
            title = stringResource(id = R.string.dialog_edit_item_title),
            confirmLabel = stringResource(id = R.string.dialog_update_item),
            initialName = target.name,
            initialQuantity = target.quantity,
            initialCategory = target.parsedCategory,
            initialDays = target.daysUntilExpiry,
            initialNote = target.visualConditionNote,
            onDismiss = { editingItem = null },
            onConfirm = { name, category, qty, days, note ->
                onUpdateItem(target, name, category, qty, days, note)
                editingItem = null
            }
        )
    }
}

@Composable
private fun SmartRestockCard(
    restockItems: List<RestockItemEntity>,
    onRestockToFridge: (RestockItemEntity) -> Unit,
    onDismissRestock: (RestockItemEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("smart_restock_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = CircleShape,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.restock_card_title, restockItems.size),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = R.string.restock_card_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(id = R.string.restock_btn_hide) else stringResource(id = R.string.restock_btn_view))
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    restockItems.forEach { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.parsedCategory.emoji} ${item.name} (${item.suggestedQuantity})",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Tip: ${item.zeroWasteBuyingTip}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDismissRestock(item) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss restock item",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Button(
                                    onClick = { onRestockToFridge(item) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("restock_item_${item.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(id = R.string.restock_btn_action), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreshnessPulseBreakdownCard(
    criticalCount: Int,
    soonCount: Int,
    freshCount: Int,
    atRiskDollars: Double,
    onRescueNow: () -> Unit
) {
    val total = (criticalCount + soonCount + freshCount).coerceAtLeast(1)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.pulse_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.pulse_at_risk_subtitle, atRiskDollars),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (atRiskDollars > 0) CriticalCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (criticalCount + soonCount > 0) {
                    TextButton(
                        onClick = onRescueNow,
                        modifier = Modifier.testTag("pulse_rescue_now_button")
                    ) {
                        Text(stringResource(id = R.string.pulse_cook_expiring_cta), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (criticalCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(criticalCount.toFloat() / total)
                            .fillMaxHeight()
                            .background(CriticalCrimson)
                    )
                }
                if (soonCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(soonCount.toFloat() / total)
                            .fillMaxHeight()
                            .background(ExpiringAmber)
                    )
                }
                if (freshCount > 0) {
                    Box(
                        modifier = Modifier
                            .weight(freshCount.toFloat() / total)
                            .fillMaxHeight()
                            .background(FreshEmerald)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PulseLegendDot(color = CriticalCrimson, label = stringResource(id = R.string.pulse_legend_critical, criticalCount))
                PulseLegendDot(color = ExpiringAmber, label = stringResource(id = R.string.pulse_legend_soon, soonCount))
                PulseLegendDot(color = FreshEmerald, label = stringResource(id = R.string.pulse_legend_fresh, freshCount))
            }
        }
    }
}

@Composable
private fun PulseLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchAndSortSection(
    searchQuery: String,
    sortOrder: PantrySortOrder,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (PantrySortOrder) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(id = R.string.search_pantry_placeholder)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search Pantry")
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pantry_search_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(id = R.string.sort_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PantrySortOrder.entries.forEach { order ->
                FilterChip(
                    selected = sortOrder == order,
                    onClick = { onSortOrderChange(order) },
                    label = { Text(order.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("sort_chip_${order.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun HeroPantryBanner(
    expiringCount: Int,
    criticalCount: Int,
    rescuedMeals: Int,
    savedDollars: Double,
    onSnapFridge: () -> Unit,
    onPlanMeals: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_pantry),
                contentDescription = "Open fridge shelf filled with fresh produce and jars",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(245.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(245.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x990D2116),
                                Color(0xE60D2116),
                                Color(0xF50B1C12)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0x33FFFFFF),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = stringResource(id = R.string.hero_offline_active),
                                tint = Color(0xFFA6F2CA),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.hero_offline_active),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }

                    Surface(
                        color = if (criticalCount > 0) Color(0xFFE05A36) else Color(0xFF237A4B),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (expiringCount > 0) {
                                stringResource(id = R.string.hero_expiring_soon_badge, expiringCount)
                            } else {
                                stringResource(id = R.string.hero_all_fresh_badge)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(id = R.string.hero_headline),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.hero_impact_stats, rescuedMeals, savedDollars),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD2E8DC)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSnapFridge,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE05A36),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hero_snap_fridge_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = stringResource(id = R.string.hero_btn_fridge_vision),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.hero_btn_fridge_vision), maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onPlanMeals,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hero_plan_meals_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestaurantMenu,
                            contentDescription = stringResource(id = R.string.hero_btn_rescue_meals),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.hero_btn_rescue_meals), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiringWidgetPreviewCard(
    expiringItems: List<PantryItemEntity>,
    onPinOrRefreshWidget: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = stringResource(id = R.string.widget_card_title),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.widget_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (expiringItems.isEmpty()) {
                                stringResource(id = R.string.widget_card_all_fresh)
                            } else {
                                stringResource(id = R.string.widget_card_expiring_count, expiringItems.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = onPinOrRefreshWidget,
                    modifier = Modifier.testTag("pin_widget_button")
                ) {
                    Text(stringResource(id = R.string.widget_card_pin_sync))
                }
            }

            if (expiringItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    expiringItems.take(4).forEach { item ->
                        val isCritical = item.daysUntilExpiry <= 1
                        Surface(
                            color = if (isCritical) CriticalCrimsonContainer else ExpiringAmberContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.parsedCategory.emoji} ${item.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF28120B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isCritical) CriticalCrimson else ExpiringAmber,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${item.daysUntilExpiry}d",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: PantryCategory?,
    totalCount: Int,
    onSelectCategory: (PantryCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onSelectCategory(null) },
            label = { Text(stringResource(id = R.string.filter_all_count, totalCount)) },
            modifier = Modifier.testTag("filter_all_chip")
        )
        PantryCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = {
                    onSelectCategory(if (selectedCategory == category) null else category)
                },
                label = { Text("${category.emoji} ${category.displayName}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun PantryIngredientCard(
    item: PantryItemEntity,
    onAdjustDays: (Int) -> Unit,
    onEdit: () -> Unit,
    onMarkUsedUp: () -> Unit,
    onDelete: () -> Unit
) {
    val status = item.freshnessStatus
    val (badgeBg, badgeFg) = when (status) {
        FreshnessStatus.CRITICAL -> CriticalCrimsonContainer to CriticalCrimson
        FreshnessStatus.EXPIRING_SOON -> ExpiringAmberContainer to ExpiringAmber
        FreshnessStatus.FRESH -> FreshEmeraldContainer to FreshEmerald
    }

    var expandedTip by rememberSaveable(item.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { expandedTip = !expandedTip }
            .testTag("pantry_item_card_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = item.parsedCategory.emoji,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.quantity} • Est. $${String.format("%.2f", item.estimatedValueDollars)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Expiry Countdown",
                            tint = badgeFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                item.daysUntilExpiry <= 0 -> stringResource(id = R.string.expiry_use_today)
                                item.daysUntilExpiry == 1 -> stringResource(id = R.string.expiry_one_day)
                                else -> stringResource(id = R.string.expiry_many_days, item.daysUntilExpiry)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg
                        )
                    }
                }
            }

            if (item.visualConditionNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.visualConditionNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expandedTip && item.storageTip.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.freshness_tip_prefix, item.storageTip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onAdjustDays(-1) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("decrease_days_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease expiry days",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${item.daysUntilExpiry}d",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { onAdjustDays(1) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("increase_days_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase expiry days",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove item",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    Button(
                        onClick = onMarkUsedUp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("consume_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(id = R.string.btn_used_up),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.btn_used_up), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPantryCard(
    hasSearchFilter: Boolean,
    onClearFilters: () -> Unit,
    onSnapFridge: () -> Unit,
    onAddManual: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Kitchen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasSearchFilter) {
                    stringResource(id = R.string.empty_pantry_filtered_title)
                } else {
                    stringResource(id = R.string.empty_pantry_default_title)
                },
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasSearchFilter) {
                    stringResource(id = R.string.empty_pantry_filtered_body)
                } else {
                    stringResource(id = R.string.empty_pantry_default_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasSearchFilter) {
                    Button(onClick = onClearFilters) {
                        Text(stringResource(id = R.string.btn_reset_filters))
                    }
                } else {
                    Button(onClick = onSnapFridge) {
                        Text(stringResource(id = R.string.btn_open_fridge_vision))
                    }
                    OutlinedButton(onClick = onAddManual) {
                        Text(stringResource(id = R.string.btn_add_manually))
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialQuantity: String,
    initialCategory: PantryCategory,
    initialDays: Int,
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (String, PantryCategory, String, Int, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var quantity by rememberSaveable { mutableStateOf(initialQuantity) }
    var selectedCategory by rememberSaveable { mutableStateOf(initialCategory) }
    var daysSlider by rememberSaveable { mutableFloatStateOf(initialDays.coerceIn(1, 21).toFloat()) }
    var note by rememberSaveable { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.custom_name_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_item_name_input")
                )

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(id = R.string.custom_quantity_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_item_quantity_input")
                )

                Text(
                    text = stringResource(id = R.string.custom_category_label),
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PantryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.emoji} ${cat.displayName}") }
                        )
                    }
                }

                val daysInt = daysSlider.roundToInt().coerceIn(1, 21)
                Text(
                    text = if (daysInt == 1) {
                        stringResource(id = R.string.expiry_one_day)
                    } else {
                        stringResource(id = R.string.expiry_many_days, daysInt)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (daysInt <= 2) CriticalCrimson else MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = daysSlider,
                    onValueChange = { daysSlider = it },
                    valueRange = 1f..21f,
                    steps = 19
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(id = R.string.custom_note_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name,
                            selectedCategory,
                            quantity,
                            daysSlider.roundToInt().coerceIn(1, 21),
                            note
                        )
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_ingredient_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_cancel))
            }
        }
    )
}
