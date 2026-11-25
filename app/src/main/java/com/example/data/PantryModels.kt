package com.example.data

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

const val MILLIS_PER_DAY: Long = 86_400_000L

fun startOfDayEpochMillis(epochMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

fun computeExpiryEpochMillis(
    daysUntilExpiry: Int,
    fromNowMillis: Long = System.currentTimeMillis()
): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = startOfDayEpochMillis(fromNowMillis)
        add(Calendar.DAY_OF_YEAR, daysUntilExpiry)
    }
    return cal.timeInMillis
}

fun computeDaysUntilExpiry(
    expiryDateEpochMillis: Long,
    fromNowMillis: Long = System.currentTimeMillis()
): Int {
    val todayStart = startOfDayEpochMillis(fromNowMillis)
    val expiryStart = startOfDayEpochMillis(expiryDateEpochMillis)
    return ((expiryStart - todayStart).toDouble() / MILLIS_PER_DAY.toDouble()).roundToInt()
}

fun formatExpiryDate(expiryDateEpochMillis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(expiryDateEpochMillis))
}

enum class FreshnessStatus(
    val label: String,
    val badgeText: String,
    @StringRes val labelRes: Int,
    @StringRes val badgeRes: Int
) {
    CRITICAL("Use Today / Tomorrow", "CRITICAL", R.string.freshness_critical, R.string.freshness_critical_badge),
    EXPIRING_SOON("Expiring Soon", "SOON", R.string.freshness_soon, R.string.freshness_soon_badge),
    FRESH("Fresh", "FRESH", R.string.freshness_fresh, R.string.freshness_fresh_badge);

    companion object {
        fun fromDays(days: Int): FreshnessStatus = when {
            days <= 1 -> CRITICAL
            days <= 3 -> EXPIRING_SOON
            else -> FRESH
        }
    }
}

enum class PantryCategory(
    val displayName: String,
    val emoji: String,
    @StringRes val displayNameRes: Int
) {
    PRODUCE("Produce & Greens", "🥬", R.string.category_produce),
    DAIRY_CHEESE("Dairy & Cheese", "🧀", R.string.category_dairy),
    SAUCES_JARS("Half-Used Sauces & Jars", "🫙", R.string.category_sauces),
    PROTEIN("Proteins & Deli", "🍳", R.string.category_protein),
    LEFTOVERS("Cooked Leftovers", "🥡", R.string.category_leftovers),
    PANTRY_STAPLE("Dry Pantry", "🌾", R.string.category_dry_pantry);

    companion object {
        fun fromString(raw: String): PantryCategory {
            val upper = raw.uppercase()
            return entries.firstOrNull { it.name == upper || upper.contains(it.name.take(5)) }
                ?: when {
                    upper.contains("VEG") || upper.contains("FRUIT") || upper.contains("HERB") || upper.contains("PRODUCE") -> PRODUCE
                    upper.contains("CHEESE") || upper.contains("MILK") || upper.contains("YOGURT") || upper.contains("DAIRY") -> DAIRY_CHEESE
                    upper.contains("SAUCE") || upper.contains("CONDIMENT") || upper.contains("JAR") || upper.contains("PASTE") -> SAUCES_JARS
                    upper.contains("MEAT") || upper.contains("EGG") || upper.contains("TOFU") || upper.contains("PROTEIN") -> PROTEIN
                    upper.contains("LEFTOVER") -> LEFTOVERS
                    else -> PRODUCE
                }
        }
    }
}

