package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.widget.PantryPulseWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.kitchenDataStore by preferencesDataStore(name = "kitchen_preferences")

data class KitchenSetupState(
    val ownedAppliances: Set<String> = DEFAULT_APPLIANCES,
    val ownedSpices: Set<String> = DEFAULT_SPICES,
    val rescuedMealsCount: Int = 4,
    val savedDollarsTotal: Double = 38.50,
    val lowStockThresholdPercent: Int = 30
) {
    companion object {
        val ALL_APPLIANCES = listOf(
            "Stovetop Skillet",
            "Cast Iron Pan",
            "Air Fryer",
            "Convection Oven",
            "Instant Pot",
            "High-Speed Blender",
            "Wok",
            "Sheet Pan"
        )
        val DEFAULT_APPLIANCES = setOf(
            "Stovetop Skillet",
            "Cast Iron Pan",
            "Air Fryer",
            "Convection Oven"
        )

        val ALL_SPICES = listOf(
            "Extra Virgin Olive Oil",
            "Smoked Paprika",
            "Roasted Garlic Powder",
            "Crushed Red Pepper",
            "Ground Cumin",
            "Soy Sauce",
            "Balsamic Glaze",
            "Italian Herbs",
            "Dijon Mustard",
            "Fresh Black Pepper",
            "Turmeric",
            "Sesame Oil"
        )
        val DEFAULT_SPICES = setOf(
            "Extra Virgin Olive Oil",
            "Smoked Paprika",
            "Roasted Garlic Powder",
            "Crushed Red Pepper",
            "Soy Sauce",
            "Italian Herbs",
            "Fresh Black Pepper"
        )
    }
}

