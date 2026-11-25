package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.FreshnessStatus
import com.example.data.Ingredient
import com.example.data.IngredientDao
import com.example.data.PantryCategory
import com.example.data.PantryDatabase
import com.example.data.PantryItemEntity
import com.example.data.computeExpiryEpochMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IngredientDaoTest {

    private lateinit var db: PantryDatabase
    private lateinit var ingredientDao: IngredientDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PantryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ingredientDao = db.ingredientDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryAllIngredients_ordersByEarliestExpiryDate() = runTest {
        val spinachExpiry = computeExpiryEpochMillis(2)
        val yogurtExpiry = computeExpiryEpochMillis(9)
        val berriesExpiry = computeExpiryEpochMillis(1)

        val spinachId = ingredientDao.insertIngredient(
            Ingredient(
                name = "Baby Spinach",
                quantity = 5.0,
                unit = "oz",
                expiryDate = spinachExpiry
            )
        )
        val yogurtId = ingredientDao.insertIngredient(
            Ingredient(
                name = "Greek Yogurt",
                quantity = 16.0,
                unit = "oz",
                expiryDate = yogurtExpiry
            )
        )
        val berriesId = ingredientDao.insertIngredient(
            Ingredient(
                name = "Strawberries",
                quantity = 1.0,
                unit = "pint",
                expiryDate = berriesExpiry
            )
        )

        assertTrue(spinachId > 0L)
        assertTrue(yogurtId > 0L)
        assertTrue(berriesId > 0L)

        val all = ingredientDao.getAllIngredients().first()
        assertEquals(3, all.size)
        assertEquals("Strawberries", all[0].name)
        assertEquals(1.0, all[0].quantity, 0.001)
        assertEquals("pint", all[0].unit)
        assertEquals("1 pint", all[0].formattedQuantity)
        assertEquals(berriesExpiry, all[0].expiryDate)
        assertEquals(FreshnessStatus.CRITICAL, all[0].freshnessStatus)

        assertEquals("Baby Spinach", all[1].name)
        assertEquals("Greek Yogurt", all[2].name)
    }

    @Test
    fun updateIngredient_persistsQuantityUnitAndExpiryChanges() = runTest {
        val initialExpiry = computeExpiryEpochMillis(7)
        val updatedExpiry = computeExpiryEpochMillis(3)

        val id = ingredientDao.insertIngredient(
            Ingredient(
                name = "Cheddar Cheese",
                quantity = 8.0,
                unit = "oz",
                expiryDate = initialExpiry
            )
        )

        val original = ingredientDao.getIngredientById(id)
        assertNotNull(original)

        val updated = original!!.copy(
            quantity = 3.5,
            unit = "oz wedge",
            expiryDate = updatedExpiry
        )
        ingredientDao.updateIngredient(updated)

        val reloaded = ingredientDao.observeIngredientById(id).first()
        assertNotNull(reloaded)
        assertEquals(3.5, reloaded!!.quantity, 0.001)
        assertEquals("oz wedge", reloaded.unit)
        assertEquals("3.5 oz wedge", reloaded.formattedQuantity)
        assertEquals(updatedExpiry, reloaded.expiryDate)
    }

    @Test
    fun deleteIngredientByEntityAndById_removesRowsCleanly() = runTest {
        val ids = ingredientDao.insertIngredients(
            listOf(
                Ingredient(
                    name = "Basil",
                    quantity = 1.0,
                    unit = "bunch",
                    expiryDate = computeExpiryEpochMillis(2)
                ),
                Ingredient(
                    name = "Whole Milk",
                    quantity = 0.5,
                    unit = "gallon",
                    expiryDate = computeExpiryEpochMillis(5)
                )
            )
        )
        assertEquals(2, ids.size)

        val basil = ingredientDao.getIngredientById(ids[0])!!
        ingredientDao.deleteIngredient(basil)

        assertNull(ingredientDao.getIngredientById(ids[0]))
        val remaining = ingredientDao.getAllIngredients().first()
        assertEquals(1, remaining.size)
        assertEquals("Whole Milk", remaining.first().name)

        ingredientDao.deleteIngredientById(ids[1])
        assertTrue(ingredientDao.getAllIngredients().first().isEmpty())
    }

    @Test
    fun searchAndExpiringBeforeQueries_filterCorrectly() = runTest {
        ingredientDao.insertIngredients(
            listOf(
                Ingredient(
                    name = "Avocado",
                    quantity = 2.0,
                    unit = "pcs",
                    expiryDate = computeExpiryEpochMillis(1)
                ),
                Ingredient(
                    name = "Oat Milk",
                    quantity = 1.0,
                    unit = "carton",
                    expiryDate = computeExpiryEpochMillis(10)
                )
            )
        )

        val expiringSoon = ingredientDao.getExpiringIngredientsBefore(computeExpiryEpochMillis(3)).first()
        assertEquals(1, expiringSoon.size)
        assertEquals("Avocado", expiringSoon.first().name)

        val searchByName = ingredientDao.searchIngredients("avoc").first()
        assertEquals(1, searchByName.size)
        assertEquals("Avocado", searchByName.first().name)

        val searchByUnit = ingredientDao.searchIngredients("carton").first()
        assertEquals(1, searchByUnit.size)
        assertEquals("Oat Milk", searchByUnit.first().name)

        ingredientDao.deleteAllIngredients()
        assertTrue(ingredientDao.getAllIngredients().first().isEmpty())
    }

    @Test
    fun parseQuantityAndUnit_extractsFractionsDecimalsAndPlainUnits() {
        val (fracQty, fracUnit) = Ingredient.parseQuantityAndUnit("1/2 box")
        assertEquals(0.5, fracQty, 0.001)
        assertEquals("box", fracUnit)

        val (numQty, numUnit) = Ingredient.parseQuantityAndUnit("5 oz wedge")
        assertEquals(5.0, numQty, 0.001)
        assertEquals("oz wedge", numUnit)

        val (decQty, decUnit) = Ingredient.parseQuantityAndUnit("1.25 lbs")
        assertEquals(1.25, decQty, 0.001)
        assertEquals("lbs", decUnit)

        val (mixedQty, mixedUnit) = Ingredient.parseQuantityAndUnit("1 1/2 cups")
        assertEquals(1.5, mixedQty, 0.001)
        assertEquals("cups", mixedUnit)

        val pastIngredient = Ingredient(
            name = "Old Milk",
            quantity = -2.0,
            unit = "carton",
            expiryDate = System.currentTimeMillis() - 86_400_000L
        )
        assertTrue(pastIngredient.isExpired)
        assertEquals("0 carton", pastIngredient.formattedQuantity)

        val pantryItem = PantryItemEntity(
            id = 42L,
            name = "Eggs",
            category = PantryCategory.PROTEIN.name,
            quantity = "6 eggs",
            daysUntilExpiry = 8,
            visualConditionNote = "Fresh",
            storageTip = "Middle shelf"
        )
        val mapped = Ingredient.fromPantryItemEntity(pantryItem)
        assertEquals(42L, mapped.id)
        assertEquals("Eggs", mapped.name)
        assertEquals(6.0, mapped.quantity, 0.001)
        assertEquals("eggs", mapped.unit)
        assertEquals(pantryItem.expiryDateEpochMillis, mapped.expiryDate)
    }
}