enum class RecipeCategoryFilter(
    val displayName: String,
    val shortBadge: String,
    val aiPromptInstruction: String,
    @StringRes val displayNameRes: Int,
    @StringRes val shortBadgeRes: Int
) {
    QUICK_EASY(
        displayName = "Quick & Easy",
        shortBadge = "⚡ Quick & Easy",
        aiPromptInstruction = "Quick & Easy (20 minutes or less total prep+cook time, minimal dishes, simple techniques)",
        displayNameRes = R.string.recipe_cat_quick,
        shortBadgeRes = R.string.recipe_cat_quick_badge
    ),
    HEALTHY_LOW_CAL(
        displayName = "Healthy/Low-Calorie",
        shortBadge = "🥗 Healthy/Low-Cal",
        aiPromptInstruction = "Healthy/Low-Calorie (under 450 calories per serving, nutrient-dense, high vegetable & lean protein ratio, light oil)",
        displayNameRes = R.string.recipe_cat_healthy,
        shortBadgeRes = R.string.recipe_cat_healthy_badge
    ),
    BUDGET_FRIENDLY(
        displayName = "Budget-Friendly",
        shortBadge = "💰 Budget-Friendly",
        aiPromptInstruction = "Budget-Friendly (under $2.50 per serving, stretches expiring ingredients with pantry staples for maximum value)",
        displayNameRes = R.string.recipe_cat_budget,
        shortBadgeRes = R.string.recipe_cat_budget_badge
    );

    companion object {
        fun fromString(raw: String): RecipeCategoryFilter {
            val upper = raw.uppercase()
            return when {
                upper.contains("QUICK") || upper.contains("EASY") -> QUICK_EASY
                upper.contains("HEALTH") || upper.contains("CAL") || upper.contains("LIGHT") -> HEALTHY_LOW_CAL
                upper.contains("BUDGET") || upper.contains("CHEAP") || upper.contains("VALUE") -> BUDGET_FRIENDLY
                else -> QUICK_EASY
            }
        }
    }
}

enum class RecipeDietaryPreference(
    val displayName: String,
    val badgeLabel: String,
    val aiPromptInstruction: String,
    @StringRes val displayNameRes: Int,
    @StringRes val badgeLabelRes: Int
) {
    VEGETARIAN(
        displayName = "Vegetarian",
        badgeLabel = "🌱 Vegetarian",
        aiPromptInstruction = "Vegetarian (strictly meat-free and poultry-free; may use produce, eggs, cheese, legumes, tofu)",
        displayNameRes = R.string.dietary_vegetarian,
        badgeLabelRes = R.string.dietary_vegetarian_badge
    ),
    HIGH_PROTEIN(
        displayName = "High-Protein",
        badgeLabel = "💪 High-Protein",
        aiPromptInstruction = "High-Protein (emphasizes protein-rich ingredients like eggs, cheese, Greek yogurt, tofu, chicken, or beans)",
        displayNameRes = R.string.dietary_high_protein,
        badgeLabelRes = R.string.dietary_high_protein_badge
    ),
    GLUTEN_FREE(
        displayName = "Gluten-Free",
        badgeLabel = "🌾 Gluten-Free",
        aiPromptInstruction = "Gluten-Free (naturally gluten-free ingredients; no wheat bread, pasta, or flour)",
        displayNameRes = R.string.dietary_gluten_free,
        badgeLabelRes = R.string.dietary_gluten_free_badge
    ),
    LOW_CARB(
        displayName = "Low-Carb",
        badgeLabel = "🥑 Low-Carb",
        aiPromptInstruction = "Low-Carb / Keto-Friendly (skillet, frittata, or bowl focused on greens, vegetables, eggs, proteins, and cheeses without heavy starches)",
        displayNameRes = R.string.dietary_low_carb,
        badgeLabelRes = R.string.dietary_low_carb_badge
    ),
    PLANT_FORWARD(
        displayName = "Plant-Forward",
        badgeLabel = "🥬 Plant-Forward",
        aiPromptInstruction = "Plant-Forward (90%+ vegetables, herbs, sauces, and legumes)",
        displayNameRes = R.string.dietary_plant_forward,
        badgeLabelRes = R.string.dietary_plant_forward_badge
    )
}

