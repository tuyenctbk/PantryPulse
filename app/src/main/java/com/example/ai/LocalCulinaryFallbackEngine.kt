package com.example.ai

import android.graphics.Bitmap
import com.example.data.DetectedIngredient
import com.example.data.KitchenSetupState
import com.example.data.PantryCategory
import com.example.data.PantryItemEntity
import com.example.data.PreservationGuide
import com.example.data.PreservationMethodType
import com.example.data.RecipeCategoryFilter
import com.example.data.RecipeSuggestion
import com.example.data.ScanCaptureMode
import java.util.Locale
import java.util.UUID

/**
 * Tier 3 — Zero-Cost Local / Deterministic Fallback Engine.
 * Guarantees users never lose progress or hit a hard wall when offline or at 0 cloud quota.
 */
object LocalCulinaryFallbackEngine {

    /**
     * Tier 3 Vision Fallback: Parses any user-provided focus notes into structured items
     * without fabricating placeholder groceries when offline or at 0 cloud quota.
     */
    fun analyzeShelfLocally(
        bitmaps: List<Bitmap>,
        captureMode: ScanCaptureMode,
        customFocusNote: String
    ): Pair<String, List<DetectedIngredient>> {
        val noteItems = parseCustomNoteIngredients(customFocusNote)
        val summary = when (captureMode) {
            ScanCaptureMode.FRIDGE_SHELF ->
                "Tier 3 Local Shelf Check-In (0 cloud credits used • Parsed from your focus note)."
            ScanCaptureMode.GROCERY_RECEIPT ->
                "Tier 3 On-Device Receipt Check-In (0 cloud credits • Parsed from your note)."
        }
        return summary to noteItems
    }

