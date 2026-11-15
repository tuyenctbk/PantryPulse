package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiFeatureType
import com.example.ai.UsageQuotaSnapshot
import com.example.data.KitchenSetupState
import com.example.data.PantryItemEntity
import com.example.data.RecipeCategoryFilter
import com.example.data.RecipeDietaryPreference
import com.example.data.RecipeSuggestion
import com.example.ui.RecipeGenerationUiState
import com.example.ui.components.QuotaSoftNudgeBanner
import com.example.ui.components.SmartQuotaRecoveryCard
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.CriticalCrimsonContainer
import com.example.ui.theme.ExpiringAmber
import com.example.ui.theme.ExpiringAmberContainer
import com.example.ui.theme.FreshEmerald
import com.example.ui.theme.FreshEmeraldContainer
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RescueRecipesScreen(
    recipes: List<RecipeSuggestion>,
    pantryItems: List<PantryItemEntity>,
    kitchenSetup: KitchenSetupState,
    recipeSearchQuery: String,
    selectedCategories: Set<RecipeCategoryFilter>,
    selectedDietaryPreferences: Set<RecipeDietaryPreference>,
    selectedIngredientFilters: Set<String>,
    recipeGenState: RecipeGenerationUiState,
    quotaState: UsageQuotaSnapshot,
    activeCookingRecipe: RecipeSuggestion?,
    onSearchQueryChange: (String) -> Unit,
    onToggleRecipeCategory: (RecipeCategoryFilter) -> Unit,
    onClearRecipeCategories: () -> Unit,
    onToggleDietaryPreference: (RecipeDietaryPreference) -> Unit,
    onToggleIngredientFilter: (String) -> Unit,
    onClearAllFilters: () -> Unit,
    onToggleAppliance: (String) -> Unit,
    onToggleSpice: (String) -> Unit,
    onGenerateRecipes: (String) -> Unit,
    onEnableEcoLite: () -> Unit,
    onClaimGraceCredits: () -> Unit,
    onRetryEcoLite: (String) -> Unit,
    onUseLocalFallback: (String) -> Unit,
    onOpenValueReceipt: () -> Unit,
    onOpenCookingMode: (RecipeSuggestion) -> Unit,
    onCloseCookingMode: () -> Unit,
    onCompleteCookingMeal: (RecipeSuggestion) -> Unit,
    onNavigateBack: () -> Unit
) {
    if (activeCookingRecipe != null) {
        InteractiveCookingScreen(
            recipe = activeCookingRecipe,
            onBack = onCloseCookingMode,
            onCompleteMeal = { onCompleteCookingMeal(activeCookingRecipe) }
        )
        return
    }

    BackHandler(onBack = onNavigateBack)

    var cravingNote by rememberSaveable { mutableStateOf("") }
    var showKitchenCustomizer by rememberSaveable { mutableStateOf(false) }

    val expiringCount = remember(pantryItems) { pantryItems.count { it.daysUntilExpiry <= 3 } }

    // Filter recipes by Search Query + Category Style + Dietary Preference + Available Ingredients
    val filteredRecipes = remember(
        recipes,
        recipeSearchQuery,
        selectedCategories,
        selectedDietaryPreferences,
        selectedIngredientFilters
    ) {
        recipes.filter { recipe ->
            val matchesQuery = recipe.matchesSearchQuery(recipeSearchQuery)
            val matchesCategory = selectedCategories.isEmpty() || selectedCategories.contains(recipe.recipeCategory)
            val matchesDietary = selectedDietaryPreferences.isEmpty() ||
                selectedDietaryPreferences.all { pref -> recipe.matchesDietaryPreference(pref) }
            val matchesIngredients = selectedIngredientFilters.isEmpty() ||
                selectedIngredientFilters.all { ingName -> recipe.matchesAvailableIngredient(ingName) }

            matchesQuery && matchesCategory && matchesDietary && matchesIngredients
        }
    }

    val hasAnyActiveFilter = recipeSearchQuery.isNotBlank() ||
        selectedCategories.isNotEmpty() ||
        selectedDietaryPreferences.isNotEmpty() ||
        selectedIngredientFilters.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("rescue_recipes_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(id = R.string.recipes_screen_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.recipes_screen_subtitle, expiringCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Stage 1 (80% Soft Nudge Banner)
        if (quotaState.isSoftNudgeThreshold && !quotaState.ecoLiteMode) {
            item {
                QuotaSoftNudgeBanner(
                    quota = quotaState,
                    onEnableEcoLite = onEnableEcoLite,
                    onOpenValueReceipt = onOpenValueReceipt
                )
            }
        }

        // Searchable & Filterable Recipe Explorer Hub (Search + Style Category + Dietary + Available Ingredients)
        item {
            RecipeSearchAndFilterCard(
                searchQuery = recipeSearchQuery,
                totalRecipeCount = recipes.size,
                filteredRecipeCount = filteredRecipes.size,
                pantryItems = pantryItems,
                selectedCategories = selectedCategories,
                selectedDietaryPreferences = selectedDietaryPreferences,
                selectedIngredientFilters = selectedIngredientFilters,
                onSearchQueryChange = onSearchQueryChange,
                onToggleCategory = onToggleRecipeCategory,
                onClearCategories = onClearRecipeCategories,
                onToggleDietaryPreference = onToggleDietaryPreference,
                onToggleIngredientFilter = onToggleIngredientFilter,
                onClearAllFilters = onClearAllFilters
            )
        }

        item {
            KitchenApplianceAndSpiceCard(
                kitchenSetup = kitchenSetup,
                expanded = showKitchenCustomizer,
                onToggleExpanded = { showKitchenCustomizer = !showKitchenCustomizer },
                onToggleAppliance = onToggleAppliance,
                onToggleSpice = onToggleSpice
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = cravingNote,
                        onValueChange = { cravingNote = it },
                        label = { Text(stringResource(id = R.string.recipes_craving_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recipe_craving_input")
                    )

                    val activeFilterLabel = buildList {
                        if (selectedCategories.isNotEmpty()) {
                            add(selectedCategories.joinToString("+") { it.displayName })
                        }
                        if (selectedDietaryPreferences.isNotEmpty()) {
                            add(selectedDietaryPreferences.joinToString("+") { it.displayName })
                        }
                        if (selectedIngredientFilters.isNotEmpty()) {
                            add("${selectedIngredientFilters.size} Ing.")
                        }
                    }.joinToString(" • ").ifBlank { stringResource(id = R.string.recipes_all_styles) }

                    Button(
                        onClick = { onGenerateRecipes(cravingNote) },
                        enabled = recipeGenState !is RecipeGenerationUiState.Generating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_recipes_button")
                    ) {
                        if (recipeGenState is RecipeGenerationUiState.Generating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Ranking ($activeFilterLabel • ${quotaState.activeCapabilityTier.badgeLabel})...")
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${stringResource(id = R.string.generate_recipes_cta)} ($activeFilterLabel)")
                        }
                    }
                }
            }
        }

        // Stage 2: Smart Quota Recovery Card when 100% or 429 hit
        if (recipeGenState is RecipeGenerationUiState.QuotaRecoveryNeeded || quotaState.isExhausted) {
            item {
                SmartQuotaRecoveryCard(
                    feature = AiFeatureType.RECIPE_RANKING,
                    quota = quotaState,
                    onClaimGraceCredits = onClaimGraceCredits,
                    onRetryEcoLite = { onRetryEcoLite(cravingNote) },
                    onUseLocalFallback = { onUseLocalFallback(cravingNote) },
                    onOpenValueReceipt = onOpenValueReceipt
                )
            }
        }

        if (recipeGenState is RecipeGenerationUiState.Error) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recipe_error_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = recipeGenState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onUseLocalFallback(cravingNote) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Run On-Device Chef Synthesis (0 Cloud Credits)")
                        }
                    }
                }
            }
        }

        if (filteredRecipes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("empty_filtered_recipes_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.recipes_empty_filtered_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = R.string.recipes_empty_filtered_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onGenerateRecipes(cravingNote) },
                                modifier = Modifier.testTag("generate_filtered_recipes_button")
                            ) {
                                Text(stringResource(id = R.string.recipes_btn_generate_matching))
                            }
                            if (hasAnyActiveFilter) {
                                OutlinedButton(
                                    onClick = onClearAllFilters,
                                    modifier = Modifier.testTag("reset_all_recipe_filters_button")
                                ) {
                                    Text(stringResource(id = R.string.recipes_reset_all))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredRecipes, key = { it.id }) { recipe ->
                RankedRecipeCard(
                    recipe = recipe,
                    pantryItems = pantryItems,
                    selectedIngredientFilters = selectedIngredientFilters,
                    onStartCooking = { onOpenCookingMode(recipe) }
                )
            }
        }
    }
}