enum class ScanCaptureMode(
    val title: String,
    val subtitle: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int
) {
    FRIDGE_SHELF(
        title = "Fridge / Pantry Shelf",
        subtitle = "Detect open produce, cheeses & half-used sauces",
        titleRes = R.string.scan_mode_fridge_title,
        subtitleRes = R.string.scan_mode_fridge_subtitle
    ),
    GROCERY_RECEIPT(
        title = "Grocery Receipt / Label",
        subtitle = "Import purchased items with auto shelf-life countdowns",
        titleRes = R.string.scan_mode_receipt_title,
        subtitleRes = R.string.scan_mode_receipt_subtitle
    )
}

@Entity(
    tableName = "pantry_items",
    indices = [
        Index(value = ["isConsumed", "expiryDateEpochMillis"]),
        Index(value = ["name"]),
        Index(value = ["stockPercent"])
    ]
)
data class PantryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val quantity: String,
    val daysUntilExpiry: Int,
    val expiryDateEpochMillis: Long = computeExpiryEpochMillis(daysUntilExpiry),
    val visualConditionNote: String,
    val storageTip: String,
    val estimatedValueDollars: Double = 3.50,
    val stockPercent: Int = 100,
    val lowStockThresholdPercent: Int = 30,
    val autoAddToShoppingList: Boolean = true,
    val isConsumed: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = addedTimestamp
) {
    /**
     * Real-time countdown computed from the persisted calendar expiryDateEpochMillis
     * so offline inventory stays accurate across day rollovers.
     */
    val effectiveDaysUntilExpiry: Int
        get() = if (expiryDateEpochMillis > 0L) {
            computeDaysUntilExpiry(expiryDateEpochMillis)
        } else {
            daysUntilExpiry
        }

    val formattedExpiryDate: String
        get() = formatExpiryDate(
            if (expiryDateEpochMillis > 0L) expiryDateEpochMillis else computeExpiryEpochMillis(daysUntilExpiry)
        )

    val isExpired: Boolean
        get() = effectiveDaysUntilExpiry < 0

    val freshnessStatus: FreshnessStatus
        get() = FreshnessStatus.fromDays(effectiveDaysUntilExpiry)

    val parsedCategory: PantryCategory
        get() = PantryCategory.fromString(category)

    val isLowStock: Boolean
        get() = stockPercent <= lowStockThresholdPercent
}

@Entity(
    tableName = "restock_items",
    indices = [
        Index(value = ["isChecked", "addedTimestamp"]),
        Index(value = ["sourcePantryItemId"])
    ]
)
data class RestockItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val suggestedQuantity: String,
    val defaultExpiryDays: Int,
    val zeroWasteBuyingTip: String,
    val estimatedCostDollars: Double = 3.50,
    val triggerReason: String = "Auto-Added: Low Stock Threshold",
    val isAutoAdded: Boolean = true,
    val isChecked: Boolean = false,
    val sourcePantryItemId: Long? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
) {
    val parsedCategory: PantryCategory
        get() = PantryCategory.fromString(category)
}

@Entity(tableName = "saved_recipes")
data class SavedRecipeEntity(
    @PrimaryKey val id: String,
    val rank: Int,
    val title: String,
    val recipeCategory: String,
    val rescueScore: Int,
    val prepTimeMinutes: Int,
    val servings: Int,
    val caloriesPerServing: Int,
    val estimatedCostPerServing: Double,
    val applianceUsed: String,
    val whyItRescues: String,
    val expiringIngredientsPipe: String,
    val pantryIngredientsPipe: String,
    val spicesUsedPipe: String,
    val stepsPipe: String,
    val chefTip: String,
    val generatedTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomain(): RecipeSuggestion = RecipeSuggestion(
        id = id,
        rank = rank,
        title = title,
        recipeCategory = RecipeCategoryFilter.fromString(recipeCategory),
        rescueScore = rescueScore,
        prepTimeMinutes = prepTimeMinutes,
        servings = servings,
        caloriesPerServing = caloriesPerServing,
        estimatedCostPerServing = estimatedCostPerServing,
        applianceUsed = applianceUsed,
        whyItRescues = whyItRescues,
        expiringIngredientsUsed = expiringIngredientsPipe.split("||").filter { it.isNotBlank() },
        pantryIngredientsUsed = pantryIngredientsPipe.split("||").filter { it.isNotBlank() },
        spicesUsed = spicesUsedPipe.split("||").filter { it.isNotBlank() },
        steps = stepsPipe.split("||").filter { it.isNotBlank() },
        chefTip = chefTip
    )
}

