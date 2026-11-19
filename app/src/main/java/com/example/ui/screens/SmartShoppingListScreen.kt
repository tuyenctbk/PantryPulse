package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.KitchenSetupState
import com.example.data.PantryCategory
import com.example.data.PantryItemEntity
import com.example.data.RestockItemEntity
import java.util.Locale
import kotlin.math.roundToInt

enum class ShoppingListFilter {
    ALL,
    AUTO_LOW_STOCK,
    MANUAL,
    TO_BUY,
    IN_CART
}

@Composable
private fun ShoppingListFilter.localizedLabel(): String = when (this) {
    ShoppingListFilter.ALL -> stringResource(id = R.string.shopping_filter_all)
    ShoppingListFilter.AUTO_LOW_STOCK -> stringResource(id = R.string.shopping_filter_auto_low)
    ShoppingListFilter.MANUAL -> stringResource(id = R.string.shopping_filter_manual)
    ShoppingListFilter.TO_BUY -> stringResource(id = R.string.shopping_filter_to_buy)
    ShoppingListFilter.IN_CART -> stringResource(id = R.string.shopping_filter_in_cart)
}

enum class ShoppingManagerTab {
    SHOPPING_LIST,
    LOW_SUPPLY_INVENTORY
}

private data class KitchenStapleSuggestion(
    @androidx.annotation.StringRes val nameRes: Int,
    val tagKey: String,
    val emoji: String,
    val category: PantryCategory,
    @androidx.annotation.StringRes val defaultQtyRes: Int,
    val defaultPrice: Double
)

