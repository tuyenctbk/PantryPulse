package com.example

import com.example.data.FreshnessStatus
import com.example.data.Ingredient
import com.example.data.PantryCategory
import com.example.data.PantryItemEntity
import com.example.data.PantryTypeConverters
import com.example.data.Recipe
import com.example.data.RecipeCategoryFilter
import com.example.data.RecipeDietaryPreference
import com.example.data.RecipeIngredientCrossRef
import com.example.data.RecipeSuggestion
import com.example.data.computeExpiryEpochMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientEntityUnitTest {

    @Test
    fun parseQuantityAndUnit_supportsIntegersDecimalsFractionsAndMixedFractions() {
        val (intQty, intUnit) = Ingredient.parseQuantityAndUnit("6 eggs")
        assertEquals(6.0, intQty, 0.001)
        assertEquals("eggs", intUnit)

        val (decQty, decUnit) = Ingredient.parseQuantityAndUnit("1.25 lbs")
        assertEquals(1.25, decQty, 0.001)
        assertEquals("lbs", decUnit)

        val (fracQty, fracUnit) = Ingredient.parseQuantityAndUnit("1/2 jar")
        assertEquals(0.5, fracQty, 0.001)
        assertEquals("jar", fracUnit)

        val (mixedQty, mixedUnit) = Ingredient.parseQuantityAndUnit("1 1/2 cups")
        assertEquals(1.5, mixedQty, 0.001)
        assertEquals("cups", mixedUnit)

        val (blankQty, blankUnit) = Ingredient.parseQuantityAndUnit("   ")
        assertEquals(1.0, blankQty, 0.001)
        assertEquals("unit", blankUnit)
    }

    @Test
    fun ingredientExpiryAndFormatting_clampsNegativeQuantityAndDetectsExpiration() {
        val pastItem = Ingredient(
            id = 1L,
            name = "Whole Milk",
            quantity = -3.0,
            unit = "carton",
            expiryDate = System.currentTimeMillis() - 86_400_000L
        )
        assertTrue(pastItem.isExpired)
        assertEquals("0 carton", pastItem.formattedQuantity)
        assertEquals(FreshnessStatus.CRITICAL, pastItem.freshnessStatus)

        val freshItem = Ingredient(
            id = 2L,
            name = "Greek Yogurt",
            quantity = 16.0,
            unit = "oz",
            expiryDate = computeExpiryEpochMillis(10)
        )
        assertFalse(freshItem.isExpired)
        assertEquals("16 oz", freshItem.formattedQuantity)
        assertEquals(FreshnessStatus.FRESH, freshItem.freshnessStatus)
    }

    @Test
    fun fromPantryItemEntity_preservesIdNameQuantityUnitAndExpiry() {
        val entity = PantryItemEntity(
            id = 77L,
            name = "Baby Spinach",
            category = PantryCategory.PRODUCE.name,
            quantity = "5 oz",
            daysUntilExpiry = 2,
            visualConditionNote = "Slightly wilted",
            storageTip = "Paper towel wrap"
        )
        val ingredient = Ingredient.fromPantryItemEntity(entity)
        assertEquals(77L, ingredient.id)
        assertEquals("Baby Spinach", ingredient.name)
        assertEquals(5.0, ingredient.quantity, 0.001)
        assertEquals("oz", ingredient.unit)
        assertEquals(entity.expiryDateEpochMillis, ingredient.expiryDate)
    }

    @Test
    fun recipeFiltering_handlesMultiWhitespaceAndExpandedMeatKeywords() {
        val tunaMelt = RecipeSuggestion(
            id = "r1",
            rank = 1,
            title = "Crispy Skillet Tuna Melt",
            recipeCategory = RecipeCategoryFilter.QUICK_EASY,
            rescueScore = 92,
            prepTimeMinutes = 15,
            servings = 2,
            caloriesPerServing = 420,
            estimatedCostPerServing = 2.10,
            applianceUsed = "Skillet",
            whyItRescues = "Rescues expiring cheddar and bread",
            expiringIngredientsUsed = listOf("Cheddar Cheese", "Sourdough"),
            pantryIngredientsUsed = listOf("Canned Tuna"),
            spicesUsed = listOf("Black Pepper"),
            steps = listOf("Toast in skillet"),
            chefTip = "Cover pan to melt cheese"
        )

        assertTrue(tunaMelt.matchesSearchQuery("skillet   tuna"))
        assertFalse(tunaMelt.matchesDietaryPreference(RecipeDietaryPreference.VEGETARIAN))
        assertTrue(tunaMelt.matchesDietaryPreference(RecipeDietaryPreference.HIGH_PROTEIN))
    }

    @Test
    fun recipeEntityAndTypeConverters_roundTripLosslessly() {
        val converters = PantryTypeConverters()
        val trickyItems = listOf(
            "1 1/2 cups Baby Spinach (wilted, 1:2 ratio)",
            "Step 2: Stir || fold gently\nfor 3 mins",
            "Salt, pepper & olive oil"
        )
        val encoded = converters.fromStringList(trickyItems)
        assertEquals(trickyItems, converters.toStringList(encoded))
        assertEquals(emptyList<String>(), converters.toStringList(""))

        val suggestion = RecipeSuggestion(
            id = "rescue-101",
            rank = 1,
            title = "Zero-Waste Spinach & Feta Frittata",
            recipeCategory = RecipeCategoryFilter.HEALTHY_LOW_CAL,
            rescueScore = 96,
            prepTimeMinutes = 18,
            servings = 2,
            caloriesPerServing = 340,
            estimatedCostPerServing = 1.90,
            applianceUsed = "Cast Iron Pan",
            whyItRescues = "Uses wilted spinach and feta before spoilage",
            expiringIngredientsUsed = listOf("Baby Spinach", "Crumbled Feta"),
            pantryIngredientsUsed = listOf("4 Eggs"),
            spicesUsed = listOf("Black Pepper", "Olive Oil"),
            steps = listOf("Whisk eggs", "Sauté spinach", "Bake 10 min"),
            chefTip = "Finish under broiler for 60 seconds"
        )

        val recipeEntity = Recipe.fromRecipeSuggestion(suggestion, id = 10L, isFavorite = true)
        assertEquals(10L, recipeEntity.id)
        assertEquals("rescue-101", recipeEntity.externalId)
        assertEquals(3, recipeEntity.ingredientsList.size)
        assertTrue(recipeEntity.isFavorite)
        assertEquals(RecipeCategoryFilter.HEALTHY_LOW_CAL, recipeEntity.parsedCategory)

        val roundTripped = recipeEntity.toRecipeSuggestion(rank = 1)
        assertEquals("rescue-101", roundTripped.id)
        assertEquals(suggestion.title, roundTripped.title)
        assertEquals(suggestion.steps, roundTripped.steps)

        val crossRef = RecipeIngredientCrossRef(recipeId = 10L, ingredientId = 77L)
        assertEquals(10L, crossRef.recipeId)
        assertEquals(77L, crossRef.ingredientId)
    }
}
