package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for the [Recipe] entity (`recipes` table)
 * and its relational links with [Ingredient] (`recipe_ingredient_cross_ref`).
 */
@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, rescueScore DESC, createdAt DESC")
    fun getAllRecipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteRecipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun getRecipeById(id: Long): Recipe?

    @Query("SELECT * FROM recipes WHERE externalId = :externalId LIMIT 1")
    suspend fun getRecipeByExternalId(externalId: String): Recipe?

    @Query("SELECT * FROM recipes WHERE category = :category ORDER BY rescueScore DESC, createdAt DESC")
    fun getRecipesByCategory(category: String): Flow<List<Recipe>>

    @Query(
        "SELECT * FROM recipes " +
            "WHERE title LIKE '%' || :query || '%' " +
            "OR description LIKE '%' || :query || '%' " +
            "OR ingredientsList LIKE '%' || :query || '%' " +
            "OR applianceUsed LIKE '%' || :query || '%' " +
            "ORDER BY isFavorite DESC, rescueScore DESC"
    )
    fun searchRecipes(query: String): Flow<List<Recipe>>

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY isFavorite DESC, rescueScore DESC, createdAt DESC")
    fun getRecipesWithIngredients(): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :recipeId LIMIT 1")
    fun observeRecipeWithIngredients(recipeId: Long): Flow<RecipeWithIngredients?>

    @Transaction
    @Query("SELECT * FROM ingredients ORDER BY expiryDate ASC, name ASC")
    fun getIngredientsWithRecipes(): Flow<List<IngredientWithRecipes>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipes(recipes: List<Recipe>): List<Long>

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Query("UPDATE recipes SET isFavorite = :isFavorite WHERE id = :recipeId")
    suspend fun setRecipeFavorite(recipeId: Long, isFavorite: Boolean)

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipeById(recipeId: Long)

    @Query("DELETE FROM recipes WHERE isFavorite = 0")
    suspend fun deleteNonFavoriteRecipes()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeIngredientCrossRef(crossRef: RecipeIngredientCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecipeIngredientCrossRefs(crossRefs: List<RecipeIngredientCrossRef>)

    @Query("DELETE FROM recipe_ingredient_cross_ref WHERE recipeId = :recipeId")
    suspend fun deleteCrossRefsForRecipe(recipeId: Long)
}
