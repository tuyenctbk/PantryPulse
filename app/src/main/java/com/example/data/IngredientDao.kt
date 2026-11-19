package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for the [Ingredient] entity (`ingredients` table).
 *
 * Handles basic CRUD operations — insert, update, delete, and reactive Flow queries —
 * for offline ingredient inventory management.
 */
@Dao
interface IngredientDao {

    /**
     * Queries all stored ingredients ordered by earliest [Ingredient.expiryDate] first.
     * Emits updates automatically whenever rows are inserted, updated, or deleted.
     */
    @Query("SELECT * FROM ingredients ORDER BY expiryDate ASC, name ASC")
    fun getAllIngredients(): Flow<List<Ingredient>>

    /**
     * One-shot snapshot of all stored ingredients ordered by earliest [Ingredient.expiryDate].
     */
    @Query("SELECT * FROM ingredients ORDER BY expiryDate ASC, name ASC")
    suspend fun getAllIngredientsSnapshot(): List<Ingredient>

    /**
     * Looks up a single ingredient by its primary key [id].
     */
    @Query("SELECT * FROM ingredients WHERE id = :id LIMIT 1")
    suspend fun getIngredientById(id: Long): Ingredient?

    /**
     * Observes a single ingredient reactively by its primary key [id].
     */
    @Query("SELECT * FROM ingredients WHERE id = :id LIMIT 1")
    fun observeIngredientById(id: Long): Flow<Ingredient?>

    /**
     * Queries ingredients expiring on or before [cutoffEpochMillis].
     */
    @Query("SELECT * FROM ingredients WHERE expiryDate <= :cutoffEpochMillis ORDER BY expiryDate ASC")
    fun getExpiringIngredientsBefore(cutoffEpochMillis: Long): Flow<List<Ingredient>>

    /**
     * Searches ingredients by [Ingredient.name] or [Ingredient.unit].
     */
    @Query(
        "SELECT * FROM ingredients " +
            "WHERE name LIKE '%' || :query || '%' " +
            "OR unit LIKE '%' || :query || '%' " +
            "ORDER BY expiryDate ASC"
    )
    fun searchIngredients(query: String): Flow<List<Ingredient>>

    /**
     * Inserts a single [Ingredient], replacing on primary key conflict.
     * Returns the newly generated (or replaced) row ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: Ingredient): Long

    /**
     * Inserts a batch of [Ingredient] records, replacing on primary key conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<Ingredient>): List<Long>

    /**
     * Updates an existing [Ingredient] row matching its primary key.
     */
    @Update
    suspend fun updateIngredient(ingredient: Ingredient)

    /**
     * Deletes the given [Ingredient] entity from the database.
     */
    @Delete
    suspend fun deleteIngredient(ingredient: Ingredient)

    /**
     * Deletes an [Ingredient] by its primary key [id].
     */
    @Query("DELETE FROM ingredients WHERE id = :id")
    suspend fun deleteIngredientById(id: Long)

    /**
     * Deletes all rows from the `ingredients` table.
     */
    @Query("DELETE FROM ingredients")
    suspend fun deleteAllIngredients()
}
