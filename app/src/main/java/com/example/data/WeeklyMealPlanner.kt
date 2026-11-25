package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MealPlanDay(
    val dayIndex: Int,
    val dayLabel: String,
    val mealTitle: String,
    val appliance: String,
    val prepMinutes: Int,
    val rescuedIngredients: List<String>,
    val missingStaples: List<String>,
    val urgencySummary: String
)

data class WeeklyMealPlan(
    val daysCount: Int,
    val days: List<MealPlanDay>,
    val totalAtRiskDollarsRescued: Double,
    val consolidatedMissingStaples: List<String>
)

/**
 * Deterministic, zero-cost Weekly Zero-Waste Meal Plan generator (3 to 7 days).
 * Schedules the most urgent expiring items (<=1d) on Day 1, soon-expiring items (2-3d) on Day 2-3,
 * and longer-life fridge items across remaining days while identifying minimal missing staples
 * to sync directly into the Smart Shopping List.
 */
object WeeklyMealPlanner {

    private val baseStapleSuggestions = mapOf(
        PantryCategory.PRODUCE to listOf("Garlic Cloves", "Lemon"),
        PantryCategory.DAIRY_CHEESE to listOf("Whole-Grain Bread", "Eggs"),
        PantryCategory.SAUCES_JARS to listOf("Dry Pasta", "Brown Rice"),
        PantryCategory.PROTEIN to listOf("Mixed Salad Greens", "Tortillas"),
        PantryCategory.LEFTOVERS to listOf("Fresh Scallions", "Broth"),
        PantryCategory.PANTRY_STAPLE to listOf("Seasonal Vegetables", "Olive Oil")
    )

    fun buildZeroWastePlan(
        pantryItems: List<PantryItemEntity>,
        kitchenSetup: KitchenSetupState,
        daysCount: Int = 5
    ): WeeklyMealPlan {
        val clampedDays = daysCount.coerceIn(3, 7)
        if (pantryItems.isEmpty()) {
            return WeeklyMealPlan(
                daysCount = clampedDays,
                days = emptyList(),
                totalAtRiskDollarsRescued = 0.0,
                consolidatedMissingStaples = emptyList()
            )
        }
        val sortedByUrgency = pantryItems.sortedWith(
            compareBy<PantryItemEntity> { it.effectiveDaysUntilExpiry }
                .thenByDescending { it.estimatedValueDollars }
        )

        val appliances = kitchenSetup.ownedAppliances.toList().ifEmpty { listOf("Stovetop Skillet") }
        val existingNamesLower = pantryItems.map { it.name.lowercase() }
        val spicesLower = kitchenSetup.ownedSpices.map { it.lowercase() }

        val cal = Calendar.getInstance()
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())

        val days = mutableListOf<MealPlanDay>()
        val allMissing = linkedSetOf<String>()
        var rescuedDollars = 0.0

        for (dayIdx in 0 until clampedDays) {
            val dayLabel = when (dayIdx) {
                0 -> "Today (${dayFmt.format(cal.time)})"
                1 -> "Tomorrow (${dayFmt.format(cal.time)})"
                else -> "Day ${dayIdx + 1} (${dayFmt.format(cal.time)})"
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)

            val start = (dayIdx * 2) % sortedByUrgency.size
            val first = sortedByUrgency[start]
            val second = sortedByUrgency.getOrNull((start + 1) % sortedByUrgency.size)
                ?.takeIf { it.id != first.id }
            val slice = listOfNotNull(first, second)

            if (dayIdx < 3) {
                rescuedDollars += slice.filter { it.effectiveDaysUntilExpiry <= 3 }
                    .sumOf { it.estimatedValueDollars }
            }

            val primaryItem = slice.first()
            val appliance = appliances[dayIdx % appliances.size]
            val title = buildMealTitle(primaryItem, slice.getOrNull(1), appliance, dayIdx)
            val urgencyNote = when {
                primaryItem.effectiveDaysUntilExpiry <= 1 ->
                    "Priority rescue: uses ${primaryItem.name} expiring today"
                primaryItem.effectiveDaysUntilExpiry <= 3 ->
                    "Uses ${primaryItem.name} before its ${primaryItem.effectiveDaysUntilExpiry}d window closes"
                else ->
                    "Rotates ${primaryItem.name} while fresh to keep weekly waste at 0%"
            }

            val missingForDay = mutableListOf<String>()
            val primaryCat = primaryItem.parsedCategory
            val candidateStaples = baseStapleSuggestions[primaryCat].orEmpty()
            candidateStaples.forEach { staple ->
                val alreadyOwned = existingNamesLower.any { it.contains(staple.lowercase()) } ||
                    spicesLower.any { it.contains(staple.lowercase()) }
                if (!alreadyOwned && missingForDay.size < 1) {
                    missingForDay.add(staple)
                    allMissing.add(staple)
                }
            }

            days.add(
                MealPlanDay(
                    dayIndex = dayIdx + 1,
                    dayLabel = dayLabel,
                    mealTitle = title,
                    appliance = appliance,
                    prepMinutes = 15 + (dayIdx % 3) * 5,
                    rescuedIngredients = slice.map { "${it.name} (${it.effectiveDaysUntilExpiry}d)" },
                    missingStaples = missingForDay,
                    urgencySummary = urgencyNote
                )
            )
        }

        return WeeklyMealPlan(
            daysCount = clampedDays,
            days = days,
            totalAtRiskDollarsRescued = rescuedDollars,
            consolidatedMissingStaples = allMissing.toList()
        )
    }

    private fun buildMealTitle(
        primary: PantryItemEntity,
        secondary: PantryItemEntity?,
        appliance: String,
        dayIdx: Int
    ): String {
        val firstShort = primary.name.split(" ").take(2).joinToString(" ")
        val secondShort = secondary?.name?.split(" ")?.take(2)?.joinToString(" ")
        val style = when (dayIdx % 5) {
            0 -> "Rescue Skillet"
            1 -> "Zero-Waste Bowl"
            2 -> "15-Min Frittata"
            3 -> "Crispy $appliance Toss"
            else -> "Savory Herb Stir-Fry"
        }
        return if (secondShort != null) {
            "$firstShort & $secondShort $style"
        } else {
            "$firstShort $style"
        }
    }
}
