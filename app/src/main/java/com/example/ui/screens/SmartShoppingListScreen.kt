package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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

enum class ShoppingListFilter(val label: String) {
    ALL("All Items"),
    AUTO_LOW_STOCK("⚡ Auto Low-Stock"),
    TO_BUY("To Buy"),
    IN_CART("In Cart (Checked)")
}

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
    onUpdateLowStockThreshold: (Int) -> Unit,
    onRunLowStockScanNow: () -> Unit,
    onNavigateToPantry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf(ShoppingListFilter.ALL) }
    var showAddForm by rememberSaveable { mutableStateOf(false) }
    var showThresholdControls by rememberSaveable { mutableStateOf(true) }

    var newItemName by rememberSaveable { mutableStateOf("") }
    var newItemQuantity by rememberSaveable { mutableStateOf("1 pack") }
    var newItemPrice by rememberSaveable { mutableStateOf("3.49") }
    var newItemTip by rememberSaveable { mutableStateOf("Choose fresh whole produce with minimal plastic packaging.") }
    var newItemCategory by rememberSaveable { mutableStateOf(PantryCategory.PRODUCE) }

    var sliderThreshold by remember(kitchenSetup.lowStockThresholdPercent) {
        mutableFloatStateOf(kitchenSetup.lowStockThresholdPercent.toFloat())
    }

    val autoAddedCount = remember(shoppingItems) { shoppingItems.count { it.isAutoAdded } }
    val checkedCount = remember(shoppingItems) { shoppingItems.count { it.isChecked } }
    val totalEstimatedCost = remember(shoppingItems) { shoppingItems.sumOf { it.estimatedCostDollars } }
    val checkedEstimatedCost = remember(shoppingItems) {
        shoppingItems.filter { it.isChecked }.sumOf { it.estimatedCostDollars }
    }
    val lowStockPantryItems = remember(pantryItems, kitchenSetup.lowStockThresholdPercent) {
        pantryItems.filter { it.stockPercent <= kitchenSetup.lowStockThresholdPercent }
    }

    val filteredShoppingItems = remember(shoppingItems, selectedFilter) {
        when (selectedFilter) {
            ShoppingListFilter.ALL -> shoppingItems
            ShoppingListFilter.AUTO_LOW_STOCK -> shoppingItems.filter { it.isAutoAdded }
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
        // 1. Hero Summary & Cart Action Header
        item {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(id = R.string.shopping_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = stringResource(id = R.string.shopping_subtitle, kitchenSetup.lowStockThresholdPercent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val text = buildString {
                                    appendLine("🛒 PantryPulse Smart Shopping List")
                                    appendLine("Threshold: Auto-Add ≤ ${kitchenSetup.lowStockThresholdPercent}% stock")
                                    appendLine("--------------------------------")
                                    shoppingItems.forEach { item ->
                                        val check = if (item.isChecked) "[x]" else "[ ]"
                                        appendLine("$check ${item.parsedCategory.emoji} ${item.name} (${item.suggestedQuantity}) - $${String.format(Locale.US, "%.2f", item.estimatedCostDollars)}")
                                    }
                                    appendLine("--------------------------------")
                                    appendLine("Estimated Total: $${String.format(Locale.US, "%.2f", totalEstimatedCost)}")
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Smart Shopping List"))
                            },
                            modifier = Modifier.testTag("share_shopping_list_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Shopping List",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // 4 KPI Badges
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
                            label = stringResource(id = R.string.shopping_auto_added),
                            value = "$autoAddedCount",
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRestockAllCheckedToPantry,
                            enabled = checkedCount > 0,
                            modifier = Modifier
                                .weight(1f)
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
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = { showAddForm = !showAddForm },
                            modifier = Modifier.testTag("toggle_add_shopping_item_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (showAddForm) {
                                    stringResource(id = R.string.shopping_btn_close)
                                } else {
                                    stringResource(id = R.string.shopping_btn_add_item)
                                }
                            )
                        }
                    }

                    // Exportable Household Restock Share Card (Categorized Checklist + Intent.ACTION_SEND + Clipboard)
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
                                        shoppingItems = shoppingItems,
                                        thresholdPercent = kitchenSetup.lowStockThresholdPercent,
                                        totalEstimatedCost = totalEstimatedCost
                                    )
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "PantryPulse Restock Checklist",
                                            checklistText
                                        )
                                    )
                                    try {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "PantryPulse Household Restock Checklist")
                                            putExtra(Intent.EXTRA_TEXT, checklistText)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(sendIntent, "Share Household Restock Checklist")
                                        )
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Household Restock Checklist copied to clipboard!",
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

        // 2. Low-Stock Threshold Auto-Trigger Engine Card
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
                                    text = stringResource(id = R.string.shopping_threshold_rule_fmt, sliderThreshold.roundToInt()),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(
                                        id = R.string.shopping_threshold_status_fmt,
                                        lowStockPantryItems.size,
                                        pantryItems.size,
                                        kitchenSetup.lowStockThresholdPercent
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
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
                                        label = { Text("≤ $preset% Stock") },
                                        modifier = Modifier.testTag("threshold_preset_$preset")
                                    )
                                }
                            }

                            if (lowStockPantryItems.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Low-stock inventory monitored: ${lowStockPantryItems.joinToString { "${it.name} (${it.stockPercent}%)" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Collapsible Quick-Add Shopping Item Form
        item {
            AnimatedVisibility(visible = showAddForm) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Add Item to Smart Shopping List",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newItemName,
                                onValueChange = { newItemName = it },
                                label = { Text("Ingredient Name") },
                                placeholder = { Text("e.g., Organic Oat Milk") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1.4f)
                                    .testTag("shopping_add_name_input")
                            )
                            OutlinedTextField(
                                value = newItemQuantity,
                                onValueChange = { newItemQuantity = it },
                                label = { Text("Qty") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.9f)
                                    .testTag("shopping_add_qty_input")
                            )
                            OutlinedTextField(
                                value = newItemPrice,
                                onValueChange = { newItemPrice = it },
                                label = { Text("Est. $") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(0.75f)
                                    .testTag("shopping_add_price_input")
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PantryCategory.entries.forEach { category ->
                                FilterChip(
                                    selected = newItemCategory == category,
                                    onClick = { newItemCategory = category },
                                    label = { Text("${category.emoji} ${category.displayName}") }
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
                                            newItemQuantity.ifBlank { "1 unit" },
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
                                Text("Add to Shopping List", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. Filter Chips
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
                        ShoppingListFilter.TO_BUY -> shoppingItems.count { !it.isChecked }
                        ShoppingListFilter.IN_CART -> checkedCount
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text("${filter.label} ($count)") },
                        modifier = Modifier.testTag("shopping_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        // 5. Shopping List Items or Empty State
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
                        OutlinedButton(onClick = onNavigateToPantry) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.shopping_inspect_pantry_btn))
                        }
                    }
                }
            }
        } else {
            items(filteredShoppingItems, key = { it.id }) { item ->
                SmartShoppingItemCard(
                    item = item,
                    onToggleChecked = { onToggleItemChecked(item) },
                    onRestockToPantry = { onRestockSingleItemToPantry(item) },
                    onDelete = { onDeleteShoppingItem(item) }
                )
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
private fun SmartShoppingItemCard(
    item: RestockItemEntity,
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
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isChecked) 0.dp else 2.dp),
        modifier = Modifier
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

                    // Trigger Reason Pill (Low-Stock Auto-Added vs Used in Recipe vs Manual)
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
                        text = "est.",
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
                        contentDescription = "Remove from Shopping List",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildCategorizedHouseholdChecklist(
    shoppingItems: List<RestockItemEntity>,
    thresholdPercent: Int,
    totalEstimatedCost: Double
): String {
    return buildString {
        appendLine("🛒 PantryPulse Household Restock Checklist")
        appendLine("Auto Low-Stock Threshold Rule: ≤ $thresholdPercent% stock")
        appendLine("========================================")
        val grouped = shoppingItems.groupBy { it.parsedCategory }
        grouped.forEach { (category, catItems) ->
            appendLine("\n${category.emoji} ${category.displayName.uppercase(Locale.US)} (${catItems.size})")
            catItems.forEach { item ->
                val box = if (item.isChecked) "[✓]" else "[ ]"
                val autoTag = if (item.isAutoAdded) " ⚡Auto" else ""
                appendLine("$box ${item.name} — ${item.suggestedQuantity} ($${String.format(Locale.US, "%.2f", item.estimatedCostDollars)})$autoTag")
                if (item.zeroWasteBuyingTip.isNotBlank()) {
                    appendLine("    🌱 Tip: ${item.zeroWasteBuyingTip}")
                }
            }
        }
        appendLine("\n========================================")
        appendLine("Total Items: ${shoppingItems.size} • Est. Total: $${String.format(Locale.US, "%.2f", totalEstimatedCost)}")
    }
}

