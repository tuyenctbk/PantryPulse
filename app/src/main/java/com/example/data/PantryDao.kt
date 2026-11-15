package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items WHERE isConsumed = 0 ORDER BY daysUntilExpiry ASC, addedTimestamp DESC")
    fun getActiveItemsFlow(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE isConsumed = 0 ORDER BY daysUntilExpiry ASC, addedTimestamp DESC")
    suspend fun getActiveItemsSnapshot(): List<PantryItemEntity>

    @Query("SELECT COUNT(*) FROM pantry_items")
    suspend fun getTotalEverCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PantryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PantryItemEntity>)

    @Update
    suspend fun updateItem(item: PantryItemEntity)

    @Query("UPDATE pantry_items SET isConsumed = 1 WHERE id = :id")
    suspend fun markConsumed(id: Long)

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("SELECT * FROM saved_recipes ORDER BY rank ASC")
    fun getSavedRecipesFlow(): Flow<List<SavedRecipeEntity>>

    @Query("DELETE FROM saved_recipes")
    suspend fun clearSavedRecipes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRecipes(recipes: List<SavedRecipeEntity>)

    @Query("SELECT * FROM restock_items ORDER BY addedTimestamp DESC")
    fun getRestockItemsFlow(): Flow<List<RestockItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestockItem(item: RestockItemEntity): Long

    @Query("DELETE FROM restock_items WHERE id = :id")
    suspend fun deleteRestockItemById(id: Long)
}
