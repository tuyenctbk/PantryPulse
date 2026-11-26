package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.FreshnessStatus
import com.example.data.PantryCategory
import com.example.data.PantryDao
import com.example.data.PantryDatabase
import com.example.data.PantryItemEntity
import com.example.data.PantryRepository
import com.example.data.RecipeSuggestion
import com.example.data.RestockItemEntity
import com.example.data.computeDaysUntilExpiry
import com.example.data.computeExpiryEpochMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PantryOfflineDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: PantryDatabase
    private lateinit var dao: PantryDao
    private lateinit var repository: PantryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PantryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pantryDao()
        repository = PantryRepository(context, dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `stores and queries ingredients by name quantity and expiry date offline`() = runTest {
        val inOneDay = computeExpiryEpochMillis(1)
        val inFiveDays = computeExpiryEpochMillis(5)
        val inTenDays = computeExpiryEpochMillis(10)

        dao.insertItems(
            listOf(
                PantryItemEntity(
                    name = "Aged Cheddar",
                    category = PantryCategory.DAIRY_CHEESE.name,
                    quantity = "8 oz block",
                    daysUntilExpiry = 10,
                    expiryDateEpochMillis = inTenDays,
                    visualConditionNote = "Fresh wrap",
                    storageTip = "Wrap in parchment",
                    stockPercent = 80
                ),
                PantryItemEntity(
                    name = "Baby Spinach",
                    category = PantryCategory.PRODUCE.name,
                    quantity = "5 oz bag",
                    daysUntilExpiry = 1,
                    expiryDateEpochMillis = inOneDay,
                    visualConditionNote = "Slightly wilting",
                    storageTip = "Add dry paper towel",
                    stockPercent = 25
                ),
                PantryItemEntity(
                    name = "Roma Tomatoes",
                    category = PantryCategory.PRODUCE.name,
                    quantity = "4 tomatoes",
                    daysUntilExpiry = 5,
                    expiryDateEpochMillis = inFiveDays,
                    visualConditionNote = "Ripe",
                    storageTip = "Store stem side down",
                    stockPercent = 60
                )
            )
        )

        // Active items must be ordered earliest expiryDateEpochMillis first
        val active = dao.getActiveItemsFlow().first()
        assertEquals(3, active.size)
        assertEquals("Baby Spinach", active[0].name)
        assertEquals("5 oz bag", active[0].quantity)
        assertEquals(inOneDay, active[0].expiryDateEpochMillis)
        assertEquals(1, active[0].effectiveDaysUntilExpiry)
        assertEquals(FreshnessStatus.CRITICAL, active[0].freshnessStatus)
        assertEquals("Roma Tomatoes", active[1].name)
        assertEquals("Aged Cheddar", active[2].name)

        // Expiring before 3 days query
        val expiringSoon = dao.getExpiringBeforeFlow(computeExpiryEpochMillis(3)).first()
        assertEquals(1, expiringSoon.size)
        assertEquals("Baby Spinach", expiringSoon.first().name)

        // Low-stock query
        val lowStock = dao.getLowStockItemsFlow(30).first()
        assertEquals(1, lowStock.size)
        assertEquals("Baby Spinach", lowStock.first().name)

        // Offline search query
        val searchResults = dao.searchActiveItemsFlow("cheddar").first()
        assertEquals(1, searchResults.size)
        assertEquals("Aged Cheddar", searchResults.first().name)
    }

    @Test
    fun `dynamic expiry countdown reconciles persisted calendar expiry dates`() = runTest {
        val targetExpiry = computeExpiryEpochMillis(2)
        val id = dao.insertItem(
            PantryItemEntity(
                name = "Fresh Cilantro",
                category = PantryCategory.PRODUCE.name,
                quantity = "1 bunch",
                daysUntilExpiry = 7, // stale static countdown
                expiryDateEpochMillis = targetExpiry, // real calendar date is 2 days away
                visualConditionNote = "Herb jar",
                storageTip = "Keep stems in water"
            )
        )

        val updatedCount = repository.refreshDynamicExpiryCountdowns()
        assertEquals(1, updatedCount)

        val reloaded = dao.getItemById(id)!!
        assertEquals(2, reloaded.daysUntilExpiry)
        assertEquals(2, reloaded.effectiveDaysUntilExpiry)
        assertEquals(computeDaysUntilExpiry(targetExpiry), reloaded.daysUntilExpiry)
    }

    @Test
    fun `restockAllCheckedItems returns zero when no items are checked`() = runTest {
        dao.insertRestockItem(
            RestockItemEntity(
                name = "Whole Milk",
                category = PantryCategory.DAIRY_CHEESE.name,
                suggestedQuantity = "1 gal",
                defaultExpiryDays = 7,
                zeroWasteBuyingTip = "Keep on interior shelf",
                isChecked = false
            )
        )

        val restockedCount = repository.restockAllCheckedItems()
        assertEquals(0, restockedCount)
        assertEquals(1, dao.getRestockItemsSnapshot().size)
    }

    @Test
    fun `completeRescueMeal does not double count saved dollars when ingredient finishes`() = runTest {
        val initialSetup = repository.kitchenSetupFlow.first()
        val initialSaved = initialSetup.savedDollarsTotal
        val initialMeals = initialSetup.rescuedMealsCount

        val item = PantryItemEntity(
            name = "Baby Spinach Clamshell",
            category = PantryCategory.PRODUCE.name,
            quantity = "1/2 box",
            daysUntilExpiry = 1,
            visualConditionNote = "Use today",
            storageTip = "Dry paper towel",
            estimatedValueDollars = 4.00,
            stockPercent = 40 // subtracting 55% drops to 0 -> triggers consumeItem
        )
        val insertedId = dao.insertItem(item)
        val persisted = dao.getItemById(insertedId)!!

        val recipe = RecipeSuggestion(
            id = "test-recipe-1",
            rank = 1,
            title = "Spinach Rescue Skillet",
            rescueScore = 95,
            prepTimeMinutes = 10,
            servings = 2,
            applianceUsed = "Stovetop Skillet",
            whyItRescues = "Uses wilting spinach",
            expiringIngredientsUsed = listOf("Baby Spinach Clamshell"),
            pantryIngredientsUsed = emptyList(),
            spicesUsed = listOf("Olive Oil"),
            steps = listOf("Sauté spinach"),
            chefTip = "Cook briefly"
        )

        repository.completeRescueMeal(recipe, listOf(persisted))

        val afterSetup = repository.kitchenSetupFlow.first()
        assertEquals(initialMeals + 1, afterSetup.rescuedMealsCount)
        // Must add $4.00 ONCE, not $8.00!
        assertEquals(initialSaved + 4.00, afterSetup.savedDollarsTotal, 0.001)
        assertTrue(dao.getItemById(insertedId)!!.isConsumed)
    }

    @Test
    fun `fresh startup inserts zero sample items recipes or restock entries`() = runTest {
        repository.syncOnStartup()

        assertEquals(0, dao.getActiveItemsFlow().first().size)
        assertEquals(0, dao.getSavedRecipesFlow().first().size)
        assertEquals(0, dao.getRestockItemsFlow().first().size)
        val defaultSetup = com.example.data.KitchenSetupState()
        assertEquals(0, defaultSetup.rescuedMealsCount)
        assertEquals(0.0, defaultSetup.savedDollarsTotal, 0.001)
    }

    @Test
    fun `addMissingStaplesToShoppingList deduplicates existing items case-insensitively`() = runTest {
        val firstAdded = repository.addMissingStaplesToShoppingList(
            staples = listOf("Garlic Cloves", "Lemon", "garlic cloves"),
            buyingTip = "Check pantry first"
        )
        assertEquals(2, firstAdded)
        assertEquals(2, dao.getRestockItemsSnapshot().size)

        val secondAdded = repository.addMissingStaplesToShoppingList(
            staples = listOf("GARLIC CLOVES", "Dry Pasta", "Lemon"),
            buyingTip = "Check pantry first"
        )
        assertEquals(1, secondAdded)
        assertEquals(3, dao.getRestockItemsSnapshot().size)
    }

    @Test
    fun `addDetectedIngredients preserves packaged barcode shelf life up to 365 days`() = runTest {
        repository.addDetectedIngredients(
            listOf(
                com.example.data.DetectedIngredient(
                    name = "Organic Chickpeas Can",
                    category = PantryCategory.PANTRY_STAPLE,
                    quantity = "400 g",
                    daysUntilExpiry = 180,
                    visualConditionNote = "Shelf-stable barcode item",
                    storageTip = "Store in cool dry pantry",
                    estimatedValueDollars = 1.99
                )
            )
        )
        val stored = dao.getActiveItemsSnapshot().single()
        assertEquals(180, stored.daysUntilExpiry)
        assertEquals(180, stored.effectiveDaysUntilExpiry)
    }
}