    /**
     * Tier 3 Recipe Synthesis Engine: Deterministically builds 3 ranked zero-waste recipes
     * from the user's actual expiring Room inventory, owned appliances, and spices (0 cloud tokens).
     */
    fun synthesizeRecipesLocally(
        items: List<PantryItemEntity>,
        kitchenSetup: KitchenSetupState,
        selectedCategories: Set<RecipeCategoryFilter>,
        cravingNote: String
    ): List<RecipeSuggestion> {
        if (items.isEmpty()) return emptyList()

        val sortedByUrgency = items.sortedBy { it.effectiveDaysUntilExpiry }
        val urgent = sortedByUrgency.filter { it.effectiveDaysUntilExpiry <= 3 }.ifEmpty { sortedByUrgency.take(3) }
        val supporting = sortedByUrgency.drop(urgent.size).take(3)

        val leadIng = urgent.first().name
        val secondIng = urgent.getOrNull(1)?.name ?: supporting.firstOrNull()?.name ?: leadIng
        val thirdIng = urgent.getOrNull(2)?.name ?: supporting.getOrNull(1)?.name ?: secondIng

        val primaryAppliance = kitchenSetup.ownedAppliances.firstOrNull() ?: "Stovetop Skillet"
        val secondaryAppliance = kitchenSetup.ownedAppliances.drop(1).firstOrNull() ?: primaryAppliance
        val spices = kitchenSetup.ownedSpices.take(3).ifEmpty { listOf("Olive Oil", "Black Pepper") }

        val targetCats = if (selectedCategories.isEmpty()) {
            listOf(
                RecipeCategoryFilter.QUICK_EASY,
                RecipeCategoryFilter.HEALTHY_LOW_CAL,
                RecipeCategoryFilter.BUDGET_FRIENDLY
            )
        } else {
            val list = selectedCategories.toList()
            List(3) { idx -> list[idx % list.size] }
        }

        val cravingSuffix = if (cravingNote.isNotBlank()) " (${cravingNote.trim()})" else ""

        return listOf(
            RecipeSuggestion(
                id = UUID.randomUUID().toString(),
                rank = 1,
                title = "Zero-Waste $leadIng & $secondIng Skillet Hash$cravingSuffix",
                recipeCategory = targetCats[0],
                rescueScore = 97,
                prepTimeMinutes = 14,
                servings = 2,
                caloriesPerServing = 325,
                estimatedCostPerServing = 1.65,
                applianceUsed = primaryAppliance,
                whyItRescues = "Synthesized on-device (0 cloud credits): Rescues your most urgent items ($leadIng & $secondIng) before spoilage.",
                expiringIngredientsUsed = urgent.take(3).map { "${it.name} (${it.quantity})" },
                pantryIngredientsUsed = supporting.take(2).map { "${it.name} (${it.quantity})" }.ifEmpty { listOf("Pantry Aromatics") },
                spicesUsed = spices,
                steps = listOf(
                    "Preheat your $primaryAppliance over medium-high heat with ${spices.firstOrNull() ?: "Olive Oil"}.",
                    "Add $secondIng first and sear for 3–4 minutes until golden and fragrant.",
                    "Fold in $leadIng and $thirdIng along with ${spices.drop(1).joinToString(" and ").ifBlank { "a pinch of salt & pepper" }}; toss for 2 minutes until tender.",
                    "Plate warm and finish with any remaining herbs or cheese ends."
                ),
                chefTip = "On-Device Chef Tip: High heat evaporates excess moisture from aging produce, concentrating savory flavor."
            ),
            RecipeSuggestion(
                id = UUID.randomUUID().toString(),
                rank = 2,
                title = "Crispy $secondaryAppliance $secondIng & Greens Melt$cravingSuffix",
                recipeCategory = targetCats[1],
                rescueScore = 91,
                prepTimeMinutes = 12,
                servings = 2,
                caloriesPerServing = 340,
                estimatedCostPerServing = 1.80,
                applianceUsed = secondaryAppliance,
                whyItRescues = "Synthesized on-device (0 cloud credits): Transforms $secondIng and $leadIng into a quick crispy meal.",
                expiringIngredientsUsed = urgent.take(2).map { "${it.name} (${it.quantity})" },
                pantryIngredientsUsed = supporting.take(2).map { it.name }.ifEmpty { listOf("Crisp Base / Toast") },
                spicesUsed = spices,
                steps = listOf(
                    "Toss $leadIng and $secondIng with ${spices.joinToString(", ")}.",
                    "Arrange in your $secondaryAppliance and cook for 6–8 minutes until bubbling and crisp around the edges.",
                    "Rest 2 minutes before slicing so the melted interior sets cleanly."
                ),
                chefTip = "On-Device Chef Tip: Wilting greens shrink by 70% when cooked—pack in double the volume!"
            ),
            RecipeSuggestion(
                id = UUID.randomUUID().toString(),
                rank = 3,
                title = "Rustic Pan-Glazed $thirdIng & Vegetable Bowl$cravingSuffix",
                recipeCategory = targetCats[2],
                rescueScore = 86,
                prepTimeMinutes = 15,
                servings = 3,
                caloriesPerServing = 310,
                estimatedCostPerServing = 1.35,
                applianceUsed = primaryAppliance,
                whyItRescues = "Synthesized on-device (0 cloud credits): Uses up jar ends ($thirdIng) and remaining produce for $1.35/serving.",
                expiringIngredientsUsed = urgent.map { it.name },
                pantryIngredientsUsed = supporting.map { it.name }.ifEmpty { listOf("2 tbsp warm water for deglazing") },
                spicesUsed = spices,
                steps = listOf(
                    "Sauté chopped $leadIng and $secondIng in your $primaryAppliance for 4 minutes.",
                    "Swirl in 2 tbsp water into your half-used $thirdIng jar, shake to capture every drop, and pour into the pan to deglaze.",
                    "Simmer 2 minutes with ${spices.joinToString(" + ")} until glossy and serve immediately."
                ),
                chefTip = "On-Device Chef Tip: Shaking a splash of warm water or broth inside an almost-empty sauce jar rescues 100% of the flavor."
            )
        )
    }

