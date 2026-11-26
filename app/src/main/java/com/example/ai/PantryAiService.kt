package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.DetectedIngredient
import com.example.data.KitchenSetupState
import com.example.data.PantryCategory
import com.example.data.PantryItemEntity
import com.example.data.PreservationGuide
import com.example.data.PreservationMethodType
import com.example.data.RecipeCategoryFilter
import com.example.data.RecipeSuggestion
import com.example.data.ScanCaptureMode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

data class AiInferenceResult<T>(
    val data: T,
    val tierUsed: AiCapabilityTier,
    val autoCascadedToLite: Boolean = false,
    val fromDedupCache: Boolean = false,
    val usedByokKey: Boolean = false
)

class PantryAiService(
    private val byokApiKeyProvider: () -> String? = { null }
) {

    private data class CachedEntry<T>(
        val fingerprint: String,
        val timestampMs: Long,
        val payload: T
    )

    @Volatile
    private var cachedVisionResult: CachedEntry<Pair<String, List<DetectedIngredient>>>? = null
    @Volatile
    private var cachedRecipeResult: CachedEntry<List<RecipeSuggestion>>? = null
    @Volatile
    private var cachedPreservationResult: CachedEntry<Pair<String, List<PreservationGuide>>>? = null

    private val visionSchema = Schema.obj(
        properties = mapOf(
            "summaryNote" to Schema.string(
                description = "A concise 1-sentence summary of the overall fridge/pantry shelf or grocery receipt scan and urgency."
            ),
            "ingredients" to Schema.array(
                description = "Detected food items, produce, cheeses, proteins, and half-used sauces.",
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Specific ingredient name, e.g. Wilted Baby Spinach or Half-Jar Marinara"),
                        "category" to Schema.enumeration(
                            values = listOf(
                                "PRODUCE",
                                "DAIRY_CHEESE",
                                "SAUCES_JARS",
                                "PROTEIN",
                                "LEFTOVERS",
                                "PANTRY_STAPLE"
                            ),
                            description = "Category of the detected ingredient"
                        ),
                        "quantity" to Schema.string(description = "Estimated remaining amount, e.g. '1/2 jar', '3 Roma tomatoes', '4 oz block'"),
                        "daysUntilExpiry" to Schema.integer(description = "Estimated days remaining before spoilage (1 = use today/critical, 2-3 = expiring soon, 4-14 = fresh)"),
                        "visualConditionNote" to Schema.string(description = "Visual freshness observation or receipt line item note"),
                        "storageTip" to Schema.string(description = "Actionable tip to extend shelf life"),
                        "estimatedValueDollars" to Schema.double(description = "Estimated grocery dollar value of this item, e.g. 3.50")
                    )
                )
            )
        )
    )

    private val recipeRankingSchema = Schema.obj(
        properties = mapOf(
            "recipes" to Schema.array(
                description = "Exactly 3 zero-waste recipes ranked from highest priority (#1 uses up the most expiring items first) to #3.",
                items = Schema.obj(
                    properties = mapOf(
                        "rank" to Schema.integer(description = "Rank 1, 2, or 3"),
                        "title" to Schema.string(description = "Appetizing recipe title"),
                        "recipeCategory" to Schema.enumeration(
                            values = listOf(
                                "QUICK_EASY",
                                "HEALTHY_LOW_CAL",
                                "BUDGET_FRIENDLY"
                            ),
                            description = "Category of the recipe: QUICK_EASY, HEALTHY_LOW_CAL, or BUDGET_FRIENDLY"
                        ),
                        "rescueScore" to Schema.integer(description = "Zero-waste rescue score from 75 to 99 based on expiring ingredients used"),
                        "prepTimeMinutes" to Schema.integer(description = "Total prep and cook time in minutes"),
                        "servings" to Schema.integer(description = "Number of servings"),
                        "caloriesPerServing" to Schema.integer(description = "Estimated calories per serving, e.g. 340"),
                        "estimatedCostPerServing" to Schema.double(description = "Estimated dollar cost per serving using leftover ingredients, e.g. 1.85"),
                        "applianceUsed" to Schema.string(description = "Primary kitchen appliance used from the user's owned appliances"),
                        "whyItRescues" to Schema.string(description = "1-sentence explanation of which expiring items this dish prioritizes"),
                        "expiringIngredientsUsed" to Schema.array(
                            items = Schema.string(),
                            description = "Expiring fridge items rescued by this recipe with quantities"
                        ),
                        "pantryIngredientsUsed" to Schema.array(
                            items = Schema.string(),
                            description = "Other fresh or staple fridge items used"
                        ),
                        "spicesUsed" to Schema.array(
                            items = Schema.string(),
                            description = "Spices and condiments used from the user's owned spice rack"
                        ),
                        "steps" to Schema.array(
                            items = Schema.string(),
                            description = "Clear step-by-step cooking instructions tailored to the user's appliances and spices"
                        ),
                        "chefTip" to Schema.string(description = "Zero-waste culinary tip for rescuing slightly older produce or sauces in this dish")
                    )
                )
            )
        )
    )

    private val preservationSchema = Schema.obj(
        properties = mapOf(
            "analysisSummary" to Schema.string(
                description = "A concise 1-sentence executive summary of the fridge's shelf-life risk and total extendable days."
            ),
            "guides" to Schema.array(
                description = "Personalized zero-waste storage & preservation guides for ingredients nearing expiration.",
                items = Schema.obj(
                    properties = mapOf(
                        "ingredientName" to Schema.string(description = "Exact name of the ingredient from the user's inventory"),
                        "methodType" to Schema.enumeration(
                            values = listOf(
                                "FREEZE_LOCK",
                                "BLANCH_FREEZE",
                                "HERB_OIL_CUBE",
                                "QUICK_PICKLE",
                                "HYDRO_CRISPER_WRAP",
                                "AIRTIGHT_OIL_SEAL"
                            ),
                            description = "Best culinary preservation technique for this specific ingredient"
                        ),
                        "extendedShelfLifeDays" to Schema.integer(description = "Additional days of shelf life gained by applying this preservation method (e.g. 60 for blanch & freeze greens, 30 for herb/pesto oil cubes, 21 for quick pickle)"),
                        "urgencyHeadline" to Schema.string(description = "Short actionable headline explaining why & how to preserve this item now"),
                        "personalizedStorageTip" to Schema.string(description = "Immediate fridge zone, humidity, and wrapping tip to slow spoilage right now"),
                        "preservationSteps" to Schema.array(
                            items = Schema.string(),
                            description = "3 clear, specific step-by-step instructions (e.g. how to blanch/flash-freeze specific greens, freeze sauces in ice cube trays, or wax-wrap cheese)"
                        ),
                        "bestUseAfterPreserving" to Schema.string(description = "How to cook with the preserved/frozen ingredient later without losing flavor or texture")
                    )
                )
            )
        )
    )

    // Tier 1 Primary Models (lazy)
    private val primaryVisionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelConfig.PRIMARY_VISION_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = visionSchema
            }
        )
    }

    private val primaryRecipeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelConfig.PRIMARY_REASONING_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recipeRankingSchema
            }
        )
    }

    private val primaryPreservationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelConfig.PRIMARY_REASONING_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = preservationSchema
            }
        )
    }

    private val primaryChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelConfig.PRIMARY_REASONING_MODEL
        )
    }

    // Tier 2 Economy Lite Models (lazy)
    private val economyVisionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelConfig.ECONOMY_VISION_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = visionSchema
            }
        )
    }

    private val economyRecipeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelConfig.ECONOMY_LITE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = recipeRankingSchema
            }
        )
    }

    private val economyPreservationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelConfig.ECONOMY_LITE_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = preservationSchema
            }
        )
    }

    private val economyChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AiModelConfig.ECONOMY_LITE_MODEL
        )
    }

    suspend fun analyzeFridgePhotos(
        bitmaps: List<Bitmap>,
        captureMode: ScanCaptureMode,
        customFocusNote: String,
        preferredTier: AiCapabilityTier = AiCapabilityTier.TIER_1_PRIMARY
    ): AiInferenceResult<Pair<String, List<DetectedIngredient>>> {
        val optimizedBitmaps = bitmaps.map { AiModelConfig.optimizeBitmapForUpload(it) }
        val prompt = buildString {
            when (captureMode) {
                ScanCaptureMode.FRIDGE_SHELF -> {
                    append("You are PantryPulse Fridge Vision, an expert zero-waste culinary inspector. ")
                    append("Inspect these 1-2 photos of a user's open refrigerator or pantry shelves. ")
                    append("Identify visible ingredients (vegetables, fruits, half-used sauces/jars, cheeses, proteins, leftovers). ")
                    append("Pay special attention to visual cues of aging or wilting to flag items that need to be consumed soon (daysUntilExpiry 1 to 3). ")
                }
                ScanCaptureMode.GROCERY_RECEIPT -> {
                    append("You are PantryPulse Receipt & Label Inspector. ")
                    append("Read the purchased grocery items and prices from this grocery receipt or food packaging photo. ")
                    append("Assign realistic shelf-life countdowns (daysUntilExpiry: 2-3 days for delicate greens/berries/fresh herbs, 5-7 days for dairy/deli, 10-14 days for eggs/condiments). ")
                }
            }
            if (customFocusNote.isNotBlank()) {
                append("User note: $customFocusNote. ")
            }
            append("Return between 4 and 8 specific ingredients with realistic quantities, condition notes, storage tips, and estimated grocery value.")
        }

        // Check 45-second Fingerprint Deduplication Cache
        val fingerprint = AiModelConfig.computeRequestFingerprint(prompt, optimizedBitmaps)
        val now = System.currentTimeMillis()
        cachedVisionResult?.let { cached ->
            if (cached.fingerprint == fingerprint && (now - cached.timestampMs) <= AiModelConfig.DEDUP_CACHE_TTL_MS) {
                return AiInferenceResult(
                    data = cached.payload,
                    tierUsed = preferredTier,
                    fromDedupCache = true
                )
            }
        }

        val inputContent = content {
            optimizedBitmaps.forEach { bmp -> image(bmp) }
            text(prompt)
        }

        val byokKey = byokApiKeyProvider()?.takeIf { it.isNotBlank() }
        val (responseText, actualTier, cascaded) = if (byokKey != null) {
            val byokPrompt = "$prompt Return ONLY valid JSON with fields: summaryNote (string) and ingredients (array of {name, category, quantity, daysUntilExpiry, visualConditionNote, storageTip, estimatedValueDollars})."
            val modelName = if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) "gemini-2.5-flash-lite" else "gemini-2.5-flash"
            val text = ByokGeminiRestClient.generateContent(
                apiKey = byokKey,
                modelName = modelName,
                prompt = byokPrompt,
                bitmaps = optimizedBitmaps,
                expectJson = true
            )
            Triple(text, preferredTier, false)
        } else if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) {
            val res = economyVisionModel.generateContent(inputContent)
            Triple(res.text, AiCapabilityTier.TIER_2_ECO_LITE, false)
        } else {
            try {
                val res = primaryVisionModel.generateContent(inputContent)
                Triple(res.text, AiCapabilityTier.TIER_1_PRIMARY, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isQuotaOrRateLimitException(e)) {
                    Log.w("PantryAiService", "Tier 1 hit rate-limit/quota, auto-cascading to Tier 2 Economy Lite", e)
                    val fallbackRes = economyVisionModel.generateContent(inputContent)
                    Triple(fallbackRes.text, AiCapabilityTier.TIER_2_ECO_LITE, true)
                } else {
                    throw e
                }
            }
        }

        val jsonString = unwrapJsonPayload(responseText) ?: throw IllegalStateException("Gemini Vision returned an empty response.")
        val root = JSONObject(jsonString)
        val summary = root.optString("summaryNote", "Scanned and identified ingredients by freshness urgency.")
        val arr = root.optJSONArray("ingredients")
        val detected = if (arr != null) {
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val name = obj.optString("name", "").trim()
                if (name.isBlank()) return@mapNotNull null
                DetectedIngredient(
                    name = name,
                    category = PantryCategory.fromString(obj.optString("category", "PRODUCE")),
                    quantity = obj.optString("quantity", "1 item").ifBlank { "1 item" },
                    daysUntilExpiry = obj.optInt("daysUntilExpiry", 3).coerceIn(1, 30),
                    visualConditionNote = obj.optString("visualConditionNote", "Detected in scan"),
                    storageTip = obj.optString("storageTip", "Store in sealed container in fridge."),
                    estimatedValueDollars = obj.optSafeDouble("estimatedValueDollars", 3.50).coerceIn(0.50, 25.0)
                )
            }
        } else {
            emptyList()
        }

        val payload = summary to detected
        cachedVisionResult = CachedEntry(fingerprint, now, payload)
        return AiInferenceResult(
            data = payload,
            tierUsed = actualTier,
            autoCascadedToLite = cascaded,
            usedByokKey = byokKey != null
        )
    }

    suspend fun generateRankedRescueRecipes(
        items: List<PantryItemEntity>,
        kitchenSetup: KitchenSetupState,
        selectedCategories: Set<RecipeCategoryFilter>,
        dietaryOrCravingNote: String,
        preferredTier: AiCapabilityTier = AiCapabilityTier.TIER_1_PRIMARY
    ): AiInferenceResult<List<RecipeSuggestion>> {
        val expiringList = items.filter { it.effectiveDaysUntilExpiry <= 3 }
            .joinToString("; ") { "${it.name} (${it.quantity}, expires in ${it.effectiveDaysUntilExpiry}d, note: ${it.visualConditionNote})" }
            .ifBlank { "None critically expiring, use freshest items first" }

        val freshList = items.filter { it.effectiveDaysUntilExpiry > 3 }
            .joinToString("; ") { "${it.name} (${it.quantity}, ${it.effectiveDaysUntilExpiry}d left)" }
            .ifBlank { "Standard fridge items" }

        val appliances = kitchenSetup.ownedAppliances.joinToString(", ")
        val spices = kitchenSetup.ownedSpices.joinToString(", ")

        val categoryRequirements = if (selectedCategories.isEmpty() || selectedCategories.size == RecipeCategoryFilter.entries.size) {
            "Include one QUICK_EASY recipe, one HEALTHY_LOW_CAL recipe, and one BUDGET_FRIENDLY recipe across the 3 ranked meals."
        } else {
            val activeDescriptions = selectedCategories.joinToString(" OR ") {
                "${it.name}: ${it.aiPromptInstruction}"
            }
            "STRICT CATEGORY FILTER ACTIVE: Every generated recipe MUST belong to the user's toggled category filter(s): $activeDescriptions."
        }

        val prompt = buildString {
            append("You are the PantryPulse Zero-Waste Executive Chef. ")
            append("Generate 3 distinct, delicious dinner/meal recipes ranked strictly by 'Uses up expiring items first' (Rank #1 rescues the most urgent expiring items). ")
            append("\n\nRECIPE CATEGORY REQUIREMENTS: $categoryRequirements")
            append("\nURGENT EXPIRING FRIDGE ITEMS (MUST PRIORITIZE): $expiringList")
            append("\nOTHER AVAILABLE FRIDGE/PANTRY ITEMS: $freshList")
            append("\nUSER'S OWNED KITCHEN APPLIANCES (Only use these): $appliances")
            append("\nUSER'S OWNED SPICES & CONDIMENTS (Customize steps to use these): $spices")
            if (dietaryOrCravingNote.isNotBlank()) {
                append("\nUSER PREFERENCE / CRAVING NOTE: $dietaryOrCravingNote")
            }
            append("\nEnsure every recipe has step-by-step instructions customized to the user's exact appliances and spices, along with accurate caloriesPerServing and estimatedCostPerServing.")
        }

        // Check 45-second Fingerprint Deduplication Cache
        val fingerprint = AiModelConfig.computeRequestFingerprint(prompt)
        val now = System.currentTimeMillis()
        cachedRecipeResult?.let { cached ->
            if (cached.fingerprint == fingerprint && (now - cached.timestampMs) <= AiModelConfig.DEDUP_CACHE_TTL_MS) {
                return AiInferenceResult(
                    data = cached.payload,
                    tierUsed = preferredTier,
                    fromDedupCache = true
                )
            }
        }

        val byokKey = byokApiKeyProvider()?.takeIf { it.isNotBlank() }
        val (responseText, actualTier, cascaded) = if (byokKey != null) {
            val byokPrompt = "$prompt\nReturn ONLY valid JSON: {\"recipes\":[{\"rank\":1,\"title\":\"...\",\"recipeCategory\":\"QUICK_EASY\",\"rescueScore\":95,\"prepTimeMinutes\":20,\"servings\":2,\"caloriesPerServing\":360,\"estimatedCostPerServing\":2.0,\"applianceUsed\":\"...\",\"whyItRescues\":\"...\",\"expiringIngredientsUsed\":[],\"pantryIngredientsUsed\":[],\"spicesUsed\":[],\"steps\":[],\"chefTip\":\"...\"}]}"
            val modelName = if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) "gemini-2.5-flash-lite" else "gemini-2.5-flash"
            val text = ByokGeminiRestClient.generateContent(
                apiKey = byokKey,
                modelName = modelName,
                prompt = byokPrompt,
                expectJson = true
            )
            Triple(text, preferredTier, false)
        } else if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) {
            val res = economyRecipeModel.generateContent(prompt)
            Triple(res.text, AiCapabilityTier.TIER_2_ECO_LITE, false)
        } else {
            try {
                val res = primaryRecipeModel.generateContent(prompt)
                Triple(res.text, AiCapabilityTier.TIER_1_PRIMARY, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isQuotaOrRateLimitException(e)) {
                    Log.w("PantryAiService", "Tier 1 recipe model rate-limited, auto-cascading to Tier 2 Economy Lite", e)
                    val fallbackRes = economyRecipeModel.generateContent(prompt)
                    Triple(fallbackRes.text, AiCapabilityTier.TIER_2_ECO_LITE, true)
                } else {
                    throw e
                }
            }
        }

        val jsonString = unwrapJsonPayload(responseText) ?: throw IllegalStateException("Recipe generator returned empty text.")
        val root = JSONObject(jsonString)
        val recipesArray = root.optJSONArray("recipes")

        val recipes = if (recipesArray != null) {
            (0 until recipesArray.length()).mapNotNull { idx ->
                val obj = recipesArray.optJSONObject(idx) ?: return@mapNotNull null
                val title = obj.optString("title", "").trim()
                if (title.isBlank()) return@mapNotNull null

                RecipeSuggestion(
                    id = UUID.randomUUID().toString(),
                    rank = obj.optInt("rank", idx + 1),
                    title = title,
                    recipeCategory = RecipeCategoryFilter.fromString(obj.optString("recipeCategory", "QUICK_EASY")),
                    rescueScore = obj.optInt("rescueScore", 95 - idx * 5).coerceIn(60, 100),
                    prepTimeMinutes = obj.optInt("prepTimeMinutes", 20),
                    servings = obj.optInt("servings", 2),
                    caloriesPerServing = obj.optInt("caloriesPerServing", 360).coerceIn(120, 1200),
                    estimatedCostPerServing = obj.optSafeDouble("estimatedCostPerServing", 2.10).coerceIn(0.50, 15.0),
                    applianceUsed = obj.optString("applianceUsed", kitchenSetup.ownedAppliances.firstOrNull() ?: "Skillet"),
                    whyItRescues = obj.optString("whyItRescues", "Prioritizes expiring produce before spoilage."),
                    expiringIngredientsUsed = obj.optStringList("expiringIngredientsUsed"),
                    pantryIngredientsUsed = obj.optStringList("pantryIngredientsUsed"),
                    spicesUsed = obj.optStringList("spicesUsed"),
                    steps = obj.optStringList("steps").ifEmpty {
                        listOf("Combine ingredients, season to taste, and cook until tender.")
                    },
                    chefTip = obj.optString("chefTip", "Season to taste right before serving.")
                )
            }.sortedBy { it.rank }
        } else {
            emptyList()
        }

        cachedRecipeResult = CachedEntry(fingerprint, now, recipes)
        return AiInferenceResult(
            data = recipes,
            tierUsed = actualTier,
            autoCascadedToLite = cascaded,
            usedByokKey = byokKey != null
        )
    }

    fun streamChefChatResponse(
        userMessage: String,
        items: List<PantryItemEntity>,
        kitchenSetup: KitchenSetupState,
        recentHistory: List<Pair<Boolean, String>>,
        preferredTier: AiCapabilityTier = AiCapabilityTier.TIER_1_PRIMARY
    ): Flow<String> {
        // Sliding Context Window: compact inventory state + last 6 turns
        val inventorySummary = items.take(8).joinToString(", ") {
            "${it.name} (${it.quantity}, ${it.effectiveDaysUntilExpiry}d left)"
        }
        val appliances = kitchenSetup.ownedAppliances.joinToString(", ")
        val spices = kitchenSetup.ownedSpices.joinToString(", ")

        val historyTranscript = recentHistory.takeLast(6).joinToString("\n") { (isUser, msg) ->
            if (isUser) "User: $msg" else "Chef: $msg"
        }

        val fullPrompt = buildString {
            append("You are the PantryPulse Zero-Waste Sous-Chef. ")
            append("Help the user turn leftover chaos into dinner, substitute missing ingredients, adapt cooking steps to their appliances, or rescue wilting produce.\n")
            append("Current Fridge Inventory: $inventorySummary\n")
            append("User's Appliances: $appliances\n")
            append("User's Spices: $spices\n")
            if (historyTranscript.isNotBlank()) {
                append("Recent Conversation:\n$historyTranscript\n")
            }
            append("User's Question: $userMessage\n")
            append("Respond warmly, concisely, and practically with bullet points or numbered steps tailored to their exact kitchen setup.")
        }

        val byokKey = byokApiKeyProvider()?.takeIf { it.isNotBlank() }
        if (byokKey != null) {
            val modelName = if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) "gemini-2.5-flash-lite" else "gemini-2.5-flash"
            return flow {
                emit(
                    ByokGeminiRestClient.generateContent(
                        apiKey = byokKey,
                        modelName = modelName,
                        prompt = fullPrompt,
                        expectJson = false
                    )
                )
            }
        }

        val targetModel = if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) {
            economyChatModel
        } else {
            primaryChatModel
        }

        return targetModel.generateContentStream(fullPrompt).map { chunk ->
            chunk.text.orEmpty()
        }
    }

    @Volatile
    private var cachedVoiceParseResult: CachedEntry<List<DetectedIngredient>>? = null

    /**
     * Multimodal Voice-to-Inventory AI Parser (Tier 1 -> Tier 2 Auto-Cascade).
     * Parses natural spoken kitchen memos (e.g. "I have a half bag of spinach expiring in 2 days,
     * a tub of crumbled feta, and 2 ripe avocados") into structured DetectedIngredient objects.
     */
    suspend fun parseVoiceInventoryTranscript(
        transcript: String,
        preferredTier: AiCapabilityTier = AiCapabilityTier.TIER_1_PRIMARY
    ): AiInferenceResult<List<DetectedIngredient>> {
        val prompt = buildString {
            append("You are the PantryPulse Voice-to-Inventory AI Assistant. ")
            append("Parse the user's spoken grocery/fridge memo into structured fridge/pantry ingredients: ")
            append("\"$transcript\". ")
            append("Infer realistic quantities, categories (PRODUCE, DAIRY_CHEESE, SAUCES_JARS, PROTEIN, LEFTOVERS, PANTRY_STAPLE), ")
            append("daysUntilExpiry (1-14 days based on item type and any urgency cues like 'wilted', 'half used', 'tomorrow'), ")
            append("actionable storageTip, and estimatedValueDollars.")
        }

        val fingerprint = AiModelConfig.computeRequestFingerprint("VOICE:$prompt")
        val now = System.currentTimeMillis()
        cachedVoiceParseResult?.let { cached ->
            if (cached.fingerprint == fingerprint && (now - cached.timestampMs) <= AiModelConfig.DEDUP_CACHE_TTL_MS) {
                return AiInferenceResult(
                    data = cached.payload,
                    tierUsed = preferredTier,
                    fromDedupCache = true
                )
            }
        }

        val byokKey = byokApiKeyProvider()?.takeIf { it.isNotBlank() }
        val (responseText, actualTier, cascaded) = if (byokKey != null) {
            val byokPrompt = "$prompt Return ONLY valid JSON: {\"ingredients\":[{\"name\":\"...\",\"category\":\"PRODUCE\",\"quantity\":\"1 unit\",\"daysUntilExpiry\":3,\"visualConditionNote\":\"...\",\"storageTip\":\"...\",\"estimatedValueDollars\":3.5}]}"
            val modelName = if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) "gemini-2.5-flash-lite" else "gemini-2.5-flash"
            val text = ByokGeminiRestClient.generateContent(
                apiKey = byokKey,
                modelName = modelName,
                prompt = byokPrompt,
                expectJson = true
            )
            Triple(text, preferredTier, false)
        } else if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) {
            val res = economyVisionModel.generateContent(prompt)
            Triple(res.text, AiCapabilityTier.TIER_2_ECO_LITE, false)
        } else {
            try {
                val res = primaryVisionModel.generateContent(prompt)
                Triple(res.text, AiCapabilityTier.TIER_1_PRIMARY, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isQuotaOrRateLimitException(e)) {
                    Log.w("PantryAiService", "Tier 1 voice parse hit rate-limit, auto-cascading to Tier 2 Economy Lite", e)
                    val fallbackRes = economyVisionModel.generateContent(prompt)
                    Triple(fallbackRes.text, AiCapabilityTier.TIER_2_ECO_LITE, true)
                } else {
                    throw e
                }
            }
        }

        val jsonString = unwrapJsonPayload(responseText) ?: throw IllegalStateException("Voice-to-Inventory AI returned empty text.")
        val root = JSONObject(jsonString)
        val arr = root.optJSONArray("ingredients")
        val parsed = if (arr != null) {
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val name = obj.optString("name", "").trim()
                if (name.isBlank()) return@mapNotNull null
                DetectedIngredient(
                    name = name,
                    category = PantryCategory.fromString(obj.optString("category", "PRODUCE")),
                    quantity = obj.optString("quantity", "1 unit").ifBlank { "1 unit" },
                    daysUntilExpiry = obj.optInt("daysUntilExpiry", 3).coerceIn(1, 30),
                    visualConditionNote = obj.optString("visualConditionNote", "Parsed from voice memo"),
                    storageTip = obj.optString("storageTip", "Store in sealed container in fridge."),
                    estimatedValueDollars = obj.optSafeDouble("estimatedValueDollars", 3.50).coerceIn(0.50, 25.0),
                    isSelected = true
                )
            }
        } else {
            emptyList()
        }

        cachedVoiceParseResult = CachedEntry(fingerprint, now, parsed)
        return AiInferenceResult(
            data = parsed,
            tierUsed = actualTier,
            autoCascadedToLite = cascaded,
            usedByokKey = byokKey != null
        )
    }

    /**
     * Analyzes the user's inventory shelf-life data using Gemini structured JSON output
     * to generate personalized zero-waste storage tips and step-by-step preservation methods
     * (e.g., how to blanch/freeze specific leafy greens, freeze pesto/sauces in ice cube trays,
     * quick-pickle cut vegetables, or parchment-wrap cheeses) for ingredients nearing expiration.
     */
    suspend fun analyzeShelfLifeAndPreservation(
        items: List<PantryItemEntity>,
        preferredTier: AiCapabilityTier = AiCapabilityTier.TIER_1_PRIMARY
    ): AiInferenceResult<Pair<String, List<PreservationGuide>>> {
        val sortedByUrgency = items.sortedWith(
            compareBy<PantryItemEntity> { it.effectiveDaysUntilExpiry }
                .thenByDescending { it.estimatedValueDollars }
        )
        val targetItems = sortedByUrgency.filter { it.effectiveDaysUntilExpiry <= 4 }
            .ifEmpty { sortedByUrgency.take(5) }

        val inventoryBlock = targetItems.joinToString("\n") { item ->
            "- ID=${item.id} | ${item.name} (${item.quantity}, Category: ${item.parsedCategory.displayName}): " +
                "${item.effectiveDaysUntilExpiry} day(s) left, Est. Value $${String.format(Locale.US, "%.2f", item.estimatedValueDollars)} — " +
                "Condition: ${item.visualConditionNote}"
        }

        val prompt = """
            You are PantryPulse Zero-Waste Food Scientist & Preservation Coach.
            Analyze the shelf-life data of these refrigerator/pantry ingredients nearing expiration:
            $inventoryBlock

            For each ingredient listed above, provide:
            1. The optimal preservation methodType (BLANCH_FREEZE for leafy greens like spinach/kale/arugula, HERB_OIL_CUBE for tender herbs/pesto/sauces, FREEZE_LOCK for tomatoes/berries/proteins, QUICK_PICKLE for cut peppers/onions/cucumbers, AIRTIGHT_OIL_SEAL for aged cheeses/opened jars, or HYDRO_CRISPER_WRAP for crisp produce).
            2. Realistic extendedShelfLifeDays gained (e.g. 45 to 60 days for freezing/blanching greens, 30 days for oil cubes or shredded cheese freezing, 21 days for quick pickling).
            3. A personalizedStorageTip for immediate fridge zone, humidity, and wrapping.
            4. 3 detailed, actionable preservationSteps tailored to that exact ingredient (e.g., exact blanching + ice-bath shock timing for baby spinach, or freezing pesto in ice cube trays with an olive oil cap).
            5. bestUseAfterPreserving explaining how to cook with it directly from frozen/preserved state without losing texture.
        """.trimIndent()

        val fingerprint = AiModelConfig.computeRequestFingerprint("PRESERVE_$prompt")
        val now = System.currentTimeMillis()
        cachedPreservationResult?.let { cached ->
            if (cached.fingerprint == fingerprint && (now - cached.timestampMs) <= AiModelConfig.DEDUP_CACHE_TTL_MS) {
                return AiInferenceResult(
                    data = cached.payload,
                    tierUsed = preferredTier,
                    fromDedupCache = true
                )
            }
        }

        val byokKey = byokApiKeyProvider()?.takeIf { it.isNotBlank() }
        val (responseText, actualTier, cascaded) = if (byokKey != null) {
            val byokPrompt = "$prompt\nReturn ONLY valid JSON: {\"analysisSummary\":\"...\",\"guides\":[{\"ingredientName\":\"...\",\"methodType\":\"FREEZE_LOCK\",\"extendedShelfLifeDays\":30,\"urgencyHeadline\":\"...\",\"personalizedStorageTip\":\"...\",\"preservationSteps\":[],\"bestUseAfterPreserving\":\"...\"}]}"
            val modelName = if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) "gemini-2.5-flash-lite" else "gemini-2.5-flash"
            val text = ByokGeminiRestClient.generateContent(
                apiKey = byokKey,
                modelName = modelName,
                prompt = byokPrompt,
                expectJson = true
            )
            Triple(text, preferredTier, false)
        } else if (preferredTier == AiCapabilityTier.TIER_2_ECO_LITE) {
            val res = economyPreservationModel.generateContent(prompt)
            Triple(res.text, AiCapabilityTier.TIER_2_ECO_LITE, false)
        } else {
            try {
                val res = primaryPreservationModel.generateContent(prompt)
                Triple(res.text, AiCapabilityTier.TIER_1_PRIMARY, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isQuotaOrRateLimitException(e)) {
                    Log.w("PantryAiService", "Tier 1 preservation coach hit rate-limit, auto-cascading to Tier 2 Economy Lite", e)
                    val fallbackRes = economyPreservationModel.generateContent(prompt)
                    Triple(fallbackRes.text, AiCapabilityTier.TIER_2_ECO_LITE, true)
                } else {
                    throw e
                }
            }
        }

        val jsonString = unwrapJsonPayload(responseText) ?: throw IllegalStateException("Shelf-Life Preservation AI returned empty text.")
        val root = JSONObject(jsonString)
        val summary = root.optString(
            "analysisSummary",
            "Personalized zero-waste preservation & freezing plan ready for ${targetItems.size} expiring ingredients."
        )
        val guidesArr = root.optJSONArray("guides")
        val parsedGuides = if (guidesArr != null && targetItems.isNotEmpty()) {
            (0 until guidesArr.length()).mapNotNull { idx ->
                val obj = guidesArr.optJSONObject(idx) ?: return@mapNotNull null
                val rawName = obj.optString("ingredientName", "Pantry Item")
                val matchedEntity = targetItems.firstOrNull {
                    it.name.equals(rawName, ignoreCase = true) ||
                        it.name.contains(rawName, ignoreCase = true) ||
                        rawName.contains(it.name, ignoreCase = true)
                } ?: targetItems.getOrElse(idx) { targetItems.first() }

                val steps = obj.optStringList("preservationSteps").ifEmpty {
                    LocalCulinaryFallbackEngine.buildPreservationGuideForItem(matchedEntity).preservationSteps
                }
                val method = PreservationMethodType.fromString(obj.optString("methodType", "FREEZE_LOCK"))

                PreservationGuide(
                    id = "ai-preserve-${matchedEntity.id}-$idx",
                    itemId = matchedEntity.id,
                    ingredientName = matchedEntity.name,
                    category = matchedEntity.parsedCategory,
                    currentDaysLeft = matchedEntity.effectiveDaysUntilExpiry,
                    extendedShelfLifeDays = obj.optInt("extendedShelfLifeDays", method.defaultBonusDays).coerceIn(5, 90),
                    methodType = method,
                    urgencyHeadline = obj.optString(
                        "urgencyHeadline",
                        "Extend ${matchedEntity.name} by +${method.defaultBonusDays} days via ${method.label}"
                    ),
                    personalizedStorageTip = obj.optString(
                        "personalizedStorageTip",
                        matchedEntity.storageTip
                    ),
                    preservationSteps = steps,
                    bestUseAfterPreserving = obj.optString(
                        "bestUseAfterPreserving",
                        "Cook directly from preserved state in skillets, soups, or frittatas."
                    ),
                    estimatedSavingsDollars = matchedEntity.estimatedValueDollars,
                    isApplied = matchedEntity.isPreserved || matchedEntity.visualConditionNote.contains("Preserved via", ignoreCase = true)
                )
            }
        } else {
            emptyList()
        }

        val payload = summary to parsedGuides
        cachedPreservationResult = CachedEntry(fingerprint, now, payload)
        return AiInferenceResult(
            data = payload,
            tierUsed = actualTier,
            autoCascadedToLite = cascaded,
            usedByokKey = byokKey != null
        )
    }

    companion object {
        fun unwrapJsonPayload(raw: String?): String? {
            val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (!trimmed.startsWith("```")) return trimmed
            return trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
                .takeIf { it.isNotEmpty() }
        }

        fun isQuotaOrRateLimitException(e: Throwable): Boolean {
            val msg = (e.message ?: "") + " " + (e.cause?.message ?: "")
            val lower = msg.lowercase()
            return lower.contains("429") ||
                lower.contains("resource_exhausted") ||
                lower.contains("quota") ||
                lower.contains("rate limit") ||
                lower.contains("too many requests")
        }
        private fun JSONObject.optSafeDouble(key: String, fallback: Double): Double {
            if (isNull(key)) return fallback
            val parsed = optDouble(key, fallback)
            return if (parsed.isFinite()) parsed else fallback
        }

        private fun JSONObject.optStringList(key: String): List<String> {
            val arr = optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optString(i, "").trim().takeIf { it.isNotEmpty() }
            }
        }
    }
}
