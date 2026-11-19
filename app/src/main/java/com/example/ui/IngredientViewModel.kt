package com.example.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.Ingredient
import com.example.data.IngredientDao
import com.example.data.PantryDatabase
import com.example.data.computeExpiryEpochMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Filter categories for displaying ingredients in the UI.
 */
enum class IngredientListFilter {
    ALL,
    EXPIRING_SOON,
    EXPIRED
}

/**
 * Type-safe UI state representing the live Room-backed ingredient inventory.
 */
sealed interface IngredientUiState {
    data object Loading : IngredientUiState

    data class Success(
        val ingredients: List<Ingredient>,
        val totalCount: Int,
        val expiringSoonCount: Int,
        val searchQuery: String,
        val filter: IngredientListFilter
    ) : IngredientUiState

    data class Error(val message: String) : IngredientUiState
}

/**
 * ViewModel powered by Kotlin Coroutines & StateFlow that uses [IngredientDao]
 * to fetch, filter, and manage [Ingredient] entities in the local Room database for Compose UI.
 */
class IngredientViewModel(
    private val ingredientDao: IngredientDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val stringResolver: (Int) -> String = ::defaultIngredientString
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(IngredientListFilter.ALL)
    val selectedFilter: StateFlow<IngredientListFilter> = _selectedFilter.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _errorResId = MutableStateFlow<Int?>(null)
    val errorResId: StateFlow<Int?> = _errorResId.asStateFlow()

    /**
     * Reactive list of all stored ingredients ordered by earliest [Ingredient.expiryDate].
     * Collect in Compose with `collectAsStateWithLifecycle()`.
     */
    val ingredients: StateFlow<List<Ingredient>> = ingredientDao
        .getAllIngredients()
        .catch { e ->
            _errorResId.value = R.string.ingredient_err_load
            _errorMessage.value = e.localizedMessage ?: stringResolver(R.string.ingredient_err_load)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    /**
     * Combined reactive UI state incorporating search query, expiry filter, and live Room updates.
     */
    val uiState: StateFlow<IngredientUiState> = combine(
        ingredientDao.getAllIngredients(),
        _searchQuery,
        _selectedFilter
    ) { allIngredients, query, filter ->
        val trimmedQuery = query.trim()
        val expiringSoonCount = allIngredients.count { it.daysUntilExpiry in 0..3 }

        val filtered = allIngredients.filter { ingredient ->
            val matchesFilter = when (filter) {
                IngredientListFilter.ALL -> true
                IngredientListFilter.EXPIRING_SOON -> ingredient.daysUntilExpiry in 0..3
                IngredientListFilter.EXPIRED -> ingredient.daysUntilExpiry < 0
            }
            val matchesQuery = trimmedQuery.isEmpty() ||
                ingredient.name.contains(trimmedQuery, ignoreCase = true) ||
                ingredient.unit.contains(trimmedQuery, ignoreCase = true)

            matchesFilter && matchesQuery
        }

        IngredientUiState.Success(
            ingredients = filtered,
            totalCount = allIngredients.size,
            expiringSoonCount = expiringSoonCount,
            searchQuery = query,
            filter = filter
        ) as IngredientUiState
    }
        .catch { e ->
            emit(
                IngredientUiState.Error(
                    message = e.localizedMessage ?: stringResolver(R.string.ingredient_err_read_db)
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = IngredientUiState.Loading
        )

    /**
     * Updates the active search filter applied to [uiState].
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Updates the freshness filter applied to [uiState].
     */
    fun setFilter(filter: IngredientListFilter) {
        _selectedFilter.value = filter
    }

    /**
     * Clears any transient operation error message after the UI displays it.
     */
    fun clearError() {
        _errorMessage.value = null
        _errorResId.value = null
    }

    private fun reportError(@StringRes resId: Int, throwable: Throwable? = null) {
        _errorResId.value = resId
        _errorMessage.value = throwable?.localizedMessage ?: stringResolver(resId)
    }

    /**
     * Validates and inserts a new [Ingredient] into the Room database on [ioDispatcher].
     */
    fun addIngredient(
        name: String,
        quantity: Double,
        unit: String,
        expiryDate: Long,
        onInserted: ((Long) -> Unit)? = null
    ) {
        val cleanName = name.trim()
        val cleanUnit = unit.trim().ifEmpty { stringResolver(R.string.ingredient_default_unit) }
        if (cleanName.isEmpty()) {
            reportError(R.string.ingredient_err_empty_name)
            return
        }
        if (quantity <= 0.0 || quantity.isNaN() || quantity.isInfinite()) {
            reportError(R.string.ingredient_err_non_positive_qty)
            return
        }

        val entity = Ingredient(
            name = cleanName,
            quantity = quantity,
            unit = cleanUnit,
            expiryDate = expiryDate
        )
        addIngredient(entity, onInserted)
    }

    /**
     * Inserts a pre-constructed [Ingredient] entity into Room.
     */
    fun addIngredient(
        ingredient: Ingredient,
        onInserted: ((Long) -> Unit)? = null
    ) {
        val cleanName = ingredient.name.trim()
        if (cleanName.isEmpty() || ingredient.quantity <= 0.0) {
            reportError(R.string.ingredient_err_invalid)
            return
        }
        val defaultUnit = stringResolver(R.string.ingredient_default_unit)
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    ingredientDao.insertIngredient(
                        ingredient.copy(
                            name = cleanName,
                            unit = ingredient.unit.trim().ifEmpty { defaultUnit }
                        )
                    )
                }
            }.onSuccess { rowId ->
                clearError()
                onInserted?.invoke(rowId)
            }.onFailure { e ->
                reportError(R.string.ingredient_err_save, e)
            }
        }
    }

    /**
     * Inserts multiple [Ingredient] rows in a single coroutine operation.
     */
    fun addIngredients(ingredientsList: List<Ingredient>) {
        val defaultUnit = stringResolver(R.string.ingredient_default_unit)
        val sanitized = ingredientsList
            .map { it.copy(name = it.name.trim(), unit = it.unit.trim().ifEmpty { defaultUnit }) }
            .filter { it.name.isNotEmpty() && it.quantity > 0.0 }
        if (sanitized.isEmpty()) return

        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    ingredientDao.insertIngredients(sanitized)
                }
            }.onFailure { e ->
                reportError(R.string.ingredient_err_save_batch, e)
            }
        }
    }

    /**
     * Updates an existing [Ingredient] in Room.
     */
    fun updateIngredient(ingredient: Ingredient) {
        val cleanName = ingredient.name.trim()
        if (cleanName.isEmpty() || ingredient.quantity <= 0.0) {
            reportError(R.string.ingredient_err_invalid)
            return
        }
        val defaultUnit = stringResolver(R.string.ingredient_default_unit)
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    ingredientDao.updateIngredient(
                        ingredient.copy(
                            name = cleanName,
                            unit = ingredient.unit.trim().ifEmpty { defaultUnit }
                        )
                    )
                }
            }.onFailure { e ->
                reportError(R.string.ingredient_err_update, e)
            }
        }
    }

    /**
     * Adjusts an ingredient's numeric quantity by [delta].
     * Removes the ingredient automatically if the resulting quantity drops to `<= 0.0`.
     */
    fun adjustQuantity(ingredient: Ingredient, delta: Double) {
        val updatedQty = ingredient.quantity + delta
        if (updatedQty <= 0.0) {
            deleteIngredient(ingredient)
        } else {
            updateIngredient(ingredient.copy(quantity = updatedQty))
        }
    }

    /**
     * Shifts an ingredient's expiration date by [deltaDays] calendar days.
     */
    fun adjustExpiryDays(ingredient: Ingredient, deltaDays: Int) {
        val nextExpiry = computeExpiryEpochMillis(
            daysUntilExpiry = deltaDays,
            fromNowMillis = ingredient.expiryDate
        )
        updateIngredient(ingredient.copy(expiryDate = nextExpiry))
    }

    /**
     * Deletes the specified [Ingredient] entity from Room.
     */
    fun deleteIngredient(ingredient: Ingredient) {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    ingredientDao.deleteIngredient(ingredient)
                }
            }.onFailure { e ->
                reportError(R.string.ingredient_err_delete, e)
            }
        }
    }

    /**
     * Deletes an [Ingredient] row by its primary key [id].
     */
    fun deleteIngredientById(id: Long) {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    ingredientDao.deleteIngredientById(id)
                }
            }.onFailure { e ->
                reportError(R.string.ingredient_err_delete, e)
            }
        }
    }

    /**
     * Removes all ingredients from the local Room table.
     */
    fun clearAllIngredients() {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    ingredientDao.deleteAllIngredients()
                }
            }.onFailure { e ->
                reportError(R.string.ingredient_err_clear, e)
            }
        }
    }

    /**
     * Fetches a single [Ingredient] by [id] asynchronously and returns it via [onResult].
     */
    fun loadIngredientById(id: Long, onResult: (Ingredient?) -> Unit) {
        viewModelScope.launch {
            val found = runCatching {
                withContext(ioDispatcher) {
                    ingredientDao.getIngredientById(id)
                }
            }.getOrNull()
            onResult(found)
        }
    }

    companion object {
        private fun defaultIngredientString(@StringRes resId: Int): String = when (resId) {
            R.string.ingredient_default_unit -> "unit"
            R.string.ingredient_err_load -> "Failed to load ingredients."
            R.string.ingredient_err_read_db -> "Unable to read local ingredient database."
            R.string.ingredient_err_empty_name -> "Ingredient name cannot be empty."
            R.string.ingredient_err_non_positive_qty -> "Quantity must be greater than zero."
            R.string.ingredient_err_invalid -> "Invalid ingredient name or quantity."
            R.string.ingredient_err_save -> "Failed to save ingredient."
            R.string.ingredient_err_save_batch -> "Failed to save ingredients."
            R.string.ingredient_err_update -> "Failed to update ingredient."
            R.string.ingredient_err_delete -> "Failed to delete ingredient."
            R.string.ingredient_err_clear -> "Failed to clear ingredients."
            else -> ""
        }

        /**
         * Creates a [ViewModelProvider.Factory] backed by an explicit [IngredientDao].
         */
        fun factory(
            ingredientDao: IngredientDao,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
            stringResolver: (Int) -> String = ::defaultIngredientString
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(IngredientViewModel::class.java)) {
                    return IngredientViewModel(ingredientDao, ioDispatcher, stringResolver) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

        /**
         * Convenience factory resolving [IngredientDao] and localized strings from [Context].
         */
        fun factory(context: Context): ViewModelProvider.Factory {
            val appCtx = context.applicationContext
            val dao = PantryDatabase.getInstance(appCtx).ingredientDao()
            return factory(dao) { resId -> appCtx.getString(resId) }
        }
    }
}
