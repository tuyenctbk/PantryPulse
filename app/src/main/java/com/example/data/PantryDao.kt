package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry_items WHERE isConsumed = 0 ORDER BY expiryDateEpochMillis ASC, daysUntilExpiry ASC, addedTimestamp DESC")
    fun getActiveItemsFlow(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE isConsumed = 0 ORDER BY expiryDateEpochMillis ASC, daysUntilExpiry ASC, addedTimestamp DESC")
    suspend fun getActiveItemsSnapshot(): List<PantryItemEntity>

    @Query("SELECT * FROM pantry_items WHERE isConsumed = 0 AND expiryDateEpochMillis <= :cutoffEpochMillis ORDER BY expiryDateEpochMillis ASC")
    fun getExpiringBeforeFlow(cutoffEpochMillis: Long): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE isConsumed = 0 AND stockPercent <= :thresholdPercent ORDER BY stockPercent ASC, expiryDateEpochMillis ASC")
    fun getLowStockItemsFlow(thresholdPercent: Int): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry_items WHERE isConsumed = 0 AND (name LIKE '%' || :query || '%' OR visualConditionNote LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%') ORDER BY expiryDateEpochMillis ASC")
    fun searchActiveItemsFlow(query: String): Flow<List<PantryItemEntity>>

    @Query("SELECT COUNT(*) FROM pantry_items WHERE isConsumed = 0")
    fun getActiveItemsCountFlow(): Flow<Int>

    @Query("SELECT * FROM pantry_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): PantryItemEntity?

    @Query("SELECT * FROM pantry_items WHERE isConsumed = 0 AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getActiveItemByNameIgnoreCase(name: String): PantryItemEntity?

    @Query("SELECT COUNT(*) FROM pantry_items")
    suspend fun getTotalEverCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PantryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PantryItemEntity>)

    @Update
    suspend fun updateItem(item: PantryItemEntity)

    @Query("UPDATE pantry_items SET daysUntilExpiry = :daysUntilExpiry, expiryDateEpochMillis = :expiryDateEpochMillis, updatedTimestamp = :updatedTimestamp WHERE id = :id")
    suspend fun updateExpiryCountdown(
        id: Long,
        daysUntilExpiry: Int,
        expiryDateEpochMillis: Long,
        updatedTimestamp: Long
    )

    @Query("UPDATE pantry_items SET isConsumed = 1, updatedTimestamp = :updatedTimestamp WHERE id = :id")
    suspend fun markConsumed(id: Long, updatedTimestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM pantry_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("SELECT * FROM saved_recipes ORDER BY rank ASC")
    fun getSavedRecipesFlow(): Flow<List<SavedRecipeEntity>>

    @Query("DELETE FROM saved_recipes")
    suspend fun clearSavedRecipes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRecipes(recipes: List<SavedRecipeEntity>)

    @Query("SELECT * FROM restock_items ORDER BY isChecked ASC, addedTimestamp DESC")
    fun getRestockItemsFlow(): Flow<List<RestockItemEntity>>

    @Query("SELECT * FROM restock_items ORDER BY isChecked ASC, addedTimestamp DESC")
    suspend fun getRestockItemsSnapshot(): List<RestockItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestockItem(item: RestockItemEntity): Long

    @Update
    suspend fun updateRestockItem(item: RestockItemEntity)

    @Query("DELETE FROM restock_items WHERE id = :id")
    suspend fun deleteRestockItemById(id: Long)

    @Query("DELETE FROM restock_items WHERE isChecked = 1")
    suspend fun deleteCheckedRestockItems()
}
