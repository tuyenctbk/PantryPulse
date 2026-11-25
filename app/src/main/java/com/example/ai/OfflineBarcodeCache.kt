package com.example.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.DetectedIngredient
import com.example.data.PantryCategory
import kotlinx.coroutines.flow.first

private val Context.barcodeCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pantrypulse_tier0_barcode_cache"
)

enum class BarcodeLookupSource {
    OFFLINE_CACHE,
    OPEN_FOOD_FACTS_API,
    NOT_FOUND
}

data class Tier0BarcodeResolution(
    val barcode: String,
    val found: Boolean,
    val ingredient: DetectedIngredient? = null,
    val source: BarcodeLookupSource = BarcodeLookupSource.NOT_FOUND,
    val errorMessage: String? = null
)

/**
 * Offline-first persistent cache for Tier 0 barcode lookups.
 * Stores previously fetched Open Food Facts products in Jetpack DataStore so subsequent
 * kitchen/pantry scans work instantaneously with 0 network calls and $0 cloud AI cost.
 */
class OfflineBarcodeCache(private val context: Context) {

    suspend fun getCachedIngredient(barcode: String): DetectedIngredient? {
        val normalized = normalizeBarcodeDigits(barcode)
        if (normalized.isEmpty()) return null
        val prefs = context.barcodeCacheDataStore.data.first()
        val raw = prefs[stringPreferencesKey(KEY_PREFIX + normalized)] ?: return null
        return decodeCachedIngredient(normalized, raw)
    }

    suspend fun putCachedIngredient(barcode: String, ingredient: DetectedIngredient) {
        val normalized = normalizeBarcodeDigits(barcode)
        if (normalized.isEmpty()) return
        val serialized = encodeCachedIngredient(ingredient)
        context.barcodeCacheDataStore.edit { prefs ->
            prefs[stringPreferencesKey(KEY_PREFIX + normalized)] = serialized
        }
    }

    companion object {
        private const val KEY_PREFIX = "upc_"
        private const val DELIM = "\u001F"

        fun normalizeBarcodeDigits(raw: String): String = raw.filter { it.isDigit() }

        fun isValidGroceryBarcode(raw: String): Boolean {
            val digits = normalizeBarcodeDigits(raw)
            return digits.length in 6..14
        }

        fun encodeCachedIngredient(item: DetectedIngredient): String {
            fun clean(value: String): String = value.replace(DELIM, " ").replace("\n", " ").trim()
            return listOf(
                clean(item.name),
                item.category.name,
                clean(item.quantity),
                item.daysUntilExpiry.toString(),
                clean(item.storageTip),
                item.estimatedValueDollars.toString()
            ).joinToString(DELIM)
        }

        fun decodeCachedIngredient(barcode: String, encoded: String): DetectedIngredient? {
            val parts = encoded.split(DELIM)
            if (parts.size < 6) return null
            val name = parts[0].trim()
            if (name.isEmpty()) return null
            val category = PantryCategory.fromString(parts[1])
            val quantity = parts[2].trim().ifEmpty { "1 package" }
            val days = parts[3].toIntOrNull()?.coerceIn(1, 90) ?: 7
            val tip = parts[4].trim().ifEmpty { OpenFoodFactsClient.defaultStorageTip(category) }
            val value = parts[5].toDoubleOrNull()?.coerceIn(0.50, 50.0) ?: 3.50

            return DetectedIngredient(
                name = name,
                category = category,
                quantity = quantity,
                daysUntilExpiry = days,
                visualConditionNote = "Offline Cache #$barcode (0 AI credits)",
                storageTip = tip,
                estimatedValueDollars = value,
                isSelected = true
            )
        }
    }
}