    /**
     * Tier 3 Instant Local Reflex Engine for Sous-Chef Chat (0 cloud tokens).
     */
    fun generateLocalReflexReply(
        userMessage: String,
        items: List<PantryItemEntity>,
        kitchenSetup: KitchenSetupState
    ): String {
        val urgentItems = items.filter { it.effectiveDaysUntilExpiry <= 3 }
        val topUrgent = urgentItems.firstOrNull() ?: items.firstOrNull()
        val appliance = kitchenSetup.ownedAppliances.firstOrNull() ?: "Stovetop Skillet"
        val spices = kitchenSetup.ownedSpices.take(3).joinToString(", ")
        val lower = userMessage.lowercase()

        return buildString {
            append("🛡️ **[Local Reflex Chef • 0 Cloud Credits Used]**\n\n")
            when {
                lower.contains("sauce") || lower.contains("dressing") || lower.contains("pesto") -> {
                    append("Here is a **5-Minute Zero-Waste Pan Sauce** using your current inventory:\n")
                    append("1. **Base**: Use **${topUrgent?.name ?: "your expiring greens/tomatoes"}** (${topUrgent?.quantity ?: "1 cup"}) finely chopped.\n")
                    append("2. **Deglaze**: Warm 1 tbsp olive oil in your **$appliance** with **$spices** for 90 seconds.\n")
                    append("3. **Emulsify**: Swirl 2 tbsp warm water inside any half-used condiment jar in your fridge door and whisk into the pan until glossy!")
                }
                lower.contains("wilted") || lower.contains("revive") || lower.contains("save") || lower.contains("store") -> {
                    append("To **revive & rescue aging produce** right now:\n")
                    append("• **Ice Bath Shock (10 mins)**: Submerge wilted leafy greens or herbs in ice-cold water with a squeeze of lemon to restore cell turgor.\n")
                    if (topUrgent != null) {
                        append("• **Priority Item (${topUrgent.name} — ${topUrgent.effectiveDaysUntilExpiry}d left)**: ${topUrgent.storageTip}\n")
                    }
                    append("• **Blister & Freeze**: Sauté with **$spices** in your **$appliance** for 2 minutes and freeze in portion cubes.")
                }
                lower.contains("crisp") || lower.contains("air fryer") || lower.contains("skillet") -> {
                    append("To get maximum crispness in your **$appliance**:\n")
                    append("1. **Dry Surface First**: Pat **${topUrgent?.name ?: "your vegetables"}** dry or pre-sauté for 2 minutes so steam doesn't make cheese soggy.\n")
                    append("2. **Spice Crust**: Toss lightly with **$spices**.\n")
                    append("3. **High-Heat Finish**: Cook in a single uncrowded layer for 5–7 minutes until golden brown.")
                }
                lower.contains("substitute") || lower.contains("swap") || lower.contains("replace") || lower.contains("instead of") -> {
                    val creamyDairy = items.firstOrNull { item ->
                        val n = item.name.lowercase()
                        item.parsedCategory == PantryCategory.DAIRY_CHEESE &&
                            (n.contains("yogurt") || n.contains("milk") || n.contains("cream") || n.contains("kefir"))
                    }
                    val sauceItem = items.firstOrNull { it.parsedCategory == PantryCategory.SAUCES_JARS }
                    append("Here are **1:1 Chef Substitutions** using what's already in your fridge:\n")
                    append("• **Missing Heavy Cream / Buttermilk?** Whisk **${creamyDairy?.name ?: "Greek yogurt or sour cream"}** with 2 tbsp warm water or milk (1:1 swap).\n")
                    append("• **Missing Fresh Herbs or Acid?** Swirl **1 tbsp of ${sauceItem?.name ?: "jarred pesto, salsa, or pickle brine"}** into the pan at the final minute.\n")
                    append("• **Missing Eggs for Binding?** Use **1 tbsp olive oil + 2 tbsp starchy pasta water or mashed avocado**.")
                }
                lower.contains("safe") || lower.contains("spoil") || lower.contains("bad") || lower.contains("smell") || lower.contains("mold") || lower.contains("fact") -> {
                    append("🔬 **Zero-Waste Food Safety & Spoilage Fact-Check (USDA Guidelines)**:\n")
                    if (topUrgent != null) {
                        append("• **Inspecting ${topUrgent.name} (${topUrgent.effectiveDaysUntilExpiry}d left)**: ")
                    } else {
                        append("• **General Shelf Inspection**: ")
                    }
                    append("Wilting, slight wrinkling, or surface condensation on produce is **100% safe to cook** at high heat (≥165°F)—just trim bruised edges.\n")
                    append("• **When to Discard Immediately**: Slimy film on leafy greens/meats, sour/ammonia odor, bulging lids, or fuzzy mold on soft cheeses/sauces.\n")
                    append("• **Hard Block Cheeses**: You can safely slice 1 inch around a surface spot on hard cheddar/parmesan and use the rest.")
                }
                else -> {
                    append("Based on your offline fridge inventory (**${items.size} items**, **${urgentItems.size} expiring within 72h**):\n")
                    urgentItems.take(3).forEach { item ->
                        append("• **${item.name}** (${item.quantity}, ${item.effectiveDaysUntilExpiry}d left): *${item.visualConditionNote}*\n")
                    }
                    append("\n**Fastest Tonight Plan ($appliance)**:\n")
                    append("1. Sear **${urgentItems.getOrNull(0)?.name ?: "your top produce"}** and **${urgentItems.getOrNull(1)?.name ?: "proteins/cheese"}** in your **$appliance**.\n")
                    append("2. Season with **$spices** and finish in under 15 minutes for zero food waste!")
                }
            }
        }
    }

