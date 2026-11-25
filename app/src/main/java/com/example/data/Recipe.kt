package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter

/**
 * Room Entity representing an offline-persisted recipe in the local PantryPulse SQLite database.
 *
 * Works alongside [Ingredient] and [RecipeIngredientCrossRef] to enable offline recipe browsing,
 * favoriting, search, and relational matching against the user's current pantry inventory.
 */
@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["title"]),
        Index(value = ["category"]),
        Index(value = ["isFavorite", "createdAt"])
    ]
)
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "externalId")
    val externalId: String = "",
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String = "",
    @ColumnInfo(name = "category")
    val category: String = RecipeCategoryFilter.QUICK_EASY.name,
    @ColumnInfo(name = "prepTimeMinutes")
    val prepTimeMinutes: Int = 20,
    @ColumnInfo(name = "servings")
    val servings: Int = 2,
    @ColumnInfo(name = "caloriesPerServing")
    val caloriesPerServing: Int = 380,
    @ColumnInfo(name = "estimatedCostPerServing")
    val estimatedCostPerServing: Double = 2.15,
    @ColumnInfo(name = "rescueScore")
    val rescueScore: Int = 90,
    @ColumnInfo(name = "applianceUsed")
    val applianceUsed: String = "Stovetop Skillet",
    @ColumnInfo(name = "ingredientsList")
    val ingredientsList: List<String> = emptyList(),
    @ColumnInfo(name = "spicesList")
    val spicesList: List<String> = emptyList(),
    @ColumnInfo(name = "instructions")
    val instructions: List<String> = emptyList(),
    @ColumnInfo(name = "chefTip")
    val chefTip: String = "",
    @ColumnInfo(name = "isFavorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis()
) {
    val parsedCategory: RecipeCategoryFilter
        get() = RecipeCategoryFilter.fromString(category)

    /**
     * Converts this persisted Room [Recipe] entity into a UI-ready [RecipeSuggestion].
     */
    fun toRecipeSuggestion(rank: Int = 1): RecipeSuggestion = RecipeSuggestion(
        id = externalId.ifBlank { "recipe-$id" },
        rank = rank,
        title = title,
        recipeCategory = parsedCategory,
        rescueScore = rescueScore,
        prepTimeMinutes = prepTimeMinutes,
        servings = servings,
        caloriesPerServing = caloriesPerServing,
        estimatedCostPerServing = estimatedCostPerServing,
        applianceUsed = applianceUsed,
        whyItRescues = description,
        expiringIngredientsUsed = ingredientsList,
        pantryIngredientsUsed = emptyList(),
        spicesUsed = spicesList,
        steps = instructions,
        chefTip = chefTip
    )

    companion object {
        /**
         * Maps a generated or local [RecipeSuggestion] into the canonical [Recipe] Room entity.
         */
        fun fromRecipeSuggestion(
            suggestion: RecipeSuggestion,
            id: Long = 0L,
            isFavorite: Boolean = false
        ): Recipe = Recipe(
            id = id,
            externalId = suggestion.id,
            title = suggestion.title.trim(),
            description = suggestion.whyItRescues.trim(),
            category = suggestion.recipeCategory.name,
            prepTimeMinutes = suggestion.prepTimeMinutes.coerceAtLeast(1),
            servings = suggestion.servings.coerceAtLeast(1),
            caloriesPerServing = suggestion.caloriesPerServing.coerceAtLeast(0),
            estimatedCostPerServing = suggestion.estimatedCostPerServing.coerceAtLeast(0.0),
            rescueScore = suggestion.rescueScore.coerceIn(0, 100),
            applianceUsed = suggestion.applianceUsed.trim(),
            ingredientsList = (suggestion.expiringIngredientsUsed + suggestion.pantryIngredientsUsed)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            spicesList = suggestion.spicesUsed.map { it.trim() }.filter { it.isNotEmpty() },
            instructions = suggestion.steps.map { it.trim() }.filter { it.isNotEmpty() },
            chefTip = suggestion.chefTip.trim(),
            isFavorite = isFavorite
        )
    }
}

/**
 * Many-to-many junction table linking [Recipe] rows with [Ingredient] inventory rows so
 * PantryPulse can query recipes by available/expiring ingredients completely offline.
 */
@Entity(
    tableName = "recipe_ingredient_cross_ref",
    primaryKeys = ["recipeId", "ingredientId"],
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Ingredient::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recipeId"]),
        Index(value = ["ingredientId"])
    ]
)
data class RecipeIngredientCrossRef(
    @ColumnInfo(name = "recipeId")
    val recipeId: Long,
    @ColumnInfo(name = "ingredientId")
    val ingredientId: Long
)

/**
 * Relational projection pairing a [Recipe] with its linked pantry [Ingredient] records.
 */
data class RecipeWithIngredients(
    @Embedded val recipe: Recipe,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeIngredientCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "ingredientId"
        )
    )
    val ingredients: List<Ingredient>
)

/**
 * Relational projection pairing an [Ingredient] with offline [Recipe] records that use it.
 */
data class IngredientWithRecipes(
    @Embedded val ingredient: Ingredient,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeIngredientCrossRef::class,
            parentColumn = "ingredientId",
            entityColumn = "recipeId"
        )
    )
    val recipes: List<Recipe>
)

/**
 * Lossless, dependency-free Room [TypeConverter] for persisting `List<String>` columns
 * (ingredient lists, spices, and cooking instructions) in SQLite.
 */
class PantryTypeConverters {
    @TypeConverter
    fun fromStringList(items: List<String>?): String {
        if (items.isNullOrEmpty()) return ""
        return buildString {
            items.forEach { entry ->
                append(entry.length)
                append(':')
                append(entry)
            }
        }
    }

    @TypeConverter
    fun toStringList(serialized: String?): List<String> {
        if (serialized.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var cursor = 0
        while (cursor < serialized.length) {
            val colonIdx = serialized.indexOf(':', cursor)
            if (colonIdx == -1) {
                return serialized.split("||").map { it.trim() }.filter { it.isNotEmpty() }
            }
            val length = serialized.substring(cursor, colonIdx).toIntOrNull()
                ?: return serialized.split("||").map { it.trim() }.filter { it.isNotEmpty() }
            val start = colonIdx + 1
            val end = start + length
            if (length < 0 || end > serialized.length) {
                return serialized.split("||").map { it.trim() }.filter { it.isNotEmpty() }
            }
            result.add(serialized.substring(start, end))
            cursor = end
        }
        return result
    }
}
