package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.ai.BarcodeLookupSource
import com.example.ai.OfflineBarcodeCache
import com.example.data.remote.OpenFoodFactsRemoteDataSource
import com.example.widget.PantryPulseWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class BarcodeIngredientLookupResult(
    val barcode: String,
    val found: Boolean,
    val ingredient: Ingredient? = null,
    val detectedIngredient: DetectedIngredient? = null,
    val source: BarcodeLookupSource = BarcodeLookupSource.NOT_FOUND,
    val persistedRowId: Long? = null,
    val errorMessage: String? = null
)

private val Context.kitchenDataStore by preferencesDataStore(name = "kitchen_preferences")

data class KitchenSetupState(
    val ownedAppliances: Set<String> = DEFAULT_APPLIANCES,
    val ownedSpices: Set<String> = DEFAULT_SPICES,
    val rescuedMealsCount: Int = 0,
    val savedDollarsTotal: Double = 0.0,
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
    private val dao: PantryDao,
    private val ingredientDao: IngredientDao? = null,
    private val recipeDao: RecipeDao? = null,
    private val openFoodFactsRemoteDataSource: OpenFoodFactsRemoteDataSource = OpenFoodFactsRemoteDataSource()
) {
    private val appliancesKey = stringSetPreferencesKey("owned_appliances")
    private val spicesKey = stringSetPreferencesKey("owned_spices")
    private val rescuedMealsKey = intPreferencesKey("rescued_meals_count")
    private val savedDollarsKey = doublePreferencesKey("saved_dollars_total")
    private val lowStockThresholdKey = intPreferencesKey("low_stock_threshold_percent")
    private val barcodeCache by lazy { OfflineBarcodeCache(appContext.applicationContext) }

    val allIngredientsFlow: Flow<List<Ingredient>> =
        ingredientDao?.getAllIngredients() ?: emptyFlow()

    val allRecipesFlow: Flow<List<Recipe>> =
        recipeDao?.getAllRecipes() ?: emptyFlow()

    val favoriteRecipesFlow: Flow<List<Recipe>> =
        recipeDao?.getFavoriteRecipes() ?: emptyFlow()

    val recipesWithIngredientsFlow: Flow<List<RecipeWithIngredients>> =
        recipeDao?.getRecipesWithIngredients() ?: emptyFlow()

    val activeItemsFlow: Flow<List<PantryItemEntity>> = dao.getActiveItemsFlow()

    val savedRecipesFlow: Flow<List<RecipeSuggestion>> = dao.getSavedRecipesFlow().map { list ->
        list.map { it.toDomain() }
    }

    val restockItemsFlow: Flow<List<RestockItemEntity>> = dao.getRestockItemsFlow()

    val kitchenSetupFlow: Flow<KitchenSetupState> = appContext.kitchenDataStore.data.map { prefs ->
        KitchenSetupState(
            ownedAppliances = prefs[appliancesKey] ?: KitchenSetupState.DEFAULT_APPLIANCES,
            ownedSpices = prefs[spicesKey] ?: KitchenSetupState.DEFAULT_SPICES,
            rescuedMealsCount = prefs[rescuedMealsKey] ?: 0,
            savedDollarsTotal = prefs[savedDollarsKey] ?: 0.0,
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

        /**
         * Maps a product returned from Open Food Facts / offline barcode cache into a canonical
         * Room [Ingredient] entity (`name`, `quantity`, `unit`, `expiryDate`).
         */
        fun mapDetectedProductToIngredient(
            detected: DetectedIngredient,
            fromTimestampMillis: Long = System.currentTimeMillis()
        ): Ingredient {
            val (parsedQty, parsedUnit) = Ingredient.parseQuantityAndUnit(detected.quantity)
            val clampedDays = detected.daysUntilExpiry.coerceIn(1, 365)
            return Ingredient(
                name = detected.name.trim(),
                quantity = parsedQty.coerceAtLeast(0.01),
                unit = parsedUnit.ifBlank { "unit" },
                expiryDate = computeExpiryEpochMillis(clampedDays, fromTimestampMillis)
            )
        }
    }

    suspend fun syncOnStartup() {
        refreshDynamicExpiryCountdowns()
        syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    /**
     * Reconciles each active pantry item's integer daysUntilExpiry countdown with its
     * persisted calendar expiryDateEpochMillis so offline shelf-life countdowns advance
     * accurately across calendar days without network connectivity.
     */
    suspend fun refreshDynamicExpiryCountdowns(): Int {
        val active = dao.getActiveItemsSnapshot()
        val now = System.currentTimeMillis()
        var updatedCount = 0
        for (item in active) {
            val resolvedExpiryMillis = if (item.expiryDateEpochMillis > 0L) {
                item.expiryDateEpochMillis
            } else {
                computeExpiryEpochMillis(item.daysUntilExpiry, now)
            }
            val dynamicDays = computeDaysUntilExpiry(resolvedExpiryMillis, now).coerceAtLeast(0)
            if (item.expiryDateEpochMillis != resolvedExpiryMillis || item.daysUntilExpiry != dynamicDays) {
                dao.updateExpiryCountdown(
                    id = item.id,
                    daysUntilExpiry = dynamicDays,
                    expiryDateEpochMillis = resolvedExpiryMillis,
                    updatedTimestamp = now
                )
                ingredientDao?.insertIngredient(
                    Ingredient.fromPantryItemEntity(
                        item.copy(
                            daysUntilExpiry = dynamicDays,
                            expiryDateEpochMillis = resolvedExpiryMillis
                        )
                    )
                )
                updatedCount++
            } else if (ingredientDao != null && ingredientDao.getIngredientById(item.id) == null) {
                ingredientDao.insertIngredient(Ingredient.fromPantryItemEntity(item))
            }
        }
        if (updatedCount > 0) {
            PantryPulseWidgetProvider.updateAllWidgets(appContext)
        }
        return updatedCount
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
                val reason = appContext.getString(
                    R.string.repo_restock_low_stock_reason_fmt,
                    item.stockPercent,
                    effectiveThreshold
                )
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
                                appContext.getString(R.string.repo_default_buying_tip)
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
        val now = System.currentTimeMillis()
        val normalized = item.copy(
            expiryDateEpochMillis = if (item.expiryDateEpochMillis > 0L) {
                item.expiryDateEpochMillis
            } else {
                computeExpiryEpochMillis(item.daysUntilExpiry, now)
            },
            updatedTimestamp = now
        )
        val insertedId = dao.insertItem(normalized)
        ingredientDao?.insertIngredient(
            Ingredient.fromPantryItemEntity(normalized.copy(id = insertedId))
        )
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun insertIngredient(ingredient: Ingredient): Long {
        return ingredientDao?.insertIngredient(ingredient) ?: -1L
    }

    suspend fun updateIngredient(ingredient: Ingredient) {
        ingredientDao?.updateIngredient(ingredient)
    }

    /**
     * Fetches product metadata for an EAN-13 / UPC barcode using an offline-first Tier 0 chain
     * (Local DataStore cache -> Open Food Facts API v2 -> Built-in offline catalog),
     * auto-populates a Room [Ingredient] entity (`name`, `quantity`, `unit`, `expiryDate`),
     * and optionally inserts it into Room (`ingredients` and `pantry_items`).
     */
    suspend fun fetchProductAndPopulateIngredient(
        rawBarcode: String,
        persistToDatabase: Boolean = false
    ): BarcodeIngredientLookupResult {
        val barcode = OfflineBarcodeCache.normalizeBarcodeDigits(rawBarcode)
        if (!OfflineBarcodeCache.isValidGroceryBarcode(barcode)) {
            return BarcodeIngredientLookupResult(
                barcode = barcode,
                found = false,
                source = BarcodeLookupSource.NOT_FOUND,
                errorMessage = appContext.getString(R.string.tier0_barcode_invalid_error)
            )
        }

        // 1. Offline-first persistent DataStore cache lookup
        val cachedDetected = barcodeCache.getCachedIngredient(barcode)
        val resolvedPair: Pair<DetectedIngredient, BarcodeLookupSource> = if (cachedDetected != null) {
            cachedDetected to BarcodeLookupSource.OFFLINE_CACHE
        } else {
            // 2. Live Open Food Facts v2 API fetch via Retrofit/OkHttp/Gson with write-through caching
            val apiResult = openFoodFactsRemoteDataSource.fetchProductByBarcode(barcode)
            val apiDetected = apiResult.ingredient
            if (apiResult.found && apiDetected != null) {
                barcodeCache.putCachedIngredient(barcode, apiDetected)
                apiDetected to BarcodeLookupSource.OPEN_FOOD_FACTS_API
            } else {
                return BarcodeIngredientLookupResult(
                    barcode = barcode,
                    found = false,
                    source = BarcodeLookupSource.NOT_FOUND,
                    errorMessage = apiResult.errorMessage
                        ?: appContext.getString(R.string.tier0_barcode_not_found_fmt, barcode)
                )
            }
        }

        val (detected, source) = resolvedPair
        val now = System.currentTimeMillis()
        val populatedIngredient = mapDetectedProductToIngredient(detected, fromTimestampMillis = now)

        var insertedRowId: Long? = null
        if (persistToDatabase) {
            val currentSetup = kitchenSetupFlow.first()
            val clampedDays = detected.daysUntilExpiry.coerceIn(0, 365)
            val pantryEntity = PantryItemEntity(
                name = populatedIngredient.name,
                category = detected.category.name,
                quantity = detected.quantity.ifBlank { populatedIngredient.formattedQuantity }.trim(),
                daysUntilExpiry = clampedDays,
                expiryDateEpochMillis = populatedIngredient.expiryDate,
                visualConditionNote = detected.visualConditionNote.trim(),
                storageTip = detected.storageTip.trim(),
                estimatedValueDollars = detected.estimatedValueDollars.coerceIn(0.50, 100.0),
                stockPercent = inferStockPercentFromQuantity(detected.quantity),
                lowStockThresholdPercent = currentSetup.lowStockThresholdPercent,
                addedTimestamp = now,
                updatedTimestamp = now
            )
            val itemId = dao.insertItem(pantryEntity)
            val syncedIngredient = populatedIngredient.copy(id = itemId)
            ingredientDao?.insertIngredient(syncedIngredient)
            insertedRowId = itemId
            syncLowStockToShoppingList()
            PantryPulseWidgetProvider.updateAllWidgets(appContext)
        }

        return BarcodeIngredientLookupResult(
            barcode = barcode,
            found = true,
            ingredient = populatedIngredient.copy(id = insertedRowId ?: 0L),
            detectedIngredient = detected,
            source = source,
            persistedRowId = insertedRowId
        )
    }

    suspend fun addDetectedIngredients(detected: List<DetectedIngredient>): List<String> {
        val currentSetup = kitchenSetupFlow.first()
        val now = System.currentTimeMillis()
        val entities = detected.map { d ->
            val clampedDays = d.daysUntilExpiry.coerceIn(0, 90)
            PantryItemEntity(
                name = d.name.trim(),
                category = d.category.name,
                quantity = d.quantity.ifBlank { "1 unit" }.trim(),
                daysUntilExpiry = clampedDays,
                expiryDateEpochMillis = computeExpiryEpochMillis(clampedDays, now),
                visualConditionNote = d.visualConditionNote.trim(),
                storageTip = d.storageTip.trim(),
                estimatedValueDollars = d.estimatedValueDollars.coerceIn(0.50, 100.0),
                stockPercent = inferStockPercentFromQuantity(d.quantity),
                lowStockThresholdPercent = currentSetup.lowStockThresholdPercent,
                addedTimestamp = now,
                updatedTimestamp = now
            )
        }
        entities.forEach { entity ->
            val id = dao.insertItem(entity)
            ingredientDao?.insertIngredient(Ingredient.fromPantryItemEntity(entity.copy(id = id)))
        }
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun adjustExpiryDays(item: PantryItemEntity, delta: Int) {
        val now = System.currentTimeMillis()
        val newDays = (item.effectiveDaysUntilExpiry + delta).coerceIn(0, 90)
        val updated = item.copy(
            daysUntilExpiry = newDays,
            expiryDateEpochMillis = computeExpiryEpochMillis(newDays, now),
            updatedTimestamp = now
        )
        dao.updateItem(updated)
        ingredientDao?.insertIngredient(Ingredient.fromPantryItemEntity(updated))
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    /**
     * Applies a personalized zero-waste preservation method (e.g., Blanch & Ice Shock,
     * Oil/Pesto Cube, Quick Pickle, Freeze & Lock) to an inventory item, extending its
     * shelf-life countdown and updating its calendar expiry date, storage tip, and condition note.
     */
    suspend fun applyPreservationGuide(item: PantryItemEntity, guide: PreservationGuide): PantryItemEntity {
        val now = System.currentTimeMillis()
        val newDays = (item.effectiveDaysUntilExpiry + guide.extendedShelfLifeDays).coerceIn(3, 90)
        val updated = item.copy(
            daysUntilExpiry = newDays,
            expiryDateEpochMillis = computeExpiryEpochMillis(newDays, now),
            visualConditionNote = appContext.getString(
                R.string.repo_preserved_note_fmt,
                guide.methodType.badgeEmoji,
                appContext.getString(guide.methodType.labelRes),
                guide.extendedShelfLifeDays
            ),
            storageTip = appContext.getString(
                R.string.repo_best_use_fmt,
                guide.personalizedStorageTip,
                guide.bestUseAfterPreserving
            ),
            updatedTimestamp = now
        )
        dao.updateItem(updated)
        ingredientDao?.insertIngredient(Ingredient.fromPantryItemEntity(updated))
        appContext.kitchenDataStore.edit { prefs ->
            val currentSaved = prefs[savedDollarsKey] ?: 0.0
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
            newStock <= 25 -> appContext.getString(R.string.repo_stock_low_fmt, newStock)
            newStock <= 50 -> appContext.getString(R.string.repo_stock_half_fmt, newStock)
            newStock >= 95 -> appContext.getString(R.string.repo_stock_full)
            else -> item.quantity
        }
        val updated = item.copy(
            stockPercent = newStock,
            quantity = updatedQuantityLabel,
            updatedTimestamp = System.currentTimeMillis()
        )
        dao.updateItem(updated)
        ingredientDao?.insertIngredient(Ingredient.fromPantryItemEntity(updated))
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun setGlobalLowStockThreshold(newThresholdPercent: Int): List<String> {
        val clamped = newThresholdPercent.coerceIn(10, 80)
        appContext.kitchenDataStore.edit { prefs ->
            prefs[lowStockThresholdKey] = clamped
        }
        val now = System.currentTimeMillis()
        val active = dao.getActiveItemsSnapshot()
        for (item in active) {
            dao.updateItem(item.copy(lowStockThresholdPercent = clamped, updatedTimestamp = now))
        }
        return syncLowStockToShoppingList(thresholdOverride = clamped)
    }

    suspend fun updateItemDetails(item: PantryItemEntity): List<String> {
        val now = System.currentTimeMillis()
        val clampedDays = item.daysUntilExpiry.coerceIn(0, 90)
        val synced = item.copy(
            daysUntilExpiry = clampedDays,
            expiryDateEpochMillis = computeExpiryEpochMillis(clampedDays, now),
            updatedTimestamp = now
        )
        dao.updateItem(synced)
        ingredientDao?.insertIngredient(Ingredient.fromPantryItemEntity(synced))
        val autoAdded = syncLowStockToShoppingList()
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
        return autoAdded
    }

    suspend fun consumeItem(item: PantryItemEntity, recordSavings: Boolean = true) {
        dao.markConsumed(item.id)
        ingredientDao?.deleteIngredientById(item.id)
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
                    triggerReason = appContext.getString(R.string.repo_restock_finished_reason),
                    isAutoAdded = true,
                    isChecked = false,
                    sourcePantryItemId = item.id
                )
            )
        } else {
            dao.updateRestockItem(
                alreadyListed.copy(
                    triggerReason = appContext.getString(R.string.repo_restock_finished_reason),
                    isAutoAdded = true
                )
            )
        }
        if (recordSavings) {
            appContext.kitchenDataStore.edit { prefs ->
                val currentSaved = prefs[savedDollarsKey] ?: 0.0
                prefs[savedDollarsKey] = currentSaved + item.estimatedValueDollars
            }
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
                estimatedCostDollars = estimatedCostDollars.coerceIn(0.0, 500.0),
                triggerReason = appContext.getString(R.string.repo_restock_manual_reason),
                isAutoAdded = false,
                isChecked = false
            )
        )
    }

    suspend fun movePantryItemToShoppingList(pantryItem: PantryItemEntity): Boolean {
        val existing = dao.getRestockItemsSnapshot().firstOrNull { restock ->
            (restock.sourcePantryItemId != null && restock.sourcePantryItemId == pantryItem.id) ||
                restock.name.equals(pantryItem.name, ignoreCase = true)
        }
        if (existing == null) {
            val cat = pantryItem.parsedCategory
            dao.insertRestockItem(
                RestockItemEntity(
                    name = pantryItem.name,
                    category = pantryItem.category,
                    suggestedQuantity = defaultReplenishQuantityFor(cat, pantryItem.quantity),
                    defaultExpiryDays = when (cat) {
                        PantryCategory.PRODUCE -> 6
                        PantryCategory.DAIRY_CHEESE -> 10
                        PantryCategory.SAUCES_JARS -> 14
                        PantryCategory.PROTEIN -> 7
                        else -> 7
                    },
                    zeroWasteBuyingTip = pantryItem.storageTip.ifBlank {
                        appContext.getString(R.string.shopping_default_tip)
                    },
                    estimatedCostDollars = pantryItem.estimatedValueDollars.coerceAtLeast(0.50),
                    triggerReason = appContext.getString(R.string.repo_restock_moved_reason_fmt, pantryItem.stockPercent),
                    isAutoAdded = true,
                    isChecked = false,
                    sourcePantryItemId = pantryItem.id
                )
            )
            return true
        }
        return false
    }

    suspend fun moveAllLowSupplyItemsToShoppingList(): Int {
        val currentSetup = kitchenSetupFlow.first()
        val threshold = currentSetup.lowStockThresholdPercent
        val lowStockItems = dao.getActiveItemsSnapshot().filter { it.stockPercent <= threshold }
        var addedCount = 0
        for (item in lowStockItems) {
            if (movePantryItemToShoppingList(item)) {
                addedCount++
            }
        }
        return addedCount
    }

    suspend fun restockItemToFridge(restockItem: RestockItemEntity) {
        dao.deleteRestockItemById(restockItem.id)
        val now = System.currentTimeMillis()
        val freshExpiryMillis = computeExpiryEpochMillis(restockItem.defaultExpiryDays, now)
        val existingSource = restockItem.sourcePantryItemId?.let { dao.getItemById(it) }
            ?: dao.getActiveItemByNameIgnoreCase(restockItem.name)

        if (existingSource != null && !existingSource.isConsumed) {
            val restocked = existingSource.copy(
                quantity = restockItem.suggestedQuantity,
                daysUntilExpiry = restockItem.defaultExpiryDays,
                expiryDateEpochMillis = freshExpiryMillis,
                visualConditionNote = appContext.getString(R.string.repo_restocked_100_note),
                stockPercent = 100,
                updatedTimestamp = now
            )
            dao.updateItem(restocked)
            ingredientDao?.insertIngredient(Ingredient.fromPantryItemEntity(restocked))
        } else {
            val freshEntity = PantryItemEntity(
                name = restockItem.name,
                category = restockItem.category,
                quantity = restockItem.suggestedQuantity,
                daysUntilExpiry = restockItem.defaultExpiryDays,
                expiryDateEpochMillis = freshExpiryMillis,
                visualConditionNote = appContext.getString(R.string.repo_freshly_restocked_note),
                storageTip = restockItem.zeroWasteBuyingTip,
                estimatedValueDollars = restockItem.estimatedCostDollars,
                stockPercent = 100,
                addedTimestamp = now,
                updatedTimestamp = now
            )
            val newId = dao.insertItem(freshEntity)
            ingredientDao?.insertIngredient(Ingredient.fromPantryItemEntity(freshEntity.copy(id = newId)))
        }
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun restockAllCheckedItems(): Int {
        val checked = dao.getRestockItemsSnapshot().filter { it.isChecked }
        if (checked.isEmpty()) return 0
        for (item in checked) {
            restockItemToFridge(item)
        }
        return checked.size
    }

    suspend fun deleteRestockItem(id: Long) {
        dao.deleteRestockItemById(id)
    }

    suspend fun deleteIngredient(ingredient: Ingredient) {
        ingredientDao?.deleteIngredient(ingredient)
        dao.deleteItemById(ingredient.id)
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun deleteItem(id: Long) {
        dao.deleteItemById(id)
        ingredientDao?.deleteIngredientById(id)
        PantryPulseWidgetProvider.updateAllWidgets(appContext)
    }

    suspend fun insertRecipe(recipe: Recipe, linkedIngredientIds: List<Long> = emptyList()): Long {
        val rDao = recipeDao ?: return -1L
        val insertedId = rDao.insertRecipe(recipe)
        if (linkedIngredientIds.isNotEmpty()) {
            rDao.insertRecipeIngredientCrossRefs(
                linkedIngredientIds.distinct().map { ingId ->
                    RecipeIngredientCrossRef(recipeId = insertedId, ingredientId = ingId)
                }
            )
        }
        return insertedId
    }

    suspend fun toggleRecipeFavorite(recipeId: Long, isFavorite: Boolean) {
        recipeDao?.setRecipeFavorite(recipeId, isFavorite)
    }

    suspend fun deleteRecipe(recipeId: Long) {
        recipeDao?.deleteRecipeById(recipeId)
    }

    suspend fun saveGeneratedRecipes(recipes: List<RecipeSuggestion>) {
        dao.clearSavedRecipes()
        dao.insertSavedRecipes(recipes.map { it.toEntity() })

        val rDao = recipeDao ?: return
        rDao.deleteNonFavoriteRecipes()
        val storedIngredients = ingredientDao?.getAllIngredientsSnapshot().orEmpty()
        recipes.forEach { suggestion ->
            val existing = rDao.getRecipeByExternalId(suggestion.id)
            val entity = Recipe.fromRecipeSuggestion(
                suggestion = suggestion,
                id = existing?.id ?: 0L,
                isFavorite = existing?.isFavorite ?: false
            )
            val recipeId = rDao.insertRecipe(entity)
            if (storedIngredients.isNotEmpty()) {
                val matchedIds = storedIngredients
                    .filter { ing -> suggestion.matchesAvailableIngredient(ing.name) }
                    .map { it.id }
                if (matchedIds.isNotEmpty()) {
                    rDao.deleteCrossRefsForRecipe(recipeId)
                    rDao.insertRecipeIngredientCrossRefs(
                        matchedIds.map { ingId ->
                            RecipeIngredientCrossRef(recipeId = recipeId, ingredientId = ingId)
                        }
                    )
                }
            }
        }
    }

    suspend fun completeRescueMeal(recipe: RecipeSuggestion, activeItems: List<PantryItemEntity>): List<String> {
        var rescuedValue = 0.0
        val now = System.currentTimeMillis()
        val matchedItems = activeItems.filter { item ->
            recipe.expiringIngredientsUsed.any { used ->
                used.contains(item.name, ignoreCase = true) ||
                    item.name.split(" ").any { word -> word.length > 3 && used.contains(word, ignoreCase = true) }
            }
        }
        val toConsume = if (matchedItems.isNotEmpty()) {
            matchedItems
        } else {
            activeItems.filter { it.effectiveDaysUntilExpiry <= 3 }.take(2)
        }
        toConsume.forEach { item ->
            val nextStock = (item.stockPercent - 55).coerceAtLeast(0)
            if (nextStock == 0) {
                // Pass recordSavings = false so savings are tallied exactly once below (fixes double-counting bug)
                consumeItem(item, recordSavings = false)
                rescuedValue += item.estimatedValueDollars
            } else {
                val updatedItem = item.copy(
                    stockPercent = nextStock,
                    quantity = appContext.getString(R.string.repo_stock_after_cooking_fmt, nextStock),
                    updatedTimestamp = now
                )
                dao.updateItem(updatedItem)
                ingredientDao?.insertIngredient(Ingredient.fromPantryItemEntity(updatedItem))
                rescuedValue += (item.estimatedValueDollars * 0.55)
            }
        }
        appContext.kitchenDataStore.edit { prefs ->
            val meals = prefs[rescuedMealsKey] ?: 0
            val dollars = prefs[savedDollarsKey] ?: 0.0
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