    /**
     * Tier 3 Voice-to-Inventory Local Fallback Parser (0 cloud tokens).
     * Parses spoken grocery dictation phrases like:
     * "Half bag of baby spinach expiring in 2 days, 6 eggs for a week, and half jar of pesto for 3 days"
     * into structured DetectedIngredient items when cloud quota is exhausted or offline.
     */
    fun parseVoiceTranscriptLocally(transcript: String): List<DetectedIngredient> {
        if (transcript.isBlank()) return emptyList()
        val clauses = transcript
            .split(Regex("[,;]|\\band\\b|\\bplus\\b|\\balso\\b", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.length >= 3 }

        val daysRegex = Regex("(\\d+)\\s*(?:day|days|d)\\b", RegexOption.IGNORE_CASE)
        val qtyRegex = Regex(
            "^((?:half|1/2|1/3|a|one|two|three|four|five|six|\\d+)\\s*(?:bag|bunch|jar|tub|pack|carton|cup|cups|oz|lb|block|bottle|can|loaves|loaf|pieces|avocados|eggs|peppers)?(?:\\s+of)?)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )

        return clauses.take(8).mapIndexed { index, rawClause ->
            val lower = rawClause.lowercase()
            val explicitDays = daysRegex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val inferredDays = when {
                explicitDays != null -> explicitDays.coerceIn(1, 30)
                lower.contains("today") || lower.contains("tonight") || lower.contains("wilted") -> 1
                lower.contains("tomorrow") || lower.contains("soon") -> 2
                lower.contains("week") -> 7
                else -> ((index % 3) + 2)
            }

            // Strip trailing expiry phrase from name
            val cleanedClause = rawClause
                .replace(Regex("\\b(?:expiring|expires|use|good|for|in)\\s+.*$", RegexOption.IGNORE_CASE), "")
                .trim()

            val qtyMatch = qtyRegex.find(cleanedClause)
            val rawQty = qtyMatch?.groupValues?.getOrNull(1)
                ?.replace(Regex("\\bof$", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.ifBlank { "1 unit" } ?: "1 unit"

            val cleanName = (qtyMatch?.groupValues?.getOrNull(2) ?: cleanedClause)
                .trim()
                .ifBlank { rawClause.trim() }
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            val category = PantryCategory.fromString(cleanName)
            val tip = when (category) {
                PantryCategory.PRODUCE -> "Wrap in dry paper towel inside crisper drawer."
                PantryCategory.DAIRY_CHEESE -> "Keep sealed on interior middle shelf (not door)."
                PantryCategory.SAUCES_JARS -> "Wipe rim clean and store sealed; deglaze with water when low."
                PantryCategory.PROTEIN -> "Store on coldest bottom shelf and cook within 48h."
                PantryCategory.LEFTOVERS -> "Keep in shallow airtight container; re-crisp in skillet/Air Fryer."
                PantryCategory.PANTRY_STAPLE -> "Store in a cool, dry cupboard or airtight jar."
            }

            DetectedIngredient(
                name = cleanName,
                category = category,
                quantity = rawQty,
                daysUntilExpiry = inferredDays,
                visualConditionNote = "Raw Transcript Parsed On-Device (0 cloud credits)",
                storageTip = tip,
                estimatedValueDollars = 3.75,
                isSelected = true
            )
        }
    }

    private fun parseCustomNoteIngredients(note: String): List<DetectedIngredient> {
        if (note.isBlank()) return emptyList()
        return parseVoiceTranscriptLocally(note)
    }

    /**
     * Tier 3 Zero-Cost Shelf-Life & Preservation Engine:
     * Analyzes inventory shelf-life data and generates personalized zero-waste storage tips
     * and step-by-step preservation methods (e.g., how to blanch/freeze specific leafy greens,
     * freeze herb/pesto cubes, quick-pickle vegetables, or parchment-wrap cheeses) for expiring items.
     */
    fun synthesizePreservationGuidesLocally(
        items: List<PantryItemEntity>
    ): Pair<String, List<PreservationGuide>> {
        if (items.isEmpty()) {
            return "Add ingredients to your pantry to unlock personalized shelf-life & freezing guides." to emptyList()
        }

        val sortedByUrgency = items.sortedWith(
            compareBy<PantryItemEntity> { it.effectiveDaysUntilExpiry }
                .thenByDescending { it.estimatedValueDollars }
        )
        val expiringCandidates = sortedByUrgency.filter { it.effectiveDaysUntilExpiry <= 4 }
            .ifEmpty { sortedByUrgency.take(4) }

        val guides = expiringCandidates.map { item ->
            buildPreservationGuideForItem(item)
        }

        val totalExtendableValue = guides.sumOf { it.estimatedSavingsDollars }
        val criticalCount = guides.count { it.currentDaysLeft <= 1 }
        val summary = buildString {
            append("Analyzed ${items.size} pantry items: ")
            append("${guides.size} ingredient(s) ")
            if (criticalCount > 0) {
                append("($criticalCount critical ≤24h) ")
            }
            append("can be preserved right now to protect ~$${String.format(Locale.US, "%.2f", totalExtendableValue)} in groceries and extend shelf life up to +60 days.")
        }

        return summary to guides
    }

    fun buildPreservationGuideForItem(item: PantryItemEntity): PreservationGuide {
        val lower = (item.name + " " + item.visualConditionNote).lowercase()
        val category = item.parsedCategory
        val daysLeft = item.effectiveDaysUntilExpiry.coerceAtLeast(0)
        val alreadyPreserved = item.isPreserved || item.visualConditionNote.contains("Preserved", ignoreCase = true)

        return when {
            // Specific Leafy Greens (Spinach, Kale, Arugula, Chard, Collards, Herbs)
            lower.contains("spinach") || lower.contains("arugula") || lower.contains("kale") ||
                lower.contains("greens") || lower.contains("chard") || lower.contains("lettuce") ||
                lower.contains("cilantro") || lower.contains("parsley") || lower.contains("basil") -> {
                val isTenderGreen = lower.contains("spinach") || lower.contains("arugula") || lower.contains("kale") || lower.contains("greens")
                val method = if (isTenderGreen) {
                    PreservationMethodType.BLANCH_FREEZE
                } else {
                    PreservationMethodType.HERB_OIL_CUBE
                }
                val bonusDays = if (isTenderGreen) 60 else 45
                PreservationGuide(
                    id = "preserve-${item.id}",
                    itemId = item.id,
                    ingredientName = item.name,
                    category = category,
                    currentDaysLeft = daysLeft,
                    extendedShelfLifeDays = bonusDays,
                    methodType = method,
                    urgencyHeadline = if (daysLeft <= 1) {
                        "Wilting Alert (${daysLeft}d left) — Lock in Chlorophyll & Nutrients Today"
                    } else {
                        "Preserve Leafy Greens Before Moisture Breakdown (${daysLeft}d left)"
                    },
                    personalizedStorageTip = "Immediate Fridge Tip: Line the clamshell or bag with 2 dry paper towels to wick condensation and store in the high-humidity crisper drawer away from ethylene-producing fruit.",
                    preservationSteps = if (isTenderGreen) {
                        listOf(
                            "1. Rinse & Sort: Discard any slimy stems from ${item.name}; slightly wilted leaves are 100% safe and ideal for freezing.",
                            "2. 60-Second Steam/Blanch (Best Texture) or Dry Flash-Freeze: Plunge greens into boiling water for 60 seconds, then immediately transfer to an ice-water bath for 1 minute to deactivate enzymes that cause bitterness and browning.",
                            "3. Squeeze & Portion into Freeze Pucks: Squeeze out excess water, roll into tight 2-tbsp balls (or pack dry leaves flat into a freezer bag with all air pressed out), and freeze on a parchment-lined tray before sealing."
                        )
                    } else {
                        listOf(
                            "1. Finely chop ${item.name} (stems included for cilantro/parsley) before leaves darken.",
                            "2. Pack chopped greens two-thirds full into an ice cube tray and cover with Extra Virgin Olive Oil or melted butter.",
                            "3. Freeze solid for 3 hours, then pop herb-oil cubes into a labeled freezer bag for up to 45 days."
                        )
                    },
                    bestUseAfterPreserving = "Drop frozen green pucks straight into hot skillets, frittatas, smoothies, pasta sauces, or soups without thawing first.",
                    estimatedSavingsDollars = item.estimatedValueDollars,
                    isApplied = alreadyPreserved
                )
            }

            // Tomatoes (Cherry tomatoes, Roma, heirloom)
            lower.contains("tomato") -> {
                PreservationGuide(
                    id = "preserve-${item.id}",
                    itemId = item.id,
                    ingredientName = item.name,
                    category = category,
                    currentDaysLeft = daysLeft,
                    extendedShelfLifeDays = 45,
                    methodType = PreservationMethodType.FREEZE_LOCK,
                    urgencyHeadline = "Wrinkling Skins = Peak Sugar Concentration (${daysLeft}d left)",
                    personalizedStorageTip = "Immediate Storage Tip: Store stem-side down in a single layer on the front middle shelf; stem-scar sealing slows moisture loss by 40%.",
                    preservationSteps = listOf(
                        "1. Wash & Dry Thoroughly: Wipe ${item.name} completely dry so ice crystals don't puncture skins prematurely.",
                        "2. Whole Sheet-Pan Flash Freeze (or 12-Min Oil Confit): Spread whole cherry tomatoes (or cored wedges) in a single layer on parchment paper and freeze for 2 hours until marbles-hard.",
                        "3. Transfer to Airtight Freezer Bag: Squeeze out excess air and freeze up to 45 days. (Bonus: Their skins slip right off under warm tap water when removed from the freezer!)"
                    ),
                    bestUseAfterPreserving = "Toss frozen tomatoes directly into simmering shakshuka, skillet pasta sauces, or pan roasts—they burst into instant jammy sauce.",
                    estimatedSavingsDollars = item.estimatedValueDollars,
                    isApplied = alreadyPreserved
                )
            }

            // Pesto, Herb Sauces, Paste Tubes, Opened Jars
            category == PantryCategory.SAUCES_JARS || lower.contains("pesto") || lower.contains("paste") || lower.contains("sauce") || lower.contains("hummus") -> {
                PreservationGuide(
                    id = "preserve-${item.id}",
                    itemId = item.id,
                    ingredientName = item.name,
                    category = category,
                    currentDaysLeft = daysLeft,
                    extendedShelfLifeDays = 35,
                    methodType = PreservationMethodType.HERB_OIL_CUBE,
                    urgencyHeadline = "Opened Jar Oxidation Risk (${daysLeft}d left) — Freeze in 1-Tbsp Cubes",
                    personalizedStorageTip = "Immediate Fridge Tip: Scrape down the inside glass walls with a clean rubber spatula and pour a 2mm layer of Extra Virgin Olive Oil over the surface to create an anaerobic oxygen barrier.",
                    preservationSteps = listOf(
                        "1. Portion by Tablespoon: Spoon remaining ${item.name} (${item.quantity}) into silicone ice cube compartments (1 tbsp per slot).",
                        "2. Seal with Olive Oil Cap: Drizzle a few drops of olive oil over each cube top to prevent freezer burn and preserve bright basil/tomato aromatics.",
                        "3. Pop & Bag: Once frozen solid (2–3 hours), transfer cubes to a sealed container for 1-cube-at-a-time skillet cooking."
                    ),
                    bestUseAfterPreserving = "Swirl 1 frozen cube directly into warm pasta water, scrambled eggs, grain bowls, or pan-seared proteins.",
                    estimatedSavingsDollars = item.estimatedValueDollars,
                    isApplied = alreadyPreserved
                )
            }

            // Bell Peppers, Onions, Mushrooms, Avocados, Cruciferous Produce
            lower.contains("pepper") || lower.contains("mushroom") || lower.contains("onion") || lower.contains("avocado") || category == PantryCategory.PRODUCE -> {
                val useQuickPickle = lower.contains("pepper") || lower.contains("onion") || lower.contains("cucumber") || lower.contains("radish")
                val method = if (useQuickPickle) PreservationMethodType.QUICK_PICKLE else PreservationMethodType.FREEZE_LOCK
                val bonusDays = if (useQuickPickle) 21 else 40
                PreservationGuide(
                    id = "preserve-${item.id}",
                    itemId = item.id,
                    ingredientName = item.name,
                    category = category,
                    currentDaysLeft = daysLeft,
                    extendedShelfLifeDays = bonusDays,
                    methodType = method,
                    urgencyHeadline = "Cut Produce Edge Softening (${daysLeft}d left) — Quick-Pickle or Flash-Freeze",
                    personalizedStorageTip = "Immediate Fridge Tip: Store cut produce cut-side down in a tight glass container lined with a dry paper towel in the crisper drawer.",
                    preservationSteps = if (useQuickPickle) {
                        listOf(
                            "1. Slice Thinly: Cut remaining ${item.name} into thin strips or rings and pack into a clean glass jar.",
                            "2. 3-Minute 1-1-1 Quick Brine: Warm 1/2 cup vinegar (or lemon juice), 1/2 cup water, 1 tsp salt, and 1 tsp sugar until dissolved, then pour over the sliced produce.",
                            "3. Cool & Refrigerate: Seal and chill—acidity halts spoilage bacteria and extends crispness for +21 days. (Or dice raw and flash-freeze flat for +45 days!)"
                        )
                    } else {
                        listOf(
                            "1. Prep & Slice: Trim any dry edges on ${item.name} and slice into cooking-ready pieces.",
                            "2. Quick Sauté or Flash Freeze: Spread slices on a parchment tray in the freezer for 90 minutes so pieces don't clump together.",
                            "3. Seal Airtight: Store in a freezer-safe bag for instant stir-fries, omelets, and sheet-pan meals."
                        )
                    },
                    bestUseAfterPreserving = if (useQuickPickle) {
                        "Use tangy quick-pickled strips to brighten grain bowls, melts, tacos, salads, and frittatas."
                    } else {
                        "Sauté straight from frozen over high heat so moisture evaporates quickly while edges caramelize."
                    },
                    estimatedSavingsDollars = item.estimatedValueDollars,
                    isApplied = alreadyPreserved
                )
            }

            // Dairy & Cheese (Cheddar, Feta, Yogurt, Milk)
            category == PantryCategory.DAIRY_CHEESE || lower.contains("cheddar") || lower.contains("cheese") || lower.contains("feta") || lower.contains("yogurt") -> {
                PreservationGuide(
                    id = "preserve-${item.id}",
                    itemId = item.id,
                    ingredientName = item.name,
                    category = category,
                    currentDaysLeft = daysLeft,
                    extendedShelfLifeDays = 30,
                    methodType = PreservationMethodType.AIRTIGHT_OIL_SEAL,
                    urgencyHeadline = "Prevent Mold & Rind Drying (${daysLeft}d left) — Breathable Parchment + Pre-Grate Freeze",
                    personalizedStorageTip = "Immediate Fridge Tip: Ditch tight plastic wrap (which traps sweating moisture and accelerates mold). Wrap cheese in wax or parchment paper first, then place loosely inside an unsealed zip bag in the cheese drawer.",
                    preservationSteps = listOf(
                        "1. Breathable Re-Wrap for Fridge (+14d): Wrap ${item.name} in fresh parchment/wax paper so the cheese can breathe without drying out.",
                        "2. Pre-Grate & Freeze for Melting (+30d): If you won't finish the ${item.quantity} this week, shred or crumble it now and toss with 1/2 tsp cornstarch (or keep plain).",
                        "3. Freeze Flat: Seal shredded cheese in a freezer bag—shredded aged cheese melts identically to fresh straight from the freezer!"
                    ),
                    bestUseAfterPreserving = "Sprinkle frozen shredded/crumbled cheese directly onto hot skillet frittatas, pan melts, baked pasta, or shakshuka.",
                    estimatedSavingsDollars = item.estimatedValueDollars,
                    isApplied = alreadyPreserved
                )
            }

            // Proteins, Eggs, Deli, or Leftovers
            else -> {
                PreservationGuide(
                    id = "preserve-${item.id}",
                    itemId = item.id,
                    ingredientName = item.name,
                    category = category,
                    currentDaysLeft = daysLeft,
                    extendedShelfLifeDays = 30,
                    methodType = PreservationMethodType.FREEZE_LOCK,
                    urgencyHeadline = "Extend Protein / Staple Shelf-Life (${daysLeft}d left)",
                    personalizedStorageTip = "Immediate Fridge Tip: Keep ${item.name} on the coldest interior middle/bottom shelf (34–37°F), never in the warm refrigerator door.",
                    preservationSteps = listOf(
                        "1. Portion for Single Meals: If ${item.name} is eggs, whisk whole eggs with a pinch of salt (prevents yolk gelation in freezer) and pour into a muffin tin or ice tray; for proteins/leftovers, divide into flat single servings.",
                        "2. Press Out Air: Wrap tightly or seal in freezer containers with minimal headspace to block ice crystal formation.",
                        "3. Label & Freeze (+30d): Thaw overnight in the fridge or cook directly in a covered skillet."
                    ),
                    bestUseAfterPreserving = "Thaw in fridge overnight for scrambles, baking, protein bowls, or quick skillet stir-fries.",
                    estimatedSavingsDollars = item.estimatedValueDollars,
                    isApplied = alreadyPreserved
                )
            }
        }
    }
}