private val KITCHEN_STAPLE_SUGGESTIONS = listOf(
    KitchenStapleSuggestion(R.string.staple_whole_milk, "whole_milk", "🥛", PantryCategory.DAIRY_CHEESE, R.string.qty_1_gal, 3.79),
    KitchenStapleSuggestion(R.string.staple_organic_eggs, "organic_eggs", "🥚", PantryCategory.PROTEIN, R.string.qty_1_dozen, 3.99),
    KitchenStapleSuggestion(R.string.staple_sourdough_bread, "sourdough_bread", "🍞", PantryCategory.PANTRY_STAPLE, R.string.qty_1_loaf, 4.29),
    KitchenStapleSuggestion(R.string.staple_butter, "butter", "🧈", PantryCategory.DAIRY_CHEESE, R.string.qty_1_lb, 3.99),
    KitchenStapleSuggestion(R.string.staple_olive_oil, "olive_oil", "🫒", PantryCategory.SAUCES_JARS, R.string.qty_500ml, 8.49),
    KitchenStapleSuggestion(R.string.staple_fresh_garlic, "fresh_garlic", "🧄", PantryCategory.PRODUCE, R.string.qty_1_head, 0.99),
    KitchenStapleSuggestion(R.string.staple_yellow_onions, "yellow_onions", "🧅", PantryCategory.PRODUCE, R.string.qty_3_lbs, 2.99),
    KitchenStapleSuggestion(R.string.staple_roma_tomatoes, "roma_tomatoes", "🍅", PantryCategory.PRODUCE, R.string.qty_1_lb, 1.99),
    KitchenStapleSuggestion(R.string.staple_cheddar_cheese, "cheddar_cheese", "🧀", PantryCategory.DAIRY_CHEESE, R.string.qty_8_oz, 3.49),
    KitchenStapleSuggestion(R.string.staple_coffee_beans, "coffee_beans", "☕", PantryCategory.PANTRY_STAPLE, R.string.qty_12_oz, 7.99),
    KitchenStapleSuggestion(R.string.staple_jasmine_rice, "jasmine_rice", "🍚", PantryCategory.PANTRY_STAPLE, R.string.qty_2_lbs, 3.29),
    KitchenStapleSuggestion(R.string.staple_chicken_breast, "chicken_breast", "🍗", PantryCategory.PROTEIN, R.string.qty_1_lb, 4.99)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartShoppingListScreen(
    shoppingItems: List<RestockItemEntity>,
    pantryItems: List<PantryItemEntity>,
    kitchenSetup: KitchenSetupState,
    onToggleItemChecked: (RestockItemEntity) -> Unit,
    onRestockSingleItemToPantry: (RestockItemEntity) -> Unit,
    onRestockAllCheckedToPantry: () -> Unit,
    onDeleteShoppingItem: (RestockItemEntity) -> Unit,
    onAddManualShoppingItem: (name: String, category: PantryCategory, quantity: String, estimatedCostUsd: Double, tip: String) -> Unit,
    onMoveLowSupplyItemToShoppingList: (PantryItemEntity) -> Unit,
    onMoveAllLowSupplyToShoppingList: () -> Unit,
    onUpdateLowStockThreshold: (Int) -> Unit,
    onRunLowStockScanNow: () -> Unit,
    onNavigateToPantry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedManagerTab by rememberSaveable { mutableStateOf(ShoppingManagerTab.SHOPPING_LIST) }
    var selectedFilter by rememberSaveable { mutableStateOf(ShoppingListFilter.ALL) }
    var showAddForm by rememberSaveable { mutableStateOf(false) }
    var showThresholdControls by rememberSaveable { mutableStateOf(true) }

    BackHandler(enabled = selectedManagerTab == ShoppingManagerTab.LOW_SUPPLY_INVENTORY) {
        selectedManagerTab = ShoppingManagerTab.SHOPPING_LIST
    }

    val defaultShoppingTip = stringResource(id = R.string.shopping_default_tip)
    val defaultQtyPack = stringResource(id = R.string.qty_1_pack)
    val defaultQtyUnit = stringResource(id = R.string.qty_1_unit)
    var newItemName by rememberSaveable { mutableStateOf("") }
    var newItemQuantity by rememberSaveable { mutableStateOf(defaultQtyPack) }
    var newItemPrice by rememberSaveable { mutableStateOf("3.49") }
    var newItemTip by rememberSaveable { mutableStateOf(defaultShoppingTip) }
    var newItemCategory by rememberSaveable { mutableStateOf(PantryCategory.PRODUCE) }

    var sliderThreshold by remember(kitchenSetup.lowStockThresholdPercent) {
        mutableFloatStateOf(kitchenSetup.lowStockThresholdPercent.toFloat())
    }

    val autoAddedCount = remember(shoppingItems) { shoppingItems.count { it.isAutoAdded } }
    val manualCount = remember(shoppingItems) { shoppingItems.count { !it.isAutoAdded } }
    val checkedCount = remember(shoppingItems) { shoppingItems.count { it.isChecked } }
    val totalEstimatedCost = remember(shoppingItems) { shoppingItems.sumOf { it.estimatedCostDollars } }
    val checkedEstimatedCost = remember(shoppingItems) {
        shoppingItems.filter { it.isChecked }.sumOf { it.estimatedCostDollars }
    }

    val lowStockPantryItems = remember(pantryItems, kitchenSetup.lowStockThresholdPercent) {
        pantryItems.filter { it.stockPercent <= kitchenSetup.lowStockThresholdPercent }
    }

    val unaddedLowStockItems = remember(lowStockPantryItems, shoppingItems) {
        lowStockPantryItems.filter { pantryItem ->
            shoppingItems.none { restock ->
                (restock.sourcePantryItemId != null && restock.sourcePantryItemId == pantryItem.id) ||
                    restock.name.equals(pantryItem.name, ignoreCase = true)
            }
        }
    }

    val filteredShoppingItems = remember(shoppingItems, selectedFilter) {
        when (selectedFilter) {
            ShoppingListFilter.ALL -> shoppingItems
            ShoppingListFilter.AUTO_LOW_STOCK -> shoppingItems.filter { it.isAutoAdded }
            ShoppingListFilter.MANUAL -> shoppingItems.filter { !it.isAutoAdded }
            ShoppingListFilter.TO_BUY -> shoppingItems.filter { !it.isChecked }
            ShoppingListFilter.IN_CART -> shoppingItems.filter { it.isChecked }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_shopping_list_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Summary Header & Manager Tabs
        item {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .fillMaxSize()
                                    )
                                }
                                Text(
                                    text = stringResource(id = R.string.shopping_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.shopping_manager_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    // Manager Tabs: Shopping List vs Low-Supply Inventory
                    TabRow(
                        selectedTabIndex = selectedManagerTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .testTag("shopping_manager_tab_row")
                    ) {
                        Tab(
                            selected = selectedManagerTab == ShoppingManagerTab.SHOPPING_LIST,
                            onClick = { selectedManagerTab = ShoppingManagerTab.SHOPPING_LIST },
                            text = {
                                Text(
                                    text = "${stringResource(id = R.string.shopping_tab_list)} (${shoppingItems.size})",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            icon = {
                                Icon(Icons.Default.ShoppingCart, contentDescription = stringResource(id = R.string.cd_shopping_list_tab), modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.testTag("shopping_tab_list")
                        )
                        Tab(
                            selected = selectedManagerTab == ShoppingManagerTab.LOW_SUPPLY_INVENTORY,
                            onClick = { selectedManagerTab = ShoppingManagerTab.LOW_SUPPLY_INVENTORY },
                            text = {
                                Text(
                                    text = stringResource(id = R.string.shopping_tab_low_supply_fmt, lowStockPantryItems.size),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            icon = {
                                Icon(Icons.Default.Inventory2, contentDescription = stringResource(id = R.string.cd_low_supply_tab), modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.testTag("shopping_tab_low_supply")
                        )
                    }

                    // Metrics Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShoppingMetricBadge(
                            label = stringResource(id = R.string.shopping_total_items),
                            value = "${shoppingItems.size}",
                            modifier = Modifier.weight(1f)
                        )
                        ShoppingMetricBadge(
                            label = stringResource(id = R.string.shopping_in_cart),
                            value = "$checkedCount/${shoppingItems.size}",
                            modifier = Modifier.weight(1f)
                        )
                        ShoppingMetricBadge(
                            label = stringResource(id = R.string.shopping_est_cost),
                            value = "$${String.format(Locale.US, "%.2f", totalEstimatedCost)}",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Top Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRestockAllCheckedToPantry,
                            enabled = checkedCount > 0,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("restock_checked_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCartCheckout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (checkedCount > 0) {
                                    stringResource(id = R.string.shopping_restock_checked_fmt, checkedCount, checkedEstimatedCost)
                                } else {
                                    stringResource(id = R.string.shopping_check_to_restock)
                                },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledTonalButton(
                            onClick = { showAddForm = !showAddForm },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_add_shopping_item_button")
                        ) {
                            Icon(
                                imageVector = if (showAddForm) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = stringResource(id = R.string.cd_add_missing_ingredient),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showAddForm) {
                                    stringResource(id = R.string.shopping_btn_close)
                                } else {
                                    stringResource(id = R.string.shopping_btn_add_missing)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Exportable Household Restock Share Card
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("household_restock_share_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.shopping_share_card_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(id = R.string.shopping_share_card_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    val checklistText = buildCategorizedHouseholdChecklist(
                                        context = context,
                                        shoppingItems = shoppingItems,
                                        thresholdPercent = kitchenSetup.lowStockThresholdPercent,
                                        totalEstimatedCost = totalEstimatedCost
                                    )
                                    val shareSubject = context.getString(R.string.checklist_share_subject)
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            shareSubject,
                                            checklistText
                                        )
                                    )
                                    try {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                                            putExtra(Intent.EXTRA_TEXT, checklistText)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(
                                                sendIntent,
                                                context.getString(R.string.shopping_household_share_chooser)
                                            )
                                        )
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.shopping_household_copied_toast),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("share_household_checklist_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(stringResource(id = R.string.shopping_btn_share_checklist), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // 2. Quick Low-Supply Alert Banner (When on Shopping List Tab and unadded low stock exists)
        if (selectedManagerTab == ShoppingManagerTab.SHOPPING_LIST && unaddedLowStockItems.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedManagerTab = ShoppingManagerTab.LOW_SUPPLY_INVENTORY }
                        .testTag("low_supply_quick_alert_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(id = R.string.shopping_quick_low_stock_banner, unaddedLowStockItems.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        FilledTonalButton(
                            onClick = { selectedManagerTab = ShoppingManagerTab.LOW_SUPPLY_INVENTORY },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("view_low_supply_inventory_button")
                        ) {
                            Text(stringResource(id = R.string.shopping_btn_view_low_supply), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 3. Dedicated "Add Missing Ingredient" Form (Expandable)
        item {
            AnimatedVisibility(visible = showAddForm) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_missing_ingredient_form")
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
                            Column {
                                Text(
                                    text = stringResource(id = R.string.shopping_add_form_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(id = R.string.shopping_add_missing_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            IconButton(onClick = { showAddForm = false }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.shopping_btn_close))
                            }
                        }

                        // Quick Staples suggestion chips
                        Text(
                            text = stringResource(id = R.string.shopping_quick_staples_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            KITCHEN_STAPLE_SUGGESTIONS.forEach { staple ->
                                val localizedName = stringResource(id = staple.nameRes)
                                val localizedQty = stringResource(id = staple.defaultQtyRes)
                                AssistChip(
                                    onClick = {
                                        newItemName = localizedName
                                        newItemCategory = staple.category
                                        newItemQuantity = localizedQty
                                        newItemPrice = String.format(Locale.US, "%.2f", staple.defaultPrice)
                                    },
                                    label = { Text("${staple.emoji} $localizedName") },
                                    modifier = Modifier.testTag("staple_chip_${staple.tagKey}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newItemName,
                                onValueChange = { newItemName = it },
                                label = { Text(stringResource(id = R.string.shopping_input_name_label)) },
                                placeholder = { Text(stringResource(id = R.string.shopping_input_name_placeholder)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("shopping_add_name_input")
                            )
                            OutlinedTextField(
                                value = newItemQuantity,
                                onValueChange = { newItemQuantity = it },
                                label = { Text(stringResource(id = R.string.shopping_input_qty_label)) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("shopping_add_qty_input")
                            )
                            OutlinedTextField(
                                value = newItemPrice,
                                onValueChange = { newItemPrice = it },
                                label = { Text(stringResource(id = R.string.shopping_input_price_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(0.75f)
                                    .testTag("shopping_add_price_input")
                            )
                        }

                        // Category Selection Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PantryCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = newItemCategory == category,
                                    onClick = { newItemCategory = category },
                                    label = { Text("${category.emoji} ${stringResource(id = category.displayNameRes)}") },
                                    modifier = Modifier.testTag("shopping_cat_${category.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val trimmed = newItemName.trim()
                                    if (trimmed.isNotEmpty()) {
                                        val price = newItemPrice.toDoubleOrNull()?.coerceAtLeast(0.5) ?: 3.49
                                        onAddManualShoppingItem(
                                            trimmed,
                                            newItemCategory,
                                            newItemQuantity.ifBlank { defaultQtyUnit },
                                            price,
                                            newItemTip
                                        )
                                        newItemName = ""
                                        showAddForm = false
                                    }
                                },
                                enabled = newItemName.isNotBlank(),
                                modifier = Modifier.testTag("shopping_confirm_add_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(id = R.string.shopping_btn_confirm_add), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // TAB 1: SHOPPING LIST VIEW
        // ==========================================
        if (selectedManagerTab == ShoppingManagerTab.SHOPPING_LIST) {
            // Filter Chips
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ShoppingListFilter.entries.forEach { filter ->
                        val count = when (filter) {
                            ShoppingListFilter.ALL -> shoppingItems.size
                            ShoppingListFilter.AUTO_LOW_STOCK -> autoAddedCount
                            ShoppingListFilter.MANUAL -> manualCount
                            ShoppingListFilter.TO_BUY -> shoppingItems.count { !it.isChecked }
                            ShoppingListFilter.IN_CART -> checkedCount
                        }
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    stringResource(
                                        id = R.string.shopping_filter_count_fmt,
                                        filter.localizedLabel(),
                                        count
                                    )
                                )
                            },
                            modifier = Modifier.testTag("shopping_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            // Shopping Items or Empty State
            if (filteredShoppingItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.shopping_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(id = R.string.shopping_empty_body_fmt, kitchenSetup.lowStockThresholdPercent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { showAddForm = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(id = R.string.shopping_btn_add_missing))
                                }
                                OutlinedButton(onClick = { selectedManagerTab = ShoppingManagerTab.LOW_SUPPLY_INVENTORY }) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(id = R.string.shopping_btn_view_low_supply))
                                }
                            }
                        }
                    }
                }
            } else {
                items(filteredShoppingItems, key = { it.id }) { item ->
                    SmartShoppingItemCard(
                        item = item,
                        modifier = Modifier.animateItem(),
                        onToggleChecked = { onToggleItemChecked(item) },
                        onRestockToPantry = { onRestockSingleItemToPantry(item) },
                        onDelete = { onDeleteShoppingItem(item) }
                    )
                }
            }
        }

        // ==========================================
        // TAB 2: LOW-SUPPLY INVENTORY VIEW (MOVE TO LIST)
        // ==========================================
        if (selectedManagerTab == ShoppingManagerTab.LOW_SUPPLY_INVENTORY) {
            // Low-Stock Threshold & Controls Card
            item {
                OutlinedCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showThresholdControls = !showThresholdControls },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.shopping_low_supply_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(id = R.string.shopping_low_supply_desc, kitchenSetup.lowStockThresholdPercent),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            AssistChip(
                                onClick = onRunLowStockScanNow,
                                label = { Text(stringResource(id = R.string.shopping_sync_low_stock)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("scan_low_stock_button")
                            )
                        }

                        AnimatedVisibility(visible = showThresholdControls) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(id = R.string.shopping_threshold_rule_fmt, sliderThreshold.roundToInt()),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Slider(
                                    value = sliderThreshold,
                                    onValueChange = { sliderThreshold = it },
                                    onValueChangeFinished = {
                                        val rounded = ((sliderThreshold / 5f).roundToInt() * 5).coerceIn(10, 80)
                                        sliderThreshold = rounded.toFloat()
                                        onUpdateLowStockThreshold(rounded)
                                    },
                                    valueRange = 10f..80f,
                                    steps = 13,
                                    modifier = Modifier.testTag("low_stock_threshold_slider")
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(20, 30, 40, 50, 60).forEach { preset ->
                                        FilterChip(
                                            selected = kitchenSetup.lowStockThresholdPercent == preset,
                                            onClick = {
                                                sliderThreshold = preset.toFloat()
                                                onUpdateLowStockThreshold(preset)
                                            },
                                            label = { Text(stringResource(id = R.string.shopping_preset_stock_fmt, preset)) },
                                            modifier = Modifier.testTag("threshold_preset_$preset")
                                        )
                                    }
                                }

                                if (unaddedLowStockItems.isNotEmpty()) {
                                    Button(
                                        onClick = onMoveAllLowSupplyToShoppingList,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("move_all_low_supply_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.shopping_btn_move_all_low_supply, unaddedLowStockItems.size),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Low-Supply Items List or Empty State
            if (lowStockPantryItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.shopping_low_supply_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(id = R.string.shopping_low_supply_empty_desc, kitchenSetup.lowStockThresholdPercent),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(lowStockPantryItems, key = { it.id }) { pantryItem ->
                    val isAlreadyInList = shoppingItems.any { restock ->
                        (restock.sourcePantryItemId != null && restock.sourcePantryItemId == pantryItem.id) ||
                            restock.name.equals(pantryItem.name, ignoreCase = true)
                    }

                    LowSupplyInventoryCard(
                        item = pantryItem,
                        isAlreadyInShoppingList = isAlreadyInList,
                        modifier = Modifier.animateItem(),
                        onMoveToList = { onMoveLowSupplyItemToShoppingList(pantryItem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShoppingMetricBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LowSupplyInventoryCard(
    item: PantryItemEntity,
    isAlreadyInShoppingList: Boolean,
    modifier: Modifier = Modifier,
    onMoveToList: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (item.stockPercent / 100f).coerceIn(0.05f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "low_supply_stock_progress"
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlreadyInShoppingList) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isAlreadyInShoppingList) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("low_supply_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.parsedCategory.emoji} ${item.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = item.quantity,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = item.formattedExpiryDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isAlreadyInShoppingList) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.shopping_already_in_list),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onMoveToList,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("move_low_supply_button_${item.id}")
                    ) {
                        Icon(
                            Icons.Default.AddShoppingCart,
                            contentDescription = stringResource(id = R.string.cd_move_to_shopping_list),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.shopping_btn_move_to_list),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Stock percentage bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = if (item.stockPercent <= 25) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Text(
                    text = stringResource(id = R.string.shopping_stock_remaining_fmt, item.stockPercent),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item.stockPercent <= 25) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SmartShoppingItemCard(
    item: RestockItemEntity,
    modifier: Modifier = Modifier,
    onToggleChecked: () -> Unit,
    onRestockToPantry: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isChecked) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isChecked) 0.dp else 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("shopping_item_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onToggleChecked() },
                    modifier = Modifier.testTag("shopping_item_checkbox_${item.id}")
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${item.parsedCategory.emoji} ${item.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = item.suggestedQuantity,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.isAutoAdded) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (item.isAutoAdded) "⚡ ${item.triggerReason}" else item.triggerReason,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (item.isAutoAdded) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", item.estimatedCostDollars)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(id = R.string.shopping_est_abbr),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.zeroWasteBuyingTip.isNotBlank()) {
                Text(
                    text = stringResource(id = R.string.shopping_buying_tip_fmt, item.zeroWasteBuyingTip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onRestockToPantry,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("shopping_restock_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCartCheckout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(id = R.string.shopping_buy_restock_100_btn),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("shopping_delete_item_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(id = R.string.cd_remove_from_shopping),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildCategorizedHouseholdChecklist(
    context: android.content.Context,
    shoppingItems: List<RestockItemEntity>,
    thresholdPercent: Int,
    totalEstimatedCost: Double
): String {
    val autoTagStr = " ${context.getString(R.string.checklist_export_auto_tag)}"
    return buildString {
        appendLine(context.getString(R.string.checklist_export_header))
        appendLine(context.getString(R.string.checklist_export_rule_fmt, thresholdPercent))
        appendLine("========================================")
        val grouped = shoppingItems.groupBy { it.parsedCategory }
        grouped.forEach { (category, catItems) ->
            val catName = context.getString(category.displayNameRes).uppercase(Locale.getDefault())
            appendLine("\n${category.emoji} $catName (${catItems.size})")
            catItems.forEach { item ->
                val box = if (item.isChecked) "[✓]" else "[ ]"
                val autoTag = if (item.isAutoAdded) autoTagStr else ""
                appendLine("$box ${item.name} — ${item.suggestedQuantity} ($${String.format(Locale.US, "%.2f", item.estimatedCostDollars)})$autoTag")
                if (item.zeroWasteBuyingTip.isNotBlank()) {
                    appendLine("    ${context.getString(R.string.checklist_export_tip_fmt, item.zeroWasteBuyingTip)}")
                }
            }
        }
        appendLine("\n========================================")
        appendLine(context.getString(R.string.checklist_export_total_fmt, shoppingItems.size, totalEstimatedCost))
    }
}
