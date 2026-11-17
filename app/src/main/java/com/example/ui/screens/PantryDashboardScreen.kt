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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.ai.AiCapabilityTier
import com.example.ai.LocalCulinaryFallbackEngine
import com.example.data.FreshnessStatus
import com.example.data.KitchenSetupState
import com.example.data.PantryCategory
import com.example.data.PantryItemEntity
import com.example.data.PreservationGuide
import com.example.data.RestockItemEntity
import com.example.ui.PantrySortOrder
import com.example.ui.PreservationCoachUiState
import com.example.ui.components.HomeCompactSupportBanner
import com.example.ui.components.PostOperationValueRealizationCard
import com.example.ui.engagement.EngagementSnapshot
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
    engagementState: EngagementSnapshot,
    preservationState: PreservationCoachUiState,
    onSelectCategory: (PantryCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortOrderChange: (PantrySortOrder) -> Unit,
    onNavigateToScan: () -> Unit,
    onNavigateToManualInput: () -> Unit,
    onNavigateToRecipes: () -> Unit,
    onNavigateToShoppingList: () -> Unit,
    onAnalyzeShelfLife: () -> Unit,
    onRunLocalPreservation: () -> Unit,
    onRetryPreservationEcoLite: () -> Unit,
    onClaimGraceCredits: () -> Unit,
    onApplyPreservationGuide: (PreservationGuide) -> Unit,
    onAddManualItem: (String, PantryCategory, String, Int, String) -> Unit,
    onUpdateItem: (PantryItemEntity, String, PantryCategory, String, Int, String) -> Unit,
    onAdjustDays: (PantryItemEntity, Int) -> Unit,
    onAdjustStockPercent: (PantryItemEntity, Int) -> Unit,
    onMarkUsedUp: (PantryItemEntity) -> Unit,
    onDeleteItem: (PantryItemEntity) -> Unit,
    onRestockToFridge: (RestockItemEntity) -> Unit,
    onDismissRestock: (RestockItemEntity) -> Unit,
    onPinOrRefreshWidget: () -> Unit,
    onOpenSupportHub: () -> Unit,
    onOpenRatePrompt: () -> Unit,
    onShareApp: () -> Unit,
    onDismissHomeBanner: () -> Unit,
    onDismissPostOpCard: () -> Unit
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<PantryItemEntity?>(null) }
    var inspectingPreservationGuide by remember { mutableStateOf<PreservationGuide?>(null) }

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

            item {
                PostOperationValueRealizationCard(
                    engagement = engagementState,
                    onOpenSupportDialog = onOpenSupportHub,
                    onOpenRateDialog = onOpenRatePrompt,
                    onShareApp = onShareApp,
                    onDismiss = onDismissPostOpCard,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            item {
                HomeCompactSupportBanner(
                    engagement = engagementState,
                    onOpenSupportDialog = onOpenSupportHub,
                    onOpenRateDialog = onOpenRatePrompt,
                    onShareApp = onShareApp,
                    onDismissBanner = onDismissHomeBanner
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

                item {
                    val preservedFreezerItems = remember(items) {
                        items.filter {
                            it.daysUntilExpiry >= 14 ||
                                it.visualConditionNote.contains("Preserved:", ignoreCase = true) ||
                                it.visualConditionNote.contains("Freeze", ignoreCase = true)
                        }
                    }
                    ShelfLifePreservationCoachCard(
                        preservationState = preservationState,
                        preservedFreezerItems = preservedFreezerItems,
                        onAnalyzeShelfLife = onAnalyzeShelfLife,
                        onRunLocalPreservation = onRunLocalPreservation,
                        onRetryEcoLite = onRetryPreservationEcoLite,
                        onClaimGraceCredits = onClaimGraceCredits,
                        onApplyGuide = onApplyPreservationGuide,
                        onThawAndCookItem = { frozenItem ->
                            onUpdateItem(
                                frozenItem,
                                frozenItem.name,
                                frozenItem.parsedCategory,
                                frozenItem.quantity,
                                1,
                                "Thawed today from 30-Day Freezer Vault — ready for Rescue Recipes!"
                            )
                            onNavigateToRecipes()
                        },
                        onInspectGuide = { guide -> inspectingPreservationGuide = guide }
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
                        lowStockThresholdPercent = kitchenSetup.lowStockThresholdPercent,
                        onOpenShoppingList = onNavigateToShoppingList,
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
                        onAdjustStockPercent = { deltaPct -> onAdjustStockPercent(item, deltaPct) },
                        onOpenPreservationGuide = {
                            val existingGuide = (preservationState as? PreservationCoachUiState.Success)
                                ?.guides
                                ?.firstOrNull { it.itemId == item.id }
                            inspectingPreservationGuide = existingGuide
                                ?: LocalCulinaryFallbackEngine.buildPreservationGuideForItem(item)
                        },
                        onEdit = { editingItem = item },
                        onMarkUsedUp = { onMarkUsedUp(item) },
                        onDelete = { onDeleteItem(item) }
                    )
                }
            }

            item {
                StaticAboutAndSupportCard(
                    engagement = engagementState,
                    onOpenSupportHub = onOpenSupportHub,
                    onOpenRatePrompt = onOpenRatePrompt,
                    onShareApp = onShareApp
                )
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

    inspectingPreservationGuide?.let { guide ->
        IngredientPreservationModal(
            guide = guide,
            onApplyGuide = {
                onApplyPreservationGuide(guide)
                inspectingPreservationGuide = null
            },
            onDismiss = { inspectingPreservationGuide = null }
        )
    }
}

@Composable
private fun ShelfLifePreservationCoachCard(
    preservationState: PreservationCoachUiState,
    preservedFreezerItems: List<PantryItemEntity>,
    onAnalyzeShelfLife: () -> Unit,
    onRunLocalPreservation: () -> Unit,
    onRetryEcoLite: () -> Unit,
    onClaimGraceCredits: () -> Unit,
    onApplyGuide: (PreservationGuide) -> Unit,
    onThawAndCookItem: (PantryItemEntity) -> Unit,
    onInspectGuide: (PreservationGuide) -> Unit
) {
    var expandedGuides by rememberSaveable { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("shelf_life_preservation_coach_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedGuides = !expandedGuides },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = "Shelf-Life & Preservation Coach",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.preservation_coach_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val tierText = when (preservationState) {
                            is PreservationCoachUiState.Success -> preservationState.tierUsed.badgeLabel
                            is PreservationCoachUiState.Analyzing -> stringResource(id = R.string.preservation_coach_analyzing)
                            else -> stringResource(id = R.string.preservation_coach_subtitle)
                        }
                        Text(
                            text = tierText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                TextButton(onClick = { expandedGuides = !expandedGuides }) {
                    Text(if (expandedGuides) stringResource(id = R.string.restock_btn_hide) else stringResource(id = R.string.restock_btn_view))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Cloud AI Deep Shelf-Life Analysis vs. 0-Credit Local Coach
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAnalyzeShelfLife,
                    enabled = preservationState !is PreservationCoachUiState.Analyzing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("analyze_shelf_life_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (preservationState is PreservationCoachUiState.Analyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.preservation_coach_analyzing), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.preservation_btn_ai_analyze), style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedButton(
                    onClick = onRunLocalPreservation,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("local_preservation_guide_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.preservation_btn_local_coach), style = MaterialTheme.typography.labelMedium)
                }
            }

            when (preservationState) {
                is PreservationCoachUiState.QuotaRecoveryNeeded -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = ExpiringAmberContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Cloud AI Allowance Reached — Choose Zero-Friction Recovery:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF311F00)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onClaimGraceCredits,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("+3 Grace Credits", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = onRetryEcoLite,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Eco Lite ⚡", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                is PreservationCoachUiState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = preservationState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = CriticalCrimson
                    )
                }

                is PreservationCoachUiState.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = preservationState.summaryNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = expandedGuides && preservationState.guides.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            preservationState.guides.forEach { guide ->
                                PreservationGuideItemCard(
                                    guide = guide,
                                    onApplyGuide = { onApplyGuide(guide) },
                                    onInspectGuide = { onInspectGuide(guide) }
                                )
                            }
                        }
                    }
                }

                else -> Unit
            }

            // 30-Day Freezer Vault & Rotation Check-In Tracker (High-Value Suggestion #2)
            if (preservedFreezerItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("freezer_vault_rotation_section")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.freezer_vault_title_fmt, preservedFreezerItems.size),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(id = R.string.freezer_vault_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        preservedFreezerItems.take(4).forEach { frozenItem ->
                            val needs30DayCheckIn = frozenItem.daysUntilExpiry <= 30
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${frozenItem.parsedCategory.emoji} ${frozenItem.name} (${frozenItem.quantity})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (needs30DayCheckIn) {
                                            "❄️ 30-Day Check-In: ${frozenItem.daysUntilExpiry}d left • Rotate soon"
                                        } else {
                                            "❄️ Deep Freeze: ${frozenItem.daysUntilExpiry}d shelf life locked"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = { onThawAndCookItem(frozenItem) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("thaw_cook_today_${frozenItem.id}")
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.freezer_btn_thaw_cook),
                                        style = MaterialTheme.typography.labelSmall
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
private fun PreservationGuideItemCard(
    guide: PreservationGuide,
    onApplyGuide: () -> Unit,
    onInspectGuide: () -> Unit
) {
    var showFullSteps by rememberSaveable(guide.id) { mutableStateOf(guide.currentDaysLeft <= 2) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showFullSteps = !showFullSteps }
            .testTag("preservation_guide_card_${guide.itemId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${guide.category.emoji} ${guide.ingredientName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = guide.urgencyHeadline,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (guide.currentDaysLeft <= 1) CriticalCrimson else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = if (guide.isApplied) FreshEmeraldContainer else ExpiringAmberContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (guide.isApplied) {
                            "✅ +${guide.extendedShelfLifeDays}d Locked"
                        } else {
                            "${guide.currentDaysLeft}d → +${guide.extendedShelfLifeDays}d"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (guide.isApplied) FreshEmerald else ExpiringAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Method Pill & Storage Tip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${guide.methodType.badgeEmoji} ${guide.methodType.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Saves ~$${String.format("%.2f", guide.estimatedSavingsDollars)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = guide.personalizedStorageTip,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = showFullSteps) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Step-by-Step Preservation Method:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            guide.preservationSteps.forEach { step ->
                                Text(
                                    text = step,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "🍳 Best Culinary Use After Preserving: ${guide.bestUseAfterPreserving}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onInspectGuide,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (showFullSteps) "Full Guide Modal" else "View ${guide.preservationSteps.size} Steps (${guide.methodType.label})",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Button(
                    onClick = onApplyGuide,
                    enabled = !guide.isApplied,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (guide.isApplied) FreshEmerald else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("apply_preservation_${guide.itemId}")
                ) {
                    Icon(
                        imageVector = if (guide.isApplied) Icons.Default.CheckCircle else Icons.Default.AcUnit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (guide.isApplied) {
                            stringResource(id = R.string.preservation_btn_applied_fmt, guide.extendedShelfLifeDays)
                        } else {
                            stringResource(id = R.string.preservation_btn_apply_fmt, guide.extendedShelfLifeDays)
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StaticAboutAndSupportCard(
    engagement: EngagementSnapshot,
    onOpenSupportHub: () -> Unit,
    onOpenRatePrompt: () -> Unit,
    onShareApp: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("static_about_support_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.static_support_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (engagement.isActiveSupporter) {
                    Surface(
                        color = Color(0xFF1F6E43),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = stringResource(id = R.string.support_chip_active),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = stringResource(id = R.string.static_support_card_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSupportHub,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .testTag("static_support_open_hub_button")
                ) {
                    Text(stringResource(id = R.string.hub_tab_donate), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onOpenRatePrompt,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("static_support_rate_button")
                ) {
                    Text(stringResource(id = R.string.btn_rate_star), style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onShareApp,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .testTag("static_support_share_button")
                ) {
                    Text(stringResource(id = R.string.btn_share), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun IngredientPreservationModal(
    guide: PreservationGuide,
    onApplyGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "${guide.methodType.badgeEmoji} ${guide.ingredientName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${guide.methodType.label} • Extends Shelf-Life by +${guide.extendedShelfLifeDays} Days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Immediate Fridge Storage Tip:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = guide.personalizedStorageTip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Text(
                    text = "Zero-Waste Preservation Steps:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                guide.preservationSteps.forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "🍳 How to Cook After Preserving:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = guide.bestUseAfterPreserving,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onApplyGuide,
                enabled = !guide.isApplied,
                modifier = Modifier.testTag("modal_apply_preservation_button")
            ) {
                Icon(Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (guide.isApplied) {
                        "Already Preserved (+${guide.extendedShelfLifeDays}d)"
                    } else {
                        "Apply Preservation (+${guide.extendedShelfLifeDays}d)"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun SmartRestockCard(
    restockItems: List<RestockItemEntity>,
    lowStockThresholdPercent: Int,
    onOpenShoppingList: () -> Unit,
    onRestockToFridge: (RestockItemEntity) -> Unit,
    onDismissRestock: (RestockItemEntity) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val autoAddedCount = remember(restockItems) { restockItems.count { it.isAutoAdded } }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
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
                            text = "⚡ $autoAddedCount auto-added via Low-Stock Threshold (≤$lowStockThresholdPercent%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(id = R.string.restock_btn_hide) else stringResource(id = R.string.restock_btn_view))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenShoppingList,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_smart_shopping_list_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Smart Shopping List (${restockItems.size}) →", fontWeight = FontWeight.Bold)
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
                                        text = item.triggerReason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
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
    onAdjustStockPercent: (Int) -> Unit,
    onOpenPreservationGuide: () -> Unit,
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

            // Stock Level Progress & Low-Stock Threshold Auto-Shopping List Control
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = if (item.isLowStock) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (item.isLowStock) {
                                "⚡ Low Stock: ${item.stockPercent}% (≤${item.lowStockThresholdPercent}%) • Auto-Listed in Cart"
                            } else {
                                "Stock Level: ${item.stockPercent}% (Auto-List ≤${item.lowStockThresholdPercent}%)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (item.isLowStock) FontWeight.Bold else FontWeight.Medium,
                            color = if (item.isLowStock) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((item.stockPercent / 100f).coerceIn(0.05f, 1f))
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            item.isLowStock -> CriticalCrimson
                                            item.stockPercent <= 50 -> ExpiringAmber
                                            else -> FreshEmerald
                                        }
                                    )
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = { onAdjustStockPercent(-25) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("decrease_stock_${item.id}")
                        ) {
                            Text("-25%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = { onAdjustStockPercent(25) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("increase_stock_${item.id}")
                        ) {
                            Text("+25%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                    TextButton(
                        onClick = onOpenPreservationGuide,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("preserve_item_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AcUnit,
                            contentDescription = "Preserve or freeze tip",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Preserve",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

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