class PantryRepository(
    private val appContext: Context,
    private val dao: PantryDao
) {
    private val appliancesKey = stringSetPreferencesKey("owned_appliances")
    private val spicesKey = stringSetPreferencesKey("owned_spices")
    private val rescuedMealsKey = intPreferencesKey("rescued_meals_count")
    private val savedDollarsKey = doublePreferencesKey("saved_dollars_total")
    private val lowStockThresholdKey = intPreferencesKey("low_stock_threshold_percent")

    val activeItemsFlow: Flow<List<PantryItemEntity>> = dao.getActiveItemsFlow()

    val savedRecipesFlow: Flow<List<RecipeSuggestion>> = dao.getSavedRecipesFlow().map { list ->
        list.map { it.toDomain() }
    }

    val restockItemsFlow: Flow<List<RestockItemEntity>> = dao.getRestockItemsFlow()

    val kitchenSetupFlow: Flow<KitchenSetupState> = appContext.kitchenDataStore.data.map { prefs ->
        KitchenSetupState(
            ownedAppliances = prefs[appliancesKey] ?: KitchenSetupState.DEFAULT_APPLIANCES,
            ownedSpices = prefs[spicesKey] ?: KitchenSetupState.DEFAULT_SPICES,
            rescuedMealsCount = prefs[rescuedMealsKey] ?: 4,
            savedDollarsTotal = prefs[savedDollarsKey] ?: 38.50,
            lowStockThresholdPercent = prefs[lowStockThresholdKey] ?: 30
        )
    }

    companion object {
        fun inferStockPercentFromQuantity(quantity: String): Int {
            val lower = quantity.lowercase()
            return when {
                lower.contains("1/4") || lower.contains("quarter") || lower.contains("20%") ||
                    lower.contains("almost empty") || lower.contains("1 tbsp") || lower.contains("2 tbsp") -> 20
                lower.contains("1/3") || lower.contains("third") || lower.contains("25%") ||
                    lower.contains("30%") || lower.contains("low") -> 25
                lower.contains("1/2") || lower.contains("half") || lower.contains("50%") -> 45
                lower.contains("2/3") || lower.contains("3/4") || lower.contains("75%") -> 75
                else -> 90
            }
        }

        fun defaultReplenishQuantityFor(category: PantryCategory, currentQuantity: String): String {
            return when (category) {
                PantryCategory.PRODUCE -> "1 fresh pack / bunch"
                PantryCategory.DAIRY_CHEESE -> "1 full block / tub (8 oz)"
                PantryCategory.SAUCES_JARS -> "1 full jar (12 oz)"
                PantryCategory.PROTEIN -> "1 full carton / pack"
                PantryCategory.LEFTOVERS -> "1 meal portion"
                PantryCategory.PANTRY_STAPLE -> "1 pantry pack"
            }.takeIf {
                currentQuantity.contains("1/") || currentQuantity.contains("half", ignoreCase = true) ||
                    currentQuantity.contains("tbsp", ignoreCase = true)
            } ?: currentQuantity.ifBlank { "1 full pack" }
        }
    }

    suspend fun ensureSeeded() {
        if (dao.getTotalEverCount() == 0) {
            val starterItems = listOf(
                PantryItemEntity(
                    name = "Baby Spinach Clamshell",
                    category = PantryCategory.PRODUCE.name,
                    quantity = "1/2 box (~3 oz)",
                    daysUntilExpiry = 1,
                    visualConditionNote = "Top leaves slightly wilting; great for sautéing or frittatas today",
                    storageTip = "Place a dry paper towel inside the box to absorb excess moisture.",
                    estimatedValueDollars = 3.99,
                    stockPercent = 45,
                    lowStockThresholdPercent = 30
                ),
                PantryItemEntity(
                    name = "Heirloom Cherry Tomatoes",
                    category = PantryCategory.PRODUCE.name,
                    quantity = "1 cup",
                    daysUntilExpiry = 2,
                    visualConditionNote = "Skins starting to wrinkle; peak sweetness for blistering in a pan",
                    storageTip = "Store stem-side down at room temperature or front shelf.",
                    estimatedValueDollars = 4.50,
                    stockPercent = 60,
                    lowStockThresholdPercent = 30
                ),
                PantryItemEntity(
                    name = "Artisanal Basil Pesto Jar",
                    category = PantryCategory.SAUCES_JARS.name,
                    quantity = "1/3 jar (~3 tbsp)",
                    daysUntilExpiry = 2,
                    visualConditionNote = "Opened last week; olive oil layer getting thin — Low Stock!",
                    storageTip = "Pour a thin film of olive oil over the top to prevent browning.",
                    estimatedValueDollars = 5.25,
                    stockPercent = 25,
                    lowStockThresholdPercent = 30
                ),
                PantryItemEntity(
                    name = "Sharp Aged White Cheddar",
                    category = PantryCategory.DAIRY_CHEESE.name,
                    quantity = "5 oz wedge",
                    daysUntilExpiry = 3,
                    visualConditionNote = "Opened parchment wrap; edges slightly dry",
                    storageTip = "Wrap in wax paper, then loosely in a reusable bag.",
                    estimatedValueDollars = 6.00,
                    stockPercent = 55,
                    lowStockThresholdPercent = 30
                ),
                PantryItemEntity(
                    name = "Roasted Red Bell Pepper Half",
                    category = PantryCategory.PRODUCE.name,
                    quantity = "1/4 jar left",
                    daysUntilExpiry = 2,
                    visualConditionNote = "Cut edge softening; low stock remaining in jar",
                    storageTip = "Keep in an airtight glass container in the crisper drawer.",
                    estimatedValueDollars = 2.25,
                    stockPercent = 20,
                    lowStockThresholdPercent = 30
                ),
                PantryItemEntity(
                    name = "Pasture-Raised Eggs",
                    category = PantryCategory.PROTEIN.name,
                    quantity = "6 eggs",
                    daysUntilExpiry = 8,
                    visualConditionNote = "Fresh intact shells on middle shelf",
                    storageTip = "Keep in original carton on middle shelf, not the fridge door.",
                    estimatedValueDollars = 4.75,
                    stockPercent = 50,
                    lowStockThresholdPercent = 30
                ),
                PantryItemEntity(
                    name = "Sun-Dried Tomato Paste Tube",
                    category = PantryCategory.SAUCES_JARS.name,
                    quantity = "Half tube",
                    daysUntilExpiry = 14,
                    visualConditionNote = "Sealed cap, concentrated savory umami booster",
                    storageTip = "Keep cap tightly screwed in door condiment bin.",
                    estimatedValueDollars = 3.50,
                    stockPercent = 50,
                    lowStockThresholdPercent = 30
                )
            )
            dao.insertItems(starterItems)

            val starterRecipes = listOf(
                RecipeSuggestion(
                    id = "starter-1",
                    rank = 1,
                    title = "Blistered Tomato & Pesto Egg White Skillet Frittata",
                    recipeCategory = RecipeCategoryFilter.HEALTHY_LOW_CAL,
                    rescueScore = 98,
                    prepTimeMinutes = 16,
                    servings = 2,
                    caloriesPerServing = 310,
                    estimatedCostPerServing = 2.10,
                    applianceUsed = "Cast Iron Pan",
                    whyItRescues = "Rescues 4 expiring items at once: wilting baby spinach, wrinkling cherry tomatoes, half-jar basil pesto, and aged white cheddar.",
                    expiringIngredientsUsed = listOf(
                        "Baby Spinach Clamshell (3 oz)",
                        "Heirloom Cherry Tomatoes (1 cup)",
                        "Artisanal Basil Pesto Jar (1.5 tbsp)",
                        "Sharp Aged White Cheddar (1.5 oz grated)"
                    ),
                    pantryIngredientsUsed = listOf(
                        "Pasture-Raised Eggs (4 eggs)",
                        "Roasted Red Bell Pepper Half (diced)"
                    ),
                    spicesUsed = listOf(
                        "Extra Virgin Olive Oil",
                        "Smoked Paprika",
                        "Fresh Black Pepper"
                    ),
                    steps = listOf(
                        "Heat 1 tsp Extra Virgin Olive Oil in your Cast Iron Pan over medium-high heat. Add the wrinkling Heirloom Cherry Tomatoes and diced Roasted Red Bell Pepper for 3 minutes until blistered.",
                        "Toss in the wilting Baby Spinach and a pinch of Smoked Paprika; stir for 60 seconds until just wilted.",
                        "Whisk 4 Pasture-Raised Eggs with 1.5 tbsp Artisanal Basil Pesto and Fresh Black Pepper, then pour evenly over the warm vegetables.",
                        "Scatter grated Sharp Aged White Cheddar on top and cook on medium-low for 5 minutes until puffed, golden, and light."
                    ),
                    chefTip = "Wrinkled cherry tomatoes have less water and higher sugar concentration—blistering them first creates an instant jammy sauce."
                ),
                RecipeSuggestion(
                    id = "starter-2",
                    rank = 2,
                    title = "12-Minute Charred Pepper & Crispy Cheddar Melt",
                    recipeCategory = RecipeCategoryFilter.QUICK_EASY,
                    rescueScore = 92,
                    prepTimeMinutes = 12,
                    servings = 2,
                    caloriesPerServing = 420,
                    estimatedCostPerServing = 1.95,
                    applianceUsed = "Stovetop Skillet",
                    whyItRescues = "Uses up the cut red bell pepper half, baby spinach, basil pesto spread, and sharp white cheddar in 12 minutes with one pan.",
                    expiringIngredientsUsed = listOf(
                        "Roasted Red Bell Pepper Half",
                        "Baby Spinach Clamshell",
                        "Sharp Aged White Cheddar",
                        "Artisanal Basil Pesto Jar"
                    ),
                    pantryIngredientsUsed = listOf("Sun-Dried Tomato Paste Tube (1 tsp)"),
                    spicesUsed = listOf("Smoked Paprika", "Crushed Red Pepper", "Extra Virgin Olive Oil"),
                    steps = listOf(
                        "Sauté sliced Roasted Red Bell Pepper and Baby Spinach in a Stovetop Skillet with Smoked Paprika and Crushed Red Pepper for 3 minutes until moisture evaporates.",
                        "Spread Artisanal Basil Pesto and a touch of Sun-Dried Tomato Paste over your base, pile on Sharp Aged White Cheddar and the warm greens, and crisp both sides for 3 minutes until melted."
                    ),
                    chefTip = "Cooking the moisture out of wilting greens first keeps the melted cheddar crisp instead of soggy."
                ),
                RecipeSuggestion(
                    id = "starter-3",
                    rank = 3,
                    title = "Rustic Tomato-Pesto Pan Shakshuka Bake",
                    recipeCategory = RecipeCategoryFilter.BUDGET_FRIENDLY,
                    rescueScore = 88,
                    prepTimeMinutes = 15,
                    servings = 3,
                    caloriesPerServing = 340,
                    estimatedCostPerServing = 1.45,
                    applianceUsed = "Air Fryer",
                    whyItRescues = "Stretches leftover cherry tomatoes, sun-dried tomato paste, and cheese ends into a filling $1.45/serving meal.",
                    expiringIngredientsUsed = listOf(
                        "Heirloom Cherry Tomatoes",
                        "Artisanal Basil Pesto Jar",
                        "Sharp Aged White Cheddar",
                        "Roasted Red Bell Pepper Half"
                    ),
                    pantryIngredientsUsed = listOf(
                        "Pasture-Raised Eggs (3 eggs)",
                        "Sun-Dried Tomato Paste Tube (1 tbsp)"
                    ),
                    spicesUsed = listOf("Italian Herbs", "Roasted Garlic Powder", "Fresh Black Pepper"),
                    steps = listOf(
                        "Simmer halved Heirloom Cherry Tomatoes, diced Red Bell Pepper, 1 tbsp Sun-Dried Tomato Paste, and 1/3 cup water with Italian Herbs and Roasted Garlic Powder to form a rich zero-waste sauce.",
                        "Crack 3 Pasture-Raised Eggs over the sauce, top with crumbled Sharp Aged White Cheddar and a swirl of Basil Pesto, and Air Fry at 360°F for 8 minutes until whites are set."
                    ),
                    chefTip = "A spoonful of concentrated tomato paste plus water turns a handful of aging cherry tomatoes into a full pan of shakshuka sauce for pennies."
                )
            )
            dao.insertSavedRecipes(starterRecipes.map { it.toEntity() })

            dao.insertRestockItem(
                RestockItemEntity(
                    name = "Greek Yogurt (Plain)",
                    category = PantryCategory.DAIRY_CHEESE.name,
                    suggestedQuantity = "16 oz tub",
                    defaultExpiryDays = 10,
                    zeroWasteBuyingTip = "Doubles as sour cream, mayo base, and flatbread dough—zero leftover waste.",
                    estimatedCostDollars = 4.29,
                    triggerReason = "Auto-Added: Consumed Staple",
                    isAutoAdded = true
                )
            )

            // Automatically evaluate seeded items against low-stock threshold
            syncLowStockToShoppingList()
            PantryPulseWidgetProvider.updateAllWidgets(appContext)
        } else {
            syncLowStockToShoppingList()
        }
    }

    /**
     * Automatically checks all active inventory items against their low-stock threshold
     * (or global threshold override) and adds any low-stock items to the Smart Shopping List
     * if they aren't already listed.
     *
     * @return List of item names that were newly auto-added to the Smart Shopping List.
     */
    suspend fun syncLowStockToShoppingList(thresholdOverride: Int? = null): List<String> {
        val currentSetup = kitchenSetupFlow.first()
        val globalThreshold = thresholdOverride ?: currentSetup.lowStockThresholdPercent
        val activeItems = dao.getActiveItemsSnapshot()
        val existingShoppingItems = dao.getRestockItemsSnapshot()
        val newlyAddedNames = mutableListOf<String>()

        for (item in activeItems) {
            if (!item.autoAddToShoppingList) continue
            val effectiveThreshold = maxOf(item.lowStockThresholdPercent, globalThreshold)
            if (item.stockPercent <= effectiveThreshold) {
                val existingMatch = existingShoppingItems.firstOrNull { restock ->
                    (restock.sourcePantryItemId != null && restock.sourcePantryItemId == item.id) ||
                        restock.name.equals(item.name, ignoreCase = true)
                }
                val reason = "Auto-Added • Stock at ${item.stockPercent}% (≤$effectiveThreshold% threshold)"
                if (existingMatch == null) {
                    val cat = item.parsedCategory
                    dao.insertRestockItem(
                        RestockItemEntity(
                            name = item.name,
                            category = item.category,
                            suggestedQuantity = defaultReplenishQuantityFor(cat, item.quantity),
                            defaultExpiryDays = when (cat) {
                                PantryCategory.PRODUCE -> 6
                                PantryCategory.DAIRY_CHEESE -> 10
                                PantryCategory.SAUCES_JARS -> 14
                                PantryCategory.PROTEIN -> 7
                                else -> 7
                            },
                            zeroWasteBuyingTip = item.storageTip.ifBlank {
                                "Buy smaller packs or freeze half on day 1 to prevent spoilage."
                            },
                            estimatedCostDollars = item.estimatedValueDollars,
                            triggerReason = reason,
                            isAutoAdded = true,
                            isChecked = false,
                            sourcePantryItemId = item.id
                        )
                    )
                    newlyAddedNames.add(item.name)
                } else if (existingMatch.isAutoAdded && existingMatch.triggerReason != reason) {
                    dao.updateRestockItem(existingMatch.copy(triggerReason = reason))
                }
            }
        }
        return newlyAddedNames
    }

    suspend fun addItem(item: PantryItemEntity): List<String> {
        dao.insertItem(item)
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun addDetectedIngredients(detected: List<DetectedIngredient>): List<String> {
        val currentSetup = kitchenSetupFlow.first()
        val entities = detected.map { d ->
            PantryItemEntity(
                name = d.name,
                category = d.category.name,
                quantity = d.quantity,
                daysUntilExpiry = d.daysUntilExpiry,
                visualConditionNote = d.visualConditionNote,
                storageTip = d.storageTip,
                estimatedValueDollars = d.estimatedValueDollars,
                stockPercent = inferStockPercentFromQuantity(d.quantity),
                lowStockThresholdPercent = currentSetup.lowStockThresholdPercent
            )
        }
        dao.insertItems(entities)
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun adjustExpiryDays(item: PantryItemEntity, delta: Int) {
        val updated = item.copy(daysUntilExpiry = (item.daysUntilExpiry + delta).coerceIn(0, 60))
        dao.updateItem(updated)
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    /**
     * Applies a personalized zero-waste preservation method (e.g., Blanch & Ice Shock,
     * Oil/Pesto Cube, Quick Pickle, Freeze & Lock) to an inventory item, extending its
     * shelf-life countdown and updating its storage tip and condition note.
     */
    suspend fun applyPreservationGuide(item: PantryItemEntity, guide: PreservationGuide): PantryItemEntity {
        val newDays = (item.daysUntilExpiry + guide.extendedShelfLifeDays).coerceIn(3, 90)
        val updated = item.copy(
            daysUntilExpiry = newDays,
            visualConditionNote = "Preserved via ${guide.methodType.badgeEmoji} ${guide.methodType.label} (+${guide.extendedShelfLifeDays}d shelf life)",
            storageTip = "${guide.personalizedStorageTip} • Best Use: ${guide.bestUseAfterPreserving}"
        )
        dao.updateItem(updated)
        appContext.kitchenDataStore.edit { prefs ->
            val currentSaved = prefs[savedDollarsKey] ?: 38.50
            prefs[savedDollarsKey] = currentSaved + guide.estimatedSavingsDollars
        }
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return updated
    }

    /**
     * Adjusts an inventory item's remaining stock percentage (e.g. -25% or +25%).
     * If stock drops to 0%, marks the item consumed and adds it to the Smart Shopping List.
     * If stock drops to or below the low-stock threshold, automatically adds it to the Smart Shopping List!
     */
    suspend fun adjustItemStockPercent(item: PantryItemEntity, deltaPercent: Int): List<String> {
        val newStock = (item.stockPercent + deltaPercent).coerceIn(0, 100)
        if (newStock == 0) {
            consumeItem(item)
            return listOf(item.name)
        }
        val updatedQuantityLabel = when {
            newStock <= 25 -> "Low (~$newStock% left)"
            newStock <= 50 -> "Half (~$newStock% left)"
            newStock >= 95 -> "Full pack (100%)"
            else -> item.quantity
        }
        val updated = item.copy(
            stockPercent = newStock,
            quantity = updatedQuantityLabel
        )
        dao.updateItem(updated)
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun setGlobalLowStockThreshold(newThresholdPercent: Int): List<String> {
        val clamped = newThresholdPercent.coerceIn(10, 80)
        appContext.kitchenDataStore.edit { prefs ->
            prefs[lowStockThresholdKey] = clamped
        }
        // Update active items so their threshold aligns with the new global threshold
        val active = dao.getActiveItemsSnapshot()
        for (item in active) {
            dao.updateItem(item.copy(lowStockThresholdPercent = clamped))
        }
        return syncLowStockToShoppingList(thresholdOverride = clamped)
    }

    suspend fun updateItemDetails(item: PantryItemEntity): List<String> {
        dao.updateItem(item)
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun consumeItem(item: PantryItemEntity) {
        dao.markConsumed(item.id)
        val existingShoppingItems = dao.getRestockItemsSnapshot()
        val alreadyListed = existingShoppingItems.firstOrNull {
            (it.sourcePantryItemId != null && it.sourcePantryItemId == item.id) ||
                it.name.equals(item.name, ignoreCase = true)
        }
        if (alreadyListed == null) {
            dao.insertRestockItem(
                RestockItemEntity(
                    name = item.name,
                    category = item.category,
                    suggestedQuantity = defaultReplenishQuantityFor(item.parsedCategory, item.quantity),
                    defaultExpiryDays = when (item.parsedCategory) {
                        PantryCategory.PRODUCE -> 6
                        PantryCategory.DAIRY_CHEESE -> 10
                        PantryCategory.SAUCES_JARS -> 14
                        PantryCategory.PROTEIN -> 7
                        else -> 7
                    },
                    zeroWasteBuyingTip = item.storageTip.ifBlank { "Buy smaller packs or freeze half on day 1." },
                    estimatedCostDollars = item.estimatedValueDollars,
                    triggerReason = "Auto-Added • Finished / 0% Stock",
                    isAutoAdded = true,
                    isChecked = false,
                    sourcePantryItemId = item.id
                )
            )
        } else {
            dao.updateRestockItem(
                alreadyListed.copy(
                    triggerReason = "Auto-Added • Finished / 0% Stock",
                    isAutoAdded = true
                )
            )
        }
        appContext.kitchenDataStore.edit { prefs ->
            val currentSaved = prefs[savedDollarsKey] ?: 38.50
            prefs[savedDollarsKey] = currentSaved + item.estimatedValueDollars
        }
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun toggleShoppingItemChecked(item: RestockItemEntity) {
        dao.updateRestockItem(item.copy(isChecked = !item.isChecked))
    }

    suspend fun addManualShoppingItem(
        name: String,
        category: PantryCategory,
        suggestedQuantity: String,
        estimatedCostDollars: Double,
        buyingTip: String
    ) {
        if (name.isBlank()) return
        dao.insertRestockItem(
            RestockItemEntity(
                name = name.trim(),
                category = category.name,
                suggestedQuantity = suggestedQuantity.ifBlank { "1 pack" }.trim(),
                defaultExpiryDays = when (category) {
                    PantryCategory.PRODUCE -> 6
                    PantryCategory.DAIRY_CHEESE -> 10
                    PantryCategory.SAUCES_JARS -> 14
                    PantryCategory.PROTEIN -> 7
                    else -> 7
                },
                zeroWasteBuyingTip = buyingTip.ifBlank { "Check fridge before buying duplicate packs." }.trim(),
                estimatedCostDollars = estimatedCostDollars.coerceIn(0.50, 50.0),
                triggerReason = "Manual Shopping List Addition",
                isAutoAdded = false,
                isChecked = false
            )
        )
    }

    suspend fun restockItemToFridge(restockItem: RestockItemEntity) {
        dao.deleteRestockItemById(restockItem.id)
        val existingSource = restockItem.sourcePantryItemId?.let { dao.getItemById(it) }
            ?: dao.getActiveItemsSnapshot().firstOrNull { it.name.equals(restockItem.name, ignoreCase = true) }

        if (existingSource != null && !existingSource.isConsumed) {
            dao.updateItem(
                existingSource.copy(
                    quantity = restockItem.suggestedQuantity,
                    daysUntilExpiry = restockItem.defaultExpiryDays,
                    visualConditionNote = "Restocked to 100% from Smart Shopping List",
                    stockPercent = 100
                )
            )
        } else {
            dao.insertItem(
                PantryItemEntity(
                    name = restockItem.name,
                    category = restockItem.category,
                    quantity = restockItem.suggestedQuantity,
                    daysUntilExpiry = restockItem.defaultExpiryDays,
                    visualConditionNote = "Freshly restocked (100%) from Smart Shopping List",
                    storageTip = restockItem.zeroWasteBuyingTip,
                    estimatedValueDollars = restockItem.estimatedCostDollars,
                    stockPercent = 100
                )
            )
        }
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun restockAllCheckedItems(): Int {
        val checked = dao.getRestockItemsSnapshot().filter { it.isChecked }
        val targets = checked.ifEmpty { dao.getRestockItemsSnapshot() }
        for (item in targets) {
            restockItemToFridge(item)
        }
        return targets.size
    }

    suspend fun deleteRestockItem(id: Long) {
        dao.deleteRestockItemById(id)
    }

    suspend fun deleteItem(id: Long) {
        dao.deleteItemById(id)
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun saveGeneratedRecipes(recipes: List<RecipeSuggestion>) {
        dao.clearSavedRecipes()
        dao.insertSavedRecipes(recipes.map { it.toEntity() })
    }

    suspend fun completeRescueMeal(recipe: RecipeSuggestion, activeItems: List<PantryItemEntity>): List<String> {
        var rescuedValue = 0.0
        val matchedItems = activeItems.filter { item ->
            recipe.expiringIngredientsUsed.any { used ->
                used.contains(item.name, ignoreCase = true) ||
                    item.name.split(" ").any { word -> word.length > 3 && used.contains(word, ignoreCase = true) }
            }
        }
        val toConsume = if (matchedItems.isNotEmpty()) matchedItems else activeItems.filter { it.daysUntilExpiry <= 3 }.take(2)
        toConsume.forEach { item ->
            // Reduce stock by 50% or consume if already low
            val nextStock = (item.stockPercent - 55).coerceAtLeast(0)
            if (nextStock == 0) {
                consumeItem(item)
            } else {
                dao.updateItem(
                    item.copy(
                        stockPercent = nextStock,
                        quantity = "Low (~$nextStock% left after cooking)"
                    )
                )
            }
            rescuedValue += item.estimatedValueDollars
        }
        if (rescuedValue == 0.0) rescuedValue = 6.50

        appContext.kitchenDataStore.edit { prefs ->
            val meals = prefs[rescuedMealsKey] ?: 4
            val dollars = prefs[savedDollarsKey] ?: 38.50
            prefs[rescuedMealsKey] = meals + 1
            prefs[savedDollarsKey] = dollars + rescuedValue
        }
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun toggleAppliance(appliance: String) {
        appContext.kitchenDataStore.edit { prefs ->
            val current = (prefs[appliancesKey] ?: KitchenSetupState.DEFAULT_APPLIANCES).toMutableSet()
            if (current.contains(appliance)) {
                if (current.size > 1) current.remove(appliance)
            } else {
                current.add(appliance)
            }
            prefs[appliancesKey] = current
        }
    }

    suspend fun toggleSpice(spice: String) {
        appContext.kitchenDataStore.edit { prefs ->
            val current = (prefs[spicesKey] ?: KitchenSetupState.DEFAULT_SPICES).toMutableSet()
            if (current.contains(spice)) {
                if (current.size > 1) current.remove(spice)
            } else {
                current.add(spice)
            }
            prefs[spicesKey] = current
        }
    }
}
