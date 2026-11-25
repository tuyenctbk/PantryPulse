package com.example.ai

import com.example.data.DetectedIngredient
import com.example.data.PantryCategory
import com.example.data.remote.OpenFoodFactsNetworkModule
import com.example.data.remote.OpenFoodFactsRemoteDataSource
import com.example.data.remote.OpenFoodFactsResponseDto

data class BarcodeLookupResult(
    val barcode: String,
    val found: Boolean,
    val ingredient: DetectedIngredient? = null,
    val errorMessage: String? = null
)

/**
 * Tier 0 ($0 API cost) grocery barcode lookup backed by the free public Open Food Facts API
 * via Retrofit + OkHttp + Gson. Cuts cloud Vision API usage by allowing instant barcode ingestion
 * before touching cloud credits.
 */
object OpenFoodFactsClient {

    private val remoteDataSource by lazy { OpenFoodFactsRemoteDataSource() }

    suspend fun lookupBarcode(rawBarcode: String): BarcodeLookupResult =
        remoteDataSource.fetchProductByBarcode(rawBarcode)

    /**
     * Pure JVM-safe parser for Open Food Facts v2 JSON payload backed by Gson
     * (no Android `org.json` dependency so unit tests run cleanly without Android stubs).
     */
    fun parseOpenFoodFactsResponse(barcode: String, jsonBody: String): BarcodeLookupResult {
        val dto = runCatching {
            OpenFoodFactsNetworkModule.gson.fromJson(jsonBody, OpenFoodFactsResponseDto::class.java)
        }.getOrNull() ?: return BarcodeLookupResult(
            barcode = barcode,
            found = false,
            errorMessage = "Unable to parse Open Food Facts payload for barcode $barcode."
        )
        return remoteDataSource.mapDtoToResult(barcode = barcode, dto = dto)
    }

    internal fun computeDaysUntilExpirationString(
        rawDate: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Int? {
        val cleaned = rawDate.trim()
        if (cleaned.isEmpty()) return null
        val patterns = listOf("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM")
        for (pattern in patterns) {
            val parsed = runCatching {
                java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                    isLenient = false
                }.parse(cleaned)
            }.getOrNull()
            if (parsed != null) {
                val diffDays = ((parsed.time - nowMillis) / 86_400_000L).toInt()
                return diffDays.coerceIn(1, 365)
            }
        }
        return null
    }

    internal fun inferCategory(name: String, categoriesTagsLower: String): PantryCategory {
        val combined = "${name.lowercase()} $categoriesTagsLower"
        return when {
            listOf("dairy", "milk", "cheese", "yogurt", "butter", "cream", "kefir", "feta", "cheddar")
                .any { combined.contains(it) } -> PantryCategory.DAIRY_CHEESE
            listOf("produce", "vegetable", "fruit", "salad", "spinach", "berry", "berries", "herb", "apple", "tomato")
                .any { combined.contains(it) } -> PantryCategory.PRODUCE
            listOf("sauce", "condiment", "pesto", "salsa", "marinara", "ketchup", "mustard", "mayo", "dressing", "jam", "jar")
                .any { combined.contains(it) } -> PantryCategory.SAUCES_JARS
            listOf("meat", "poultry", "chicken", "beef", "fish", "seafood", "tofu", "egg", "sausage", "ham", "deli")
                .any { combined.contains(it) } -> PantryCategory.PROTEIN
            listOf("meal", "prepared", "soup", "ready-to-eat", "leftover")
                .any { combined.contains(it) } -> PantryCategory.LEFTOVERS
            else -> PantryCategory.PANTRY_STAPLE
        }
    }

    internal fun estimateShelfLifeDays(category: PantryCategory): Int = when (category) {
        PantryCategory.PRODUCE -> 4
        PantryCategory.PROTEIN -> 4
        PantryCategory.LEFTOVERS -> 3
        PantryCategory.DAIRY_CHEESE -> 7
        PantryCategory.SAUCES_JARS -> 14
        PantryCategory.PANTRY_STAPLE -> 30
    }

    internal fun estimateGroceryDollars(category: PantryCategory): Double = when (category) {
        PantryCategory.PROTEIN -> 6.50
        PantryCategory.DAIRY_CHEESE -> 4.75
        PantryCategory.SAUCES_JARS -> 4.25
        PantryCategory.PRODUCE -> 3.50
        PantryCategory.LEFTOVERS -> 5.00
        PantryCategory.PANTRY_STAPLE -> 3.25
    }

    internal fun defaultStorageTip(category: PantryCategory): String = when (category) {
        PantryCategory.PRODUCE -> "Store in the high-humidity crisper drawer lined with a dry paper towel."
        PantryCategory.DAIRY_CHEESE -> "Keep on an interior middle shelf rather than the warm fridge door."
        PantryCategory.SAUCES_JARS -> "Refrigerate after opening and wipe the jar rim clean before sealing."
        PantryCategory.PROTEIN -> "Store on the coldest lower shelf and cook or freeze before expiry."
        PantryCategory.LEFTOVERS -> "Keep in a shallow airtight container and reheat thoroughly within 72 hours."
        PantryCategory.PANTRY_STAPLE -> "Store sealed in a cool, dry cabinet away from stove heat and humidity."
    }
}