data class RecipeSuggestion(
    val id: String,
    val rank: Int,
    val title: String,
    val recipeCategory: RecipeCategoryFilter = RecipeCategoryFilter.QUICK_EASY,
    val rescueScore: Int,
    val prepTimeMinutes: Int,
    val servings: Int,
    val caloriesPerServing: Int = 380,
    val estimatedCostPerServing: Double = 2.15,
    val applianceUsed: String,
    val whyItRescues: String,
    val expiringIngredientsUsed: List<String>,
    val pantryIngredientsUsed: List<String>,
    val spicesUsed: List<String>,
    val steps: List<String>,
    val chefTip: String
) {
    private val combinedCorpusLower: String
        get() = buildString {
            append(title).append(" ")
            append(whyItRescues).append(" ")
            append(chefTip).append(" ")
            expiringIngredientsUsed.forEach { append(it).append(" ") }
            pantryIngredientsUsed.forEach { append(it).append(" ") }
            spicesUsed.forEach { append(it).append(" ") }
            steps.forEach { append(it).append(" ") }
        }.lowercase()

    val inferredDietaryBadges: List<RecipeDietaryPreference>
        get() = RecipeDietaryPreference.entries.filter { matchesDietaryPreference(it) }

    fun matchesSearchQuery(query: String): Boolean {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return true
        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        return tokens.all { token ->
            combinedCorpusLower.contains(token) || applianceUsed.lowercase().contains(token)
        }
    }

    fun matchesDietaryPreference(pref: RecipeDietaryPreference): Boolean {
        val corpus = combinedCorpusLower
        val hasMeatKeywords = listOf(
            "chicken", "beef", "pork", "bacon", "sausage", "turkey",
            "salmon", "shrimp", "fish", "tuna", "ham", "prosciutto", "lamb", "steak", "anchovy"
        ).any { corpus.contains(it) }
        val hasGlutenKeywords = listOf("bread", "toast", "sourdough", "pasta", "noodle", "flour", "tortilla")
            .any { corpus.contains(it) }
        val hasProteinKeywords = listOf("egg", "frittata", "shakshuka", "cheddar", "feta", "cheese", "tofu", "chicken", "yogurt", "chickpea")
            .any { corpus.contains(it) }

        return when (pref) {
            RecipeDietaryPreference.VEGETARIAN -> !hasMeatKeywords || corpus.contains("vegetarian")
            RecipeDietaryPreference.HIGH_PROTEIN -> hasProteinKeywords || corpus.contains("high-protein")
            RecipeDietaryPreference.GLUTEN_FREE -> !hasGlutenKeywords || corpus.contains("gluten-free")
            RecipeDietaryPreference.LOW_CARB -> (!hasGlutenKeywords && !corpus.contains("rice")) || recipeCategory == RecipeCategoryFilter.HEALTHY_LOW_CAL || corpus.contains("low-carb")
            RecipeDietaryPreference.PLANT_FORWARD -> expiringIngredientsUsed.size >= 2 && !hasMeatKeywords
        }
    }

    fun matchesAvailableIngredient(pantryItemName: String): Boolean {
        val allRecipeIngs = (expiringIngredientsUsed + pantryIngredientsUsed).joinToString(" ").lowercase()
        val targetLower = pantryItemName.trim().lowercase()
        if (allRecipeIngs.contains(targetLower)) return true
        val significantWords = targetLower.split(" ", "-", "/")
            .map { it.trim() }
            .filter { it.length >= 4 && it !in setOf("half", "pack", "clamshell", "jar", "tube", "wedge", "fresh") }
        return significantWords.isNotEmpty() && significantWords.any { word ->
            allRecipeIngs.contains(word) || title.lowercase().contains(word)
        }
    }

    fun countMatchedPantryItems(pantryItems: List<PantryItemEntity>): Int {
        return pantryItems.count { matchesAvailableIngredient(it.name) }
    }

    fun toEntity(): SavedRecipeEntity = SavedRecipeEntity(
        id = id,
        rank = rank,
        title = title,
        recipeCategory = recipeCategory.name,
        rescueScore = rescueScore,
        prepTimeMinutes = prepTimeMinutes,
        servings = servings,
        caloriesPerServing = caloriesPerServing,
        estimatedCostPerServing = estimatedCostPerServing,
        applianceUsed = applianceUsed,
        whyItRescues = whyItRescues,
        expiringIngredientsPipe = expiringIngredientsUsed.joinToString("||"),
        pantryIngredientsPipe = pantryIngredientsUsed.joinToString("||"),
        spicesUsedPipe = spicesUsed.joinToString("||"),
        stepsPipe = steps.joinToString("||"),
        chefTip = chefTip
    )
}

