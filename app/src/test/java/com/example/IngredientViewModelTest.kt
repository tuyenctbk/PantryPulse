package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Ingredient
import com.example.data.IngredientDao
import com.example.data.PantryDatabase
import com.example.data.computeExpiryEpochMillis
import com.example.ui.IngredientListFilter
import com.example.ui.IngredientUiState
import com.example.ui.IngredientViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IngredientViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var db: PantryDatabase
    private lateinit var ingredientDao: IngredientDao
    private lateinit var viewModel: IngredientViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PantryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ingredientDao = db.ingredientDao()
        viewModel = IngredientViewModel(ingredientDao, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun addUpdateAndDeleteIngredients_reflectsInReactiveUiState() = runTest(testDispatcher) {
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val spinachExpiry = computeExpiryEpochMillis(2)
        val milkExpiry = computeExpiryEpochMillis(7)

        viewModel.addIngredient("Baby Spinach", 5.0, "oz", spinachExpiry)
        viewModel.addIngredient("Whole Milk", 1.0, "gallon", milkExpiry)
        advanceUntilIdle()

        val stateAfterInsert = viewModel.uiState.first {
            it is IngredientUiState.Success && it.totalCount == 2
        } as IngredientUiState.Success

        assertEquals(2, stateAfterInsert.ingredients.size)
        assertEquals(1, stateAfterInsert.expiringSoonCount)
        assertEquals("Baby Spinach", stateAfterInsert.ingredients[0].name)
        assertEquals("Whole Milk", stateAfterInsert.ingredients[1].name)

        // Update quantity via adjustQuantity
        val spinach = stateAfterInsert.ingredients[0]
        viewModel.adjustQuantity(spinach, -2.0)
        advanceUntilIdle()

        val updatedSpinach = ingredientDao.getIngredientById(spinach.id)!!
        assertEquals(3.0, updatedSpinach.quantity, 0.001)

        // Reducing quantity to <= 0 deletes the ingredient
        viewModel.adjustQuantity(updatedSpinach, -3.0)
        advanceUntilIdle()
        assertNull(ingredientDao.getIngredientById(spinach.id))

        val stateAfterDelete = viewModel.uiState.first {
            it is IngredientUiState.Success && it.totalCount == 1
        } as IngredientUiState.Success
        assertEquals("Whole Milk", stateAfterDelete.ingredients.single().name)

        // Clear all
        viewModel.clearAllIngredients()
        advanceUntilIdle()
        val emptyState = viewModel.uiState.first {
            it is IngredientUiState.Success && it.totalCount == 0
        } as IngredientUiState.Success
        assertTrue(emptyState.ingredients.isEmpty())

        collectorJob.cancel()
    }

    @Test
    fun searchAndExpiryFilters_filterIngredientsReactively() = runTest(testDispatcher) {
        val collectorJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.addIngredients(
            listOf(
                Ingredient(
                    name = "Expired Strawberries",
                    quantity = 1.0,
                    unit = "pint",
                    expiryDate = computeExpiryEpochMillis(-2)
                ),
                Ingredient(
                    name = "Fresh Basil",
                    quantity = 1.0,
                    unit = "bunch",
                    expiryDate = computeExpiryEpochMillis(1)
                ),
                Ingredient(
                    name = "Cheddar Block",
                    quantity = 8.0,
                    unit = "oz",
                    expiryDate = computeExpiryEpochMillis(14)
                )
            )
        )
        advanceUntilIdle()

        // Filter EXPIRING_SOON (0..3 days)
        viewModel.setFilter(IngredientListFilter.EXPIRING_SOON)
        advanceUntilIdle()
        val expiringSoonState = viewModel.uiState.first {
            it is IngredientUiState.Success && it.filter == IngredientListFilter.EXPIRING_SOON && it.ingredients.size == 1
        } as IngredientUiState.Success
        assertEquals("Fresh Basil", expiringSoonState.ingredients.single().name)

        // Filter EXPIRED (< 0 days)
        viewModel.setFilter(IngredientListFilter.EXPIRED)
        advanceUntilIdle()
        val expiredState = viewModel.uiState.first {
            it is IngredientUiState.Success && it.filter == IngredientListFilter.EXPIRED && it.ingredients.size == 1
        } as IngredientUiState.Success
        assertEquals("Expired Strawberries", expiredState.ingredients.single().name)

        // Reset filter to ALL and search by name
        viewModel.setFilter(IngredientListFilter.ALL)
        viewModel.updateSearchQuery("cheddar")
        advanceUntilIdle()
        val searchState = viewModel.uiState.first {
            it is IngredientUiState.Success && it.searchQuery == "cheddar" && it.ingredients.size == 1
        } as IngredientUiState.Success
        assertEquals("Cheddar Block", searchState.ingredients.single().name)

        collectorJob.cancel()
    }

    @Test
    fun validationRejectsEmptyNameOrNonPositiveQuantity() = runTest(testDispatcher) {
        viewModel.addIngredient("   ", 2.0, "pcs", computeExpiryEpochMillis(3))
        advanceUntilIdle()
        assertNotNull(viewModel.errorMessage.value)

        viewModel.clearError()
        assertNull(viewModel.errorMessage.value)

        viewModel.addIngredient("Milk", 0.0, "liter", computeExpiryEpochMillis(3))
        advanceUntilIdle()
        assertNotNull(viewModel.errorMessage.value)
        assertTrue(ingredientDao.getAllIngredientsSnapshot().isEmpty())
    }

    @Test
    fun factoryCreatesIngredientViewModelInstance() {
        val factory = IngredientViewModel.factory(ingredientDao, testDispatcher)
        val created = factory.create(IngredientViewModel::class.java)
        assertNotNull(created)
    }
}
