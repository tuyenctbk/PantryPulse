package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * Room Entity representing a stored kitchen ingredient in the local offline SQLite database.
 *
 * Fields:
 * - [id]: Auto-generated unique primary key.
 * - [name]: Ingredient display name (e.g., "Baby Spinach").
 * - [quantity]: Numeric quantity remaining (e.g., 5.0, 0.5, 6.0).
 * - [unit]: Unit of measurement (e.g., "oz", "cups", "bunches", "pcs").
 * - [expiryDate]: Expiration date stored as UTC epoch milliseconds.
 */
@Entity(
    tableName = "ingredients",
    indices = [
        Index(value = ["expiryDate"]),
        Index(value = ["name"])
    ]
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val quantity: Double,
    val unit: String,
    val expiryDate: Long
) {
    /**
     * Human-readable combined quantity & unit string (e.g., "5 oz", "1.5 cups", "6 pcs").
     */
    val formattedQuantity: String
        get() {
            val qtyStr = if (quantity % 1.0 == 0.0) {
                quantity.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", quantity).trimEnd('0').trimEnd('.')
            }
            return if (unit.isBlank()) qtyStr else "$qtyStr ${unit.trim()}"
        }

    /**
     * Real-time day countdown derived from [expiryDate].
     */
    val daysUntilExpiry: Int
        get() = computeDaysUntilExpiry(expiryDate)

    /**
     * Formatted calendar expiration date string (e.g., "Nov 22, 2026").
     */
    val formattedExpiryDate: String
        get() = formatExpiryDate(expiryDate)

    /**
     * Derived freshness tier based on [daysUntilExpiry].
     */
    val freshnessStatus: FreshnessStatus
        get() = FreshnessStatus.fromDays(daysUntilExpiry)

    /**
     * Converts this core [Ingredient] record into a rich [PantryItemEntity] for UI display.
     */
    fun toPantryItemEntity(
        category: PantryCategory = PantryCategory.PRODUCE,
        visualConditionNote: String = "",
        storageTip: String = "",
        estimatedValueDollars: Double = 3.50
    ): PantryItemEntity {
        val computedDays = computeDaysUntilExpiry(expiryDate).coerceAtLeast(0)
        return PantryItemEntity(
            id = id,
            name = name,
            category = category.name,
            quantity = formattedQuantity,
            daysUntilExpiry = computedDays,
            expiryDateEpochMillis = expiryDate,
            visualConditionNote = visualConditionNote,
            storageTip = storageTip,
            estimatedValueDollars = estimatedValueDollars
        )
    }

    companion object {
        private val FRACTION_PREFIX_REGEX = Regex("^(\\d+)\\s*/\\s*(\\d+)\\s*(.*)$")
        private val NUMBER_PREFIX_REGEX = Regex("^(\\d+(?:\\.\\d+)?)\\s*(.*)$")

        /**
         * Parses a free-form quantity string (e.g., "5 oz wedge", "1/2 box", "6 eggs")
         * into a structured `(quantity: Double, unit: String)` pair.
         */
        fun parseQuantityAndUnit(rawQuantity: String): Pair<Double, String> {
            val trimmed = rawQuantity.trim()
            if (trimmed.isEmpty()) return 1.0 to "unit"

            FRACTION_PREFIX_REGEX.matchEntire(trimmed)?.let { match ->
                val numerator = match.groupValues[1].toDoubleOrNull() ?: 1.0
                val denominator = match.groupValues[2].toDoubleOrNull()?.takeIf { it != 0.0 } ?: 1.0
                val unit = match.groupValues[3].trim().ifEmpty { "unit" }
                return (numerator / denominator) to unit
            }

            NUMBER_PREFIX_REGEX.matchEntire(trimmed)?.let { match ->
                val value = match.groupValues[1].toDoubleOrNull() ?: 1.0
                val unit = match.groupValues[2].trim().ifEmpty { "unit" }
                return value to unit
            }

            return 1.0 to trimmed
        }

        /**
         * Maps a rich [PantryItemEntity] into the canonical [Ingredient] Room entity.
         */
        fun fromPantryItemEntity(item: PantryItemEntity): Ingredient {
            val (parsedQty, parsedUnit) = parseQuantityAndUnit(item.quantity)
            val resolvedExpiry = if (item.expiryDateEpochMillis > 0L) {
                item.expiryDateEpochMillis
            } else {
                computeExpiryEpochMillis(item.daysUntilExpiry)
            }
            return Ingredient(
                id = item.id,
                name = item.name,
                quantity = parsedQty,
                unit = parsedUnit,
                expiryDate = resolvedExpiry
            )
        }
    }
}