data class DetectedIngredient(
    val name: String,
    val category: PantryCategory,
    val quantity: String,
    val daysUntilExpiry: Int,
    val visualConditionNote: String,
    val storageTip: String,
    val estimatedValueDollars: Double,
    val isSelected: Boolean = true
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PreservationMethodType(
    val label: String,
    val badgeEmoji: String,
    val defaultBonusDays: Int,
    @StringRes val labelRes: Int
) {
    FREEZE_LOCK("Freeze & Lock", "❄️", 45, R.string.preservation_method_freeze),
    BLANCH_FREEZE("Blanch & Ice Shock", "♨️", 60, R.string.preservation_method_blanch),
    HERB_OIL_CUBE("Oil / Pesto Ice Cube", "🧊", 30, R.string.preservation_method_herb_cube),
    QUICK_PICKLE("Quick Brine / Pickle", "🫙", 21, R.string.preservation_method_pickle),
    HYDRO_CRISPER_WRAP("Paper-Towel Hydro Wrap", "🥬", 6, R.string.preservation_method_hydro_wrap),
    AIRTIGHT_OIL_SEAL("Wax Wrap / Oil Seal", "🔒", 14, R.string.preservation_method_oil_seal);

    companion object {
        fun fromString(raw: String): PreservationMethodType {
            val upper = raw.uppercase()
            return entries.firstOrNull { it.name == upper }
                ?: when {
                    upper.contains("BLANCH") || upper.contains("SHOCK") -> BLANCH_FREEZE
                    upper.contains("CUBE") || upper.contains("ICE") || upper.contains("TRAY") -> HERB_OIL_CUBE
                    upper.contains("PICKLE") || upper.contains("BRINE") || upper.contains("VINEGAR") -> QUICK_PICKLE
                    upper.contains("TOWEL") || upper.contains("HYDRO") || upper.contains("CRISPER") -> HYDRO_CRISPER_WRAP
                    upper.contains("SEAL") || upper.contains("WAX") || upper.contains("PARCHMENT") -> AIRTIGHT_OIL_SEAL
                    else -> FREEZE_LOCK
                }
        }
    }
}

data class PreservationGuide(
    val id: String,
    val itemId: Long,
    val ingredientName: String,
    val category: PantryCategory,
    val currentDaysLeft: Int,
    val extendedShelfLifeDays: Int,
    val methodType: PreservationMethodType,
    val urgencyHeadline: String,
    val personalizedStorageTip: String,
    val preservationSteps: List<String>,
    val bestUseAfterPreserving: String,
    val estimatedSavingsDollars: Double,
    val isApplied: Boolean = false
)