@Composable
private fun RecipeSearchAndFilterCard(
    searchQuery: String,
    totalRecipeCount: Int,
    filteredRecipeCount: Int,
    pantryItems: List<PantryItemEntity>,
    selectedCategories: Set<RecipeCategoryFilter>,
    selectedDietaryPreferences: Set<RecipeDietaryPreference>,
    selectedIngredientFilters: Set<String>,
    onSearchQueryChange: (String) -> Unit,
    onToggleCategory: (RecipeCategoryFilter) -> Unit,
    onClearCategories: () -> Unit,
    onToggleDietaryPreference: (RecipeDietaryPreference) -> Unit,
    onToggleIngredientFilter: (String) -> Unit,
    onClearAllFilters: () -> Unit
) {
    val hasActiveFilter = searchQuery.isNotBlank() ||
        selectedCategories.isNotEmpty() ||
        selectedDietaryPreferences.isNotEmpty() ||
        selectedIngredientFilters.isNotEmpty()

    val sortedPantryItems = remember(pantryItems) {
        pantryItems.sortedBy { it.daysUntilExpiry }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recipe_category_filter_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row + Live Match Count + Reset All
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.recipes_search_filter_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(id = R.string.recipes_showing_count, filteredRecipeCount, totalRecipeCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (hasActiveFilter) {
                    Text(
                        text = stringResource(id = R.string.recipes_reset_all),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onClearAllFilters() }
                            .padding(4.dp)
                            .testTag("reset_recipe_category_filters")
                    )
                }
            }

            // 1. Search Input Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(id = R.string.recipes_search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search recipes")
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear recipe search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recipe_search_input")
            )

            // 2. Recipe Style Categories ('Quick & Easy', 'Healthy/Low-Calorie', 'Budget-Friendly')
            Text(
                text = stringResource(id = R.string.recipes_style_category_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategories.isEmpty(),
                    onClick = onClearCategories,
                    label = { Text(stringResource(id = R.string.recipes_all_styles)) },
                    modifier = Modifier.testTag("recipe_filter_all")
                )

                RecipeCategoryFilter.entries.forEach { category ->
                    val isSelected = selectedCategories.contains(category)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleCategory(category) },
                        label = { Text(category.displayName) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("recipe_filter_${category.name.lowercase()}")
                    )
                }
            }

            // 3. Dietary Preference Filter Row
            Text(
                text = stringResource(id = R.string.recipes_dietary_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecipeDietaryPreference.entries.forEach { pref ->
                    val isSelected = selectedDietaryPreferences.contains(pref)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onToggleDietaryPreference(pref) },
                        label = { Text(pref.badgeLabel) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FreshEmeraldContainer,
                            selectedLabelColor = FreshEmerald
                        ),
                        modifier = Modifier.testTag("dietary_filter_${pref.name.lowercase()}")
                    )
                }
            }

            // 4. Available Fridge Ingredients Filter Row
            if (sortedPantryItems.isNotEmpty()) {
                Text(
                    text = stringResource(id = R.string.recipes_available_ingredients_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedPantryItems.forEachIndexed { idx, item ->
                        val isSelected = selectedIngredientFilters.contains(item.name)
                        val isUrgent = item.daysUntilExpiry <= 2
                        FilterChip(
                            selected = isSelected,
                            onClick = { onToggleIngredientFilter(item.name) },
                            label = {
                                Text(
                                    text = "${item.parsedCategory.emoji} ${item.name} (${item.daysUntilExpiry}d)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (isUrgent) ExpiringAmberContainer else MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = if (isUrgent) ExpiringAmber else MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("ingredient_filter_chip_$idx")
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KitchenApplianceAndSpiceCard(
    kitchenSetup: KitchenSetupState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleAppliance: (String) -> Unit,
    onToggleSpice: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .testTag("toggle_kitchen_setup_button"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.kitchen_setup_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(id = R.string.kitchen_setup_subtitle, kitchenSetup.ownedAppliances.size, kitchenSetup.ownedSpices.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Expand Kitchen Setup"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Owned Appliances (Gemini tailors cooking steps to these):",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KitchenSetupState.ALL_APPLIANCES.forEach { appliance ->
                            val selected = kitchenSetup.ownedAppliances.contains(appliance)
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleAppliance(appliance) },
                                label = { Text(appliance) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Owned Spices & Condiments:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KitchenSetupState.ALL_SPICES.forEach { spice ->
                            val selected = kitchenSetup.ownedSpices.contains(spice)
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleSpice(spice) },
                                label = { Text(spice) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedRecipeCard(
    recipe: RecipeSuggestion,
    pantryItems: List<PantryItemEntity>,
    selectedIngredientFilters: Set<String>,
    onStartCooking: () -> Unit
) {
    val matchedInStockCount = remember(recipe, pantryItems) {
        recipe.countMatchedPantryItems(pantryItems)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recipe_card_rank_${recipe.rank}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (recipe.rank == 1) CriticalCrimson else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "RANK #${recipe.rank}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = ExpiringAmberContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = recipe.recipeCategory.shortBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ExpiringAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Surface(
                    color = FreshEmeraldContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = FreshEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${recipe.rescueScore}% Rescue",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FreshEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = recipe.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Dietary Preference & Available Pantry Match Badges
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (matchedInStockCount > 0) {
                    Surface(
                        color = FreshEmeraldContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "✓ Uses $matchedInStockCount In-Stock Fridge Items",
                            style = MaterialTheme.typography.labelSmall,
                            color = FreshEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
                recipe.inferredDietaryBadges.forEach { badge ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = badge.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${recipe.prepTimeMinutes} mins",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Kitchen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = recipe.applianceUsed,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${recipe.caloriesPerServing} kcal/srv",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${String.format("%.2f", recipe.estimatedCostPerServing)}/srv",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = CriticalCrimsonContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.recipes_why_it_rescues),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CriticalCrimson
                    )
                    Text(
                        text = recipe.whyItRescues,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.recipes_ingredients_used),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (recipe.expiringIngredientsUsed + recipe.pantryIngredientsUsed).forEach { ing ->
                    val isHighlightedFilter = selectedIngredientFilters.any { selectedIng ->
                        ing.contains(selectedIng, ignoreCase = true) ||
                            selectedIng.split(" ").any { w -> w.length >= 4 && ing.contains(w, ignoreCase = true) }
                    }
                    Surface(
                        color = if (isHighlightedFilter) {
                            ExpiringAmberContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "✓ $ing",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isHighlightedFilter) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHighlightedFilter) {
                                ExpiringAmber
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            var showVisualChefCard by rememberSaveable { mutableStateOf(false) }
            if (showVisualChefCard) {
                VisualChefPlatingDialog(
                    recipe = recipe,
                    onDismiss = { showVisualChefCard = false }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showVisualChefCard = true },
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("visual_chef_card_${recipe.rank}")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.recipes_btn_chef_card))
                }

                Button(
                    onClick = onStartCooking,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("cook_recipe_${recipe.rank}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.recipes_btn_cook_steps, recipe.steps.size))
                }
            }
        }
    }
}

/**
 * Tier 3 Zero-Credit On-Device Canvas Studio Generator:
 * Renders a stylized custom Recipe Plating & Zero-Waste Rescue Emblem Card locally using Compose Canvas
 * so visual recipe cards never consume cloud credits.
 */
@Composable
private fun VisualChefPlatingDialog(
    recipe: RecipeSuggestion,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Zero-Waste Chef Plating Card",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Rendered On-Device via Compose Canvas • 0 Cloud Credits",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stylized On-Device Canvas Culinary Plating Illustration & Rescue Ring
                Surface(
                    color = Color(0xFF142E21),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            val plateRadius = size.minDimension * 0.42f

                            // Outer warm gold rim
                            drawCircle(
                                color = Color(0xFFE5A93C).copy(alpha = 0.28f),
                                radius = plateRadius * 1.15f,
                                center = center
                            )
                            // Ceramic plate body
                            drawCircle(
                                color = Color(0xFFF7F4EB),
                                radius = plateRadius,
                                center = center
                            )
                            // Inner plate rim ring
                            drawCircle(
                                color = Color(0xFFD6CFC2),
                                radius = plateRadius * 0.82f,
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                            )
                            // Stylized greens / herbs / proteins plating accents
                            drawCircle(
                                color = Color(0xFF2E7D52),
                                radius = plateRadius * 0.36f,
                                center = androidx.compose.ui.geometry.Offset(center.x - 18f, center.y - 8f)
                            )
                            drawCircle(
                                color = Color(0xFFD96B27),
                                radius = plateRadius * 0.28f,
                                center = androidx.compose.ui.geometry.Offset(center.x + 22f, center.y + 10f)
                            )
                            drawCircle(
                                color = Color(0xFFF2C94C),
                                radius = plateRadius * 0.18f,
                                center = androidx.compose.ui.geometry.Offset(center.x + 6f, center.y - 20f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recipe.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF4F1EA)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${recipe.rescueScore}% Zero-Waste Rescue • ${recipe.prepTimeMinutes}m in ${recipe.applianceUsed} • $${String.format("%.2f", recipe.estimatedCostPerServing)}/srv",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFA6F2CA)
                        )
                    }
                }

                Text(
                    text = "Rescued Ingredients Featured:",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = recipe.expiringIngredientsUsed.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Chef's Plating & Zero-Waste Tip:",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = recipe.chefTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close Card")
            }
        }
    )
}

@Composable
private fun InteractiveCookingScreen(
    recipe: RecipeSuggestion,
    onBack: () -> Unit,
    onCompleteMeal: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.US
            }
        }
        ttsEngine = instance
        onDispose {
            instance?.stop()
            instance?.shutdown()
        }
    }

    val completedSteps = remember(recipe.id) {
        mutableStateListOf(*Array(recipe.steps.size) { false })
    }
    val completedCount = completedSteps.count { it }
    val progress = if (recipe.steps.isNotEmpty()) {
        completedCount.toFloat() / recipe.steps.size.toFloat()
    } else 0f

    var timerSecondsRemaining by rememberSaveable(recipe.id) { mutableIntStateOf(180) }
    var isTimerRunning by rememberSaveable(recipe.id) { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning, timerSecondsRemaining) {
        if (isTimerRunning && timerSecondsRemaining > 0) {
            delay(1_000L)
            timerSecondsRemaining -= 1
            if (timerSecondsRemaining == 0) {
                isTimerRunning = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("interactive_cooking_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("cooking_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Recipes"
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rank #${recipe.rank} • ${recipe.recipeCategory.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = recipe.title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (isSpeaking) {
                            ttsEngine?.stop()
                            isSpeaking = false
                        } else {
                            val fullScript = buildString {
                                append("Cooking ${recipe.title}. ")
                                recipe.steps.forEachIndexed { i, s ->
                                    append("Step ${i + 1}: $s. ")
                                }
                            }
                            ttsEngine?.speak(fullScript, TextToSpeech.QUEUE_FLUSH, null, "recipe_all")
                            isSpeaking = true
                        }
                    },
                    modifier = Modifier.testTag("tts_read_all_button")
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Hands-free voice guide",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSpeaking) "Stop" else "Voice")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                            text = "${recipe.applianceUsed} • ${recipe.caloriesPerServing} kcal • $${String.format("%.2f", recipe.estimatedCostPerServing)}/srv",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "$completedCount/${recipe.steps.size} steps",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    Text(
                        text = "Rescuing: ${recipe.expiringIngredientsUsed.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (recipe.spicesUsed.isNotEmpty()) {
                        Text(
                            text = "Spices used: ${recipe.spicesUsed.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Interactive Kitchen Timer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Kitchen Timer",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Built-In Kitchen Timer",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                val mins = timerSecondsRemaining / 60
                                val secs = timerSecondsRemaining % 60
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (timerSecondsRemaining == 0) timerSecondsRemaining = 180
                                    isTimerRunning = !isTimerRunning
                                },
                                modifier = Modifier.testTag("cooking_timer_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isTimerRunning) "Pause timer" else "Start timer",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isTimerRunning) "Pause" else "Start")
                            }

                            OutlinedButton(
                                onClick = {
                                    isTimerRunning = false
                                    timerSecondsRemaining = 180
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = "Reset timer",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(60 to "1 min", 180 to "3 min", 300 to "5 min", 480 to "8 min", 720 to "12 min").forEach { (sec, label) ->
                            FilterChip(
                                selected = timerSecondsRemaining == sec && !isTimerRunning,
                                onClick = {
                                    isTimerRunning = false
                                    timerSecondsRemaining = sec
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        items(recipe.steps.size) { idx ->
            val stepText = recipe.steps[idx]
            val isChecked = completedSteps.getOrNull(idx) == true
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (idx < completedSteps.size) {
                            completedSteps[idx] = !completedSteps[idx]
                        }
                    }
                    .testTag("recipe_step_card_$idx"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isChecked) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (idx < completedSteps.size) {
                                completedSteps[idx] = checked
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Step ${idx + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stepText,
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                        )
                    }
                    IconButton(
                        onClick = {
                            ttsEngine?.speak(
                                "Step ${idx + 1}. $stepText",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "step_$idx"
                            )
                            isSpeaking = true
                        },
                        modifier = Modifier.testTag("speak_step_$idx")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Read Step ${idx + 1} Aloud",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (recipe.chefTip.isNotBlank()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Chef's Zero-Waste Secret",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recipe.chefTip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onCompleteMeal,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("complete_meal_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.recipes_complete_meal_btn))
            }
        }
    }
}
