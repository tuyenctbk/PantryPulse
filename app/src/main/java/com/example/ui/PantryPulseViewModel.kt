package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.AiCapabilityTier
import com.example.ai.AiFeatureType
import com.example.ai.LocalCulinaryFallbackEngine
import com.example.ai.PantryAiService
import com.example.ai.UsageQuotaManager
import com.example.ai.UsageQuotaSnapshot
import com.example.data.ChatMessage
import com.example.data.DetectedIngredient
import com.example.data.KitchenSetupState
import com.example.data.PantryCategory
import com.example.data.PantryDatabase
import com.example.data.PantryItemEntity
import com.example.data.PantryRepository
import com.example.data.RecipeCategoryFilter
import com.example.data.RecipeDietaryPreference
import com.example.data.RecipeSuggestion
import com.example.data.RestockItemEntity
import com.example.data.ScanCaptureMode
import com.example.widget.PantryPulseWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "PantryPulseVM"

enum class PantryTab {
    PANTRY,
    SCAN,
    MANUAL_ADD,
    RECIPES,
    CHEF_CHAT
}

enum class PantrySortOrder(val label: String) {
    URGENCY("Expiring First"),
    VALUE_HIGH("Highest Value $"),
    RECENT("Recently Added")
}

sealed interface VisionScanUiState {
    data object Idle : VisionScanUiState
    data object Analyzing : VisionScanUiState
    data class Success(
        val summaryNote: String,
        val detectedItems: List<DetectedIngredient>,
        val tierUsed: AiCapabilityTier = AiCapabilityTier.TIER_1_PRIMARY
    ) : VisionScanUiState
    data object QuotaRecoveryNeeded : VisionScanUiState
    data class Error(val message: String) : VisionScanUiState
}

sealed interface RecipeGenerationUiState {
    data object Idle : RecipeGenerationUiState
    data object Generating : RecipeGenerationUiState
    data class Success(
        val count: Int,
        val tierUsed: AiCapabilityTier = AiCapabilityTier.TIER_1_PRIMARY
    ) : RecipeGenerationUiState
    data object QuotaRecoveryNeeded : RecipeGenerationUiState
    data class Error(val message: String) : RecipeGenerationUiState
}

sealed interface ChatUiState {
    data object Idle : ChatUiState
    data object Streaming : ChatUiState
    data object QuotaRecoveryNeeded : ChatUiState
    data class Error(val message: String) : ChatUiState
}

sealed interface VoiceParseUiState {
    data object Idle : VoiceParseUiState
    data object Parsing : VoiceParseUiState
    data class Success(
        val parsedItems: List<DetectedIngredient>,
        val tierUsed: AiCapabilityTier
    ) : VoiceParseUiState
    data object QuotaRecoveryNeeded : VoiceParseUiState
    data class Error(val message: String) : VoiceParseUiState
}

class PantryPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PantryDatabase.getInstance(application)
    private val repository = PantryRepository(application, database.pantryDao())
    private val aiService = PantryAiService()
    private val quotaManager = UsageQuotaManager(application)

    val quotaState: StateFlow<UsageQuotaSnapshot> = quotaManager.quotaFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageQuotaSnapshot())

    val pantryItems: StateFlow<List<PantryItemEntity>> = repository.activeItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedRecipes: StateFlow<List<RecipeSuggestion>> = repository.savedRecipesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val restockItems: StateFlow<List<RestockItemEntity>> = repository.restockItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val kitchenSetup: StateFlow<KitchenSetupState> = repository.kitchenSetupFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KitchenSetupState())

    private val _selectedTab = MutableStateFlow(PantryTab.PANTRY)
    val selectedTab: StateFlow<PantryTab> = _selectedTab.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<PantryCategory?>(null)
    val selectedCategoryFilter: StateFlow<PantryCategory?> = _selectedCategoryFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(PantrySortOrder.URGENCY)
    val sortOrder: StateFlow<PantrySortOrder> = _sortOrder.asStateFlow()

    // Recipe Generator Category Filter toggles ('Quick & Easy', 'Healthy/Low-Calorie', 'Budget-Friendly')
    private val _selectedRecipeCategories = MutableStateFlow<Set<RecipeCategoryFilter>>(emptySet())
    val selectedRecipeCategories: StateFlow<Set<RecipeCategoryFilter>> = _selectedRecipeCategories.asStateFlow()

    // Searchable & Filterable Generated Recipe List State (Search Query, Dietary Preferences, Available Ingredients)
    private val _recipeSearchQuery = MutableStateFlow("")
    val recipeSearchQuery: StateFlow<String> = _recipeSearchQuery.asStateFlow()

    private val _selectedDietaryPreferences = MutableStateFlow<Set<RecipeDietaryPreference>>(emptySet())
    val selectedDietaryPreferences: StateFlow<Set<RecipeDietaryPreference>> = _selectedDietaryPreferences.asStateFlow()

    private val _selectedIngredientFilters = MutableStateFlow<Set<String>>(emptySet())
    val selectedIngredientFilters: StateFlow<Set<String>> = _selectedIngredientFilters.asStateFlow()

    // Scan Capture Mode (Fridge Shelf vs. Grocery Receipt)
    private val _scanCaptureMode = MutableStateFlow(ScanCaptureMode.FRIDGE_SHELF)
    val scanCaptureMode: StateFlow<ScanCaptureMode> = _scanCaptureMode.asStateFlow()

    // Up to 2 captured fridge/pantry shelf or receipt photos for Gemini Vision
    private val _capturedBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedBitmaps: StateFlow<List<Bitmap>> = _capturedBitmaps.asStateFlow()

    private val _visionScanState = MutableStateFlow<VisionScanUiState>(VisionScanUiState.Idle)
    val visionScanState: StateFlow<VisionScanUiState> = _visionScanState.asStateFlow()

    private val _recipeGenState = MutableStateFlow<RecipeGenerationUiState>(RecipeGenerationUiState.Idle)
    val recipeGenState: StateFlow<RecipeGenerationUiState> = _recipeGenState.asStateFlow()

    private val _activeCookingRecipe = MutableStateFlow<RecipeSuggestion?>(null)
    val activeCookingRecipe: StateFlow<RecipeSuggestion?> = _activeCookingRecipe.asStateFlow()

    private var lastPendingChatPrompt: String = ""

    private val initialWelcomeMessage = ChatMessage(
        isUser = false,
        text = "Chef Pulse here! I can see your offline fridge inventory, expiring produce, and your owned appliances & spices. Ask me how to rescue expiring items, swap an ingredient, or adapt any recipe!"
    )

    private val _chatMessages = MutableStateFlow(listOf(initialWelcomeMessage))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatUiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            quotaManager.ensureDailyRollover()
        }
    }

    fun selectTab(tab: PantryTab) {
        _selectedTab.value = tab
    }

    fun setCategoryFilter(category: PantryCategory?) {
        _selectedCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: PantrySortOrder) {
        _sortOrder.value = order
    }

    fun toggleRecipeCategoryFilter(category: RecipeCategoryFilter) {
        _selectedRecipeCategories.update { current ->
            if (current.contains(category)) {
                current - category
            } else {
                current + category
            }
        }
    }

    fun clearRecipeCategoryFilters() {
        _selectedRecipeCategories.value = emptySet()
    }

    fun setRecipeSearchQuery(query: String) {
        _recipeSearchQuery.value = query
    }

    fun toggleDietaryPreference(preference: RecipeDietaryPreference) {
        _selectedDietaryPreferences.update { current ->
            if (current.contains(preference)) current - preference else current + preference
        }
    }

    fun toggleIngredientFilter(ingredientName: String) {
        _selectedIngredientFilters.update { current ->
            if (current.contains(ingredientName)) current - ingredientName else current + ingredientName
        }
    }

    fun clearAllRecipeFilters() {
        _recipeSearchQuery.value = ""
        _selectedRecipeCategories.value = emptySet()
        _selectedDietaryPreferences.value = emptySet()
        _selectedIngredientFilters.value = emptySet()
    }

    fun setScanCaptureMode(mode: ScanCaptureMode) {
        _scanCaptureMode.value = mode
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // --- Free-Tier Playbook & Capability Tier Controls ---

    fun setEcoLiteMode(enabled: Boolean) {
        viewModelScope.launch {
            quotaManager.setEcoLiteMode(enabled)
            _snackbarMessage.value = if (enabled) {
                "Eco Lite Mode (Tier 2 ⚡) active — stretching remaining quota 3× further!"
            } else {
                "Switched to Primary Standard AI (Tier 1)."
            }
        }
    }

    fun setForceLocalMode(enabled: Boolean) {
        viewModelScope.launch {
            quotaManager.setForceLocalOfflineMode(enabled)
            _snackbarMessage.value = if (enabled) {
                "Zero-Cost Local Mode (Tier 3 🛡️) active — 0 cloud tokens used."
            } else {
                "Cloud AI capabilities re-enabled."
            }
        }
    }

    fun claimBonusGraceCredits() {
        viewModelScope.launch {
            val claimed = quotaManager.claimBonusGraceCredits()
            if (claimed) {
                if (_visionScanState.value is VisionScanUiState.QuotaRecoveryNeeded) {
                    _visionScanState.value = VisionScanUiState.Idle
                }
                if (_recipeGenState.value is RecipeGenerationUiState.QuotaRecoveryNeeded) {
                    _recipeGenState.value = RecipeGenerationUiState.Idle
                }
                if (_chatUiState.value is ChatUiState.QuotaRecoveryNeeded) {
                    _chatUiState.value = ChatUiState.Idle
                }
                if (_voiceParseState.value is VoiceParseUiState.QuotaRecoveryNeeded) {
                    _voiceParseState.value = VoiceParseUiState.Idle
                }
                _snackbarMessage.value = "Claimed +3 Free Grace Credits! You can continue your task immediately."
            } else {
                _snackbarMessage.value = "Daily +3 Grace Credits already claimed today. Use Eco Lite ⚡ or Local Tier 3 🛡️!"
            }
        }
    }

    fun simulateQuotaPreset(preset: String) {
        viewModelScope.launch {
            quotaManager.simulateQuotaState(preset)
            when (preset) {
                "HEALTHY" -> {
                    _visionScanState.value = VisionScanUiState.Idle
                    _recipeGenState.value = RecipeGenerationUiState.Idle
                    _chatUiState.value = ChatUiState.Idle
                    _voiceParseState.value = VoiceParseUiState.Idle
                    _snackbarMessage.value = "Quota simulated at 30% used (Tier 1 Primary Standard active)."
                }
                "SOFT_NUDGE_80" -> {
                    _snackbarMessage.value = "Simulated Stage 1 (80% used) — Soft Nudge Banner & Eco Lite auto-tier active!"
                }
                "EXHAUSTED_100" -> {
                    _visionScanState.value = VisionScanUiState.QuotaRecoveryNeeded
                    _recipeGenState.value = RecipeGenerationUiState.QuotaRecoveryNeeded
                    _chatUiState.value = ChatUiState.QuotaRecoveryNeeded
                    _voiceParseState.value = VoiceParseUiState.QuotaRecoveryNeeded
                    _snackbarMessage.value = "Simulated Stage 2 (100% / 429) — Smart Quota Recovery Card active!"
                }
            }
        }
    }

    // --- Vision Scan & Tier 3 Local Shelf Check-In ---

    fun addCapturedPhoto(bitmap: Bitmap) {
        _capturedBitmaps.update { current ->
            if (current.size >= 2) {
                listOf(current[1], bitmap)
            } else {
                current + bitmap
            }
        }
    }

    /**
     * Captures a frame from the CameraX viewfinder, appends it to the active 2-photo shelf queue,
     * and immediately triggers the Gemini multimodal ingestion workflow.
     */
    fun captureAndTriggerGeminiIngestion(bitmap: Bitmap, customFocusNote: String = "") {
        addCapturedPhoto(bitmap)
        analyzeCapturedPhotos(customFocusNote = customFocusNote)
    }

    fun removeCapturedPhoto(index: Int) {
        _capturedBitmaps.update { current ->
            current.filterIndexed { i, _ -> i != index }
        }
    }

    fun clearCapturedPhotos() {
        _capturedBitmaps.value = emptyList()
        _visionScanState.value = VisionScanUiState.Idle
    }

    fun loadSamplePhotoAndAnalyze(customNote: String = "") {
        val context = getApplication<Application>()
        val mode = _scanCaptureMode.value
        val sampleBmp = when (mode) {
            ScanCaptureMode.FRIDGE_SHELF -> BitmapFactory.decodeResource(context.resources, R.drawable.img_hero_pantry)
            ScanCaptureMode.GROCERY_RECEIPT -> PantryAiService.createSampleGroceryReceiptBitmap()
        }
        if (sampleBmp != null) {
            _capturedBitmaps.value = listOf(sampleBmp)
            analyzeCapturedPhotos(customNote)
        } else {
            _visionScanState.value = VisionScanUiState.Error("Could not prepare sample scan image.")
        }
    }

    fun runLocalVisionFallback(customFocusNote: String = "") {
        viewModelScope.launch {
            val photos = _capturedBitmaps.value
            val (summary, detected) = LocalCulinaryFallbackEngine.analyzeShelfLocally(
                bitmaps = photos,
                captureMode = _scanCaptureMode.value,
                customFocusNote = customFocusNote
            )
            quotaManager.incrementQueuedScans()
            _visionScanState.value = VisionScanUiState.Success(
                summaryNote = summary,
                detectedItems = detected,
                tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
            )
            _snackbarMessage.value = "Tier 3 Local Shelf Check-In complete (0 cloud credits used)!"
        }
    }

    fun retryVisionWithEcoLite(customFocusNote: String = "") {
        viewModelScope.launch {
            quotaManager.setEcoLiteMode(true)
            analyzeCapturedPhotos(
                customFocusNote = customFocusNote,
                forceTier = AiCapabilityTier.TIER_2_ECO_LITE
            )
        }
    }

    fun analyzeCapturedPhotos(
        customFocusNote: String = "",
        forceTier: AiCapabilityTier? = null
    ) {
        val photos = _capturedBitmaps.value
        if (photos.isEmpty()) {
            _visionScanState.value = VisionScanUiState.Error("Please snap or pick 1–2 photos first.")
            return
        }

        viewModelScope.launch {
            val snap = quotaManager.getSnapshot()
            if (snap.forceLocalOfflineMode && forceTier == null) {
                runLocalVisionFallback(customFocusNote)
                return@launch
            }
            if (snap.isExhausted && forceTier == null) {
                _visionScanState.value = VisionScanUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = "Daily cloud quota reached — choose Grace Credits, Eco Lite ⚡, or 0-Credit Local Check-In!"
                return@launch
            }

            val preferredTier = forceTier ?: snap.activeCapabilityTier
            _visionScanState.value = VisionScanUiState.Analyzing
            try {
                val result = withTimeout(60_000L) {
                    aiService.analyzeFridgePhotos(
                        bitmaps = photos,
                        captureMode = _scanCaptureMode.value,
                        customFocusNote = customFocusNote,
                        preferredTier = preferredTier
                    )
                }
                val (summary, detected) = result.data
                if (detected.isNotEmpty()) {
                    if (!result.fromDedupCache) {
                        quotaManager.recordSuccessfulInteraction(
                            feature = AiFeatureType.VISION_SCAN,
                            usedFallbackCascade = result.autoCascadedToLite
                        )
                    } else {
                        _snackbarMessage.value = "Instant 45s Dedup Cache hit — 0 extra tokens used!"
                    }
                    if (result.autoCascadedToLite) {
                        quotaManager.recordAutoCascade()
                        _snackbarMessage.value = "Auto-cascaded to Tier 2 Eco Lite AI ⚡ to prevent rate-limit interruption."
                    }
                    _visionScanState.value = VisionScanUiState.Success(
                        summaryNote = summary,
                        detectedItems = detected,
                        tierUsed = result.tierUsed
                    )
                } else {
                    _visionScanState.value = VisionScanUiState.Error(
                        "No distinct food ingredients were detected. Try closer lighting or another angle."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini Vision scan timed out", e)
                _visionScanState.value = VisionScanUiState.Error(
                    "Vision analysis timed out after 60 seconds (0 credits charged). You can also use 1-Tap Local Check-In."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Vision scan failed", e)
                if (PantryAiService.isQuotaOrRateLimitException(e)) {
                    _visionScanState.value = VisionScanUiState.QuotaRecoveryNeeded
                } else {
                    _visionScanState.value = VisionScanUiState.Error(
                        e.localizedMessage ?: "Vision analysis failed (0 credits charged)."
                    )
                }
            }
        }
    }

    fun toggleDetectedItemSelection(index: Int) {
        val state = _visionScanState.value
        if (state is VisionScanUiState.Success) {
            val updated = state.detectedItems.mapIndexed { i, item ->
                if (i == index) item.copy(isSelected = !item.isSelected) else item
            }
            _visionScanState.value = state.copy(detectedItems = updated)
        }
    }

    fun adjustDetectedItemDays(index: Int, delta: Int) {
        val state = _visionScanState.value
        if (state is VisionScanUiState.Success) {
            val updated = state.detectedItems.mapIndexed { i, item ->
                if (i == index) item.copy(daysUntilExpiry = (item.daysUntilExpiry + delta).coerceIn(1, 45)) else item
            }
            _visionScanState.value = state.copy(detectedItems = updated)
        }
    }

    fun saveSelectedDetectedItemsToPantry() {
        val state = _visionScanState.value
        if (state is VisionScanUiState.Success) {
            val selected = state.detectedItems.filter { it.isSelected }
            if (selected.isEmpty()) {
                _snackbarMessage.value = "Select at least one detected ingredient to save."
                return
            }
            viewModelScope.launch {
                repository.addDetectedIngredients(selected)
                _snackbarMessage.value = "Saved ${selected.size} scanned items to Offline Pantry & updated Widget!"
                _capturedBitmaps.value = emptyList()
                _visionScanState.value = VisionScanUiState.Idle
                _selectedTab.value = PantryTab.PANTRY
            }
        }
    }

    fun addManualIngredient(
        name: String,
        category: PantryCategory,
        quantity: String,
        daysUntilExpiry: Int,
        conditionNote: String,
        storageTip: String = "Store in appropriate fridge zone to preserve freshness.",
        estimatedValueDollars: Double = 3.75,
        returnToPantry: Boolean = false
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addItem(
                PantryItemEntity(
                    name = name.trim(),
                    category = category.name,
                    quantity = quantity.ifBlank { "1 unit" }.trim(),
                    daysUntilExpiry = daysUntilExpiry.coerceIn(1, 60),
                    visualConditionNote = conditionNote.ifBlank { "Added to offline inventory" }.trim(),
                    storageTip = storageTip.ifBlank { "Store in appropriate fridge zone to preserve freshness." }.trim(),
                    estimatedValueDollars = estimatedValueDollars.coerceIn(0.50, 50.0)
                )
            )
            _snackbarMessage.value = "Added ${name.trim()} to Offline Pantry & updated Widget!"
            if (returnToPantry) {
                _selectedTab.value = PantryTab.PANTRY
            }
        }
    }

    fun addManualBatchIngredients(items: List<DetectedIngredient>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            repository.addDetectedIngredients(items)
            _snackbarMessage.value = "Saved ${items.size} manually entered items to Offline Pantry & updated Widget!"
            _selectedTab.value = PantryTab.PANTRY
        }
    }

    // --- Hands-Free Voice-to-Inventory AI & Tier 3 Local Transcript Fallback ---

    private val _voiceParseState = MutableStateFlow<VoiceParseUiState>(VoiceParseUiState.Idle)
    val voiceParseState: StateFlow<VoiceParseUiState> = _voiceParseState.asStateFlow()

    fun resetVoiceParseState() {
        _voiceParseState.value = VoiceParseUiState.Idle
    }

    fun runLocalVoiceParseFallback(
        transcript: String,
        onItemsParsed: (List<DetectedIngredient>) -> Unit
    ) {
        if (transcript.isBlank()) {
            _voiceParseState.value = VoiceParseUiState.Error("Speak or enter a grocery list first.")
            return
        }
        val localItems = LocalCulinaryFallbackEngine.parseVoiceTranscriptLocally(transcript)
        if (localItems.isNotEmpty()) {
            _voiceParseState.value = VoiceParseUiState.Success(
                parsedItems = localItems,
                tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
            )
            onItemsParsed(localItems)
            _snackbarMessage.value = "Parsed ${localItems.size} items on-device (0 cloud credits • Raw Transcript Local Parser)!"
        } else {
            _voiceParseState.value = VoiceParseUiState.Error("Could not extract ingredients from transcript.")
        }
    }

    fun retryVoiceParseWithEcoLite(
        transcript: String,
        onItemsParsed: (List<DetectedIngredient>) -> Unit
    ) {
        viewModelScope.launch {
            quotaManager.setEcoLiteMode(true)
            parseVoiceMemoToIngredients(
                transcript = transcript,
                forceTier = AiCapabilityTier.TIER_2_ECO_LITE,
                onItemsParsed = onItemsParsed
            )
        }
    }

    fun parseVoiceMemoToIngredients(
        transcript: String,
        forceTier: AiCapabilityTier? = null,
        onItemsParsed: (List<DetectedIngredient>) -> Unit
    ) {
        val cleaned = transcript.trim()
        if (cleaned.isEmpty()) {
            _voiceParseState.value = VoiceParseUiState.Error("Speak or type your grocery dictation first.")
            return
        }

        viewModelScope.launch {
            val snap = quotaManager.getSnapshot()
            if (snap.forceLocalOfflineMode && forceTier == null) {
                runLocalVoiceParseFallback(cleaned, onItemsParsed)
                return@launch
            }
            if (snap.isExhausted && forceTier == null) {
                _voiceParseState.value = VoiceParseUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = "Daily cloud quota reached — claim Grace Credits, use Eco Lite ⚡, or Parse On-Device (0 Credits)!"
                return@launch
            }

            val preferredTier = forceTier ?: snap.activeCapabilityTier
            _voiceParseState.value = VoiceParseUiState.Parsing
            try {
                val result = withTimeout(45_000L) {
                    aiService.parseVoiceInventoryTranscript(
                        transcript = cleaned,
                        preferredTier = preferredTier
                    )
                }
                val parsed = result.data
                if (parsed.isNotEmpty()) {
                    if (!result.fromDedupCache) {
                        quotaManager.recordSuccessfulInteraction(
                            feature = AiFeatureType.VOICE_MEMO_PARSE,
                            usedFallbackCascade = result.autoCascadedToLite
                        )
                    }
                    if (result.autoCascadedToLite) {
                        quotaManager.recordAutoCascade()
                    }
                    _voiceParseState.value = VoiceParseUiState.Success(
                        parsedItems = parsed,
                        tierUsed = result.tierUsed
                    )
                    onItemsParsed(parsed)
                    val suffix = when {
                        result.fromDedupCache -> " [45s Dedup Cache • 0 tokens]"
                        result.tierUsed == AiCapabilityTier.TIER_2_ECO_LITE -> " [Eco Lite ⚡]"
                        else -> ""
                    }
                    _snackbarMessage.value = "Voice AI parsed ${parsed.size} ingredients into your basket$suffix!"
                } else {
                    // Graceful Tier 3 fallback if cloud returned empty list
                    runLocalVoiceParseFallback(cleaned, onItemsParsed)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Voice parse timed out, falling back to local parser", e)
                runLocalVoiceParseFallback(cleaned, onItemsParsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice parse failed", e)
                if (PantryAiService.isQuotaOrRateLimitException(e)) {
                    _voiceParseState.value = VoiceParseUiState.QuotaRecoveryNeeded
                } else {
                    // Graceful Tier 3 fallback on network/service error so user never loses spoken progress
                    runLocalVoiceParseFallback(cleaned, onItemsParsed)
                }
            }
        }
    }

    fun updateExistingIngredient(
        item: PantryItemEntity,
        newName: String,
        newCategory: PantryCategory,
        newQuantity: String,
        newDays: Int,
        newNote: String
    ) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.updateItemDetails(
                item.copy(
                    name = newName.trim(),
                    category = newCategory.name,
                    quantity = newQuantity.ifBlank { item.quantity }.trim(),
                    daysUntilExpiry = newDays.coerceIn(0, 60),
                    visualConditionNote = newNote.trim()
                )
            )
            _snackbarMessage.value = "Updated ${newName.trim()}."
        }
    }

    fun adjustItemDays(item: PantryItemEntity, delta: Int) {
        viewModelScope.launch {
            repository.adjustExpiryDays(item, delta)
        }
    }

    fun markItemUsedUp(item: PantryItemEntity) {
        viewModelScope.launch {
            repository.consumeItem(item)
            _snackbarMessage.value = "Used up ${item.name}! Saved ~$${String.format("%.2f", item.estimatedValueDollars)} & added to Smart Restock."
        }
    }

    fun restockItemToFridge(item: RestockItemEntity) {
        viewModelScope.launch {
            repository.restockItemToFridge(item)
            _snackbarMessage.value = "Restocked ${item.name} (${item.defaultExpiryDays}d countdown) to Fridge!"
        }
    }

    fun dismissRestockItem(item: RestockItemEntity) {
        viewModelScope.launch {
            repository.deleteRestockItem(item.id)
        }
    }

    fun deleteItem(item: PantryItemEntity) {
        viewModelScope.launch {
            repository.deleteItem(item.id)
        }
    }

    fun toggleAppliance(appliance: String) {
        viewModelScope.launch {
            repository.toggleAppliance(appliance)
        }
    }

    fun toggleSpice(spice: String) {
        viewModelScope.launch {
            repository.toggleSpice(spice)
        }
    }

    // --- Recipe Ranking & Tier 3 On-Device Culinary Synthesis ---

    private fun buildCombinedDietaryAndIngredientNote(userCravingNote: String): String {
        val parts = mutableListOf<String>()
        if (userCravingNote.isNotBlank()) {
            parts.add(userCravingNote.trim())
        }
        val activeDietary = _selectedDietaryPreferences.value
        if (activeDietary.isNotEmpty()) {
            parts.add("Dietary Preferences Required: " + activeDietary.joinToString("; ") { it.aiPromptInstruction })
        }
        val activeIngredients = _selectedIngredientFilters.value
        if (activeIngredients.isNotEmpty()) {
            parts.add("Must Feature Available Ingredients: " + activeIngredients.joinToString(", "))
        }
        return parts.joinToString(" | ")
    }

    fun runLocalRecipeFallback(cravingOrNote: String = "") {
        val currentItems = pantryItems.value
        if (currentItems.isEmpty()) {
            _recipeGenState.value = RecipeGenerationUiState.Error(
                "Your pantry is empty! Scan your fridge or add ingredients first."
            )
            return
        }
        viewModelScope.launch {
            val activeCats = _selectedRecipeCategories.value
            val combinedNote = buildCombinedDietaryAndIngredientNote(cravingOrNote)
            val prioritizedItems = if (_selectedIngredientFilters.value.isNotEmpty()) {
                currentItems.sortedByDescending { item ->
                    _selectedIngredientFilters.value.contains(item.name)
                }
            } else {
                currentItems
            }
            val localRecipes = LocalCulinaryFallbackEngine.synthesizeRecipesLocally(
                items = prioritizedItems,
                kitchenSetup = kitchenSetup.value,
                selectedCategories = activeCats,
                cravingNote = combinedNote
            )
            repository.saveGeneratedRecipes(localRecipes)
            _recipeGenState.value = RecipeGenerationUiState.Success(
                count = localRecipes.size,
                tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
            )
            _snackbarMessage.value = "Synthesized 3 Rescue Recipes via Tier 3 On-Device Chef Engine (0 cloud credits)!"
        }
    }

    fun retryRecipesWithEcoLite(cravingOrNote: String = "") {
        viewModelScope.launch {
            quotaManager.setEcoLiteMode(true)
            generateRankedRecipes(
                cravingOrNote = cravingOrNote,
                forceTier = AiCapabilityTier.TIER_2_ECO_LITE
            )
        }
    }

    fun generateRankedRecipes(
        cravingOrNote: String = "",
        forceTier: AiCapabilityTier? = null
    ) {
        val currentItems = pantryItems.value
        if (currentItems.isEmpty()) {
            _recipeGenState.value = RecipeGenerationUiState.Error(
                "Your pantry is empty! Scan your fridge or add ingredients first."
            )
            return
        }

        viewModelScope.launch {
            val snap = quotaManager.getSnapshot()
            if (snap.forceLocalOfflineMode && forceTier == null) {
                runLocalRecipeFallback(cravingOrNote)
                return@launch
            }
            if (snap.isExhausted && forceTier == null) {
                _recipeGenState.value = RecipeGenerationUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = "Daily cloud quota reached — claim Grace Credits, run Eco Lite ⚡, or use On-Device Chef!"
                return@launch
            }

            val preferredTier = forceTier ?: snap.activeCapabilityTier
            _recipeGenState.value = RecipeGenerationUiState.Generating
            try {
                val activeCats = _selectedRecipeCategories.value
                val combinedNote = buildCombinedDietaryAndIngredientNote(cravingOrNote)
                val result = withTimeout(60_000L) {
                    aiService.generateRankedRescueRecipes(
                        items = currentItems,
                        kitchenSetup = kitchenSetup.value,
                        selectedCategories = activeCats,
                        dietaryOrCravingNote = combinedNote,
                        preferredTier = preferredTier
                    )
                }
                val recipes = result.data
                if (recipes.isNotEmpty()) {
                    repository.saveGeneratedRecipes(recipes)
                    if (!result.fromDedupCache) {
                        quotaManager.recordSuccessfulInteraction(
                            feature = AiFeatureType.RECIPE_RANKING,
                            usedFallbackCascade = result.autoCascadedToLite
                        )
                    }
                    if (result.autoCascadedToLite) {
                        quotaManager.recordAutoCascade()
                    }
                    _recipeGenState.value = RecipeGenerationUiState.Success(
                        count = recipes.size,
                        tierUsed = result.tierUsed
                    )
                    val filterSummary = if (activeCats.isEmpty()) {
                        "all categories"
                    } else {
                        activeCats.joinToString(", ") { it.displayName }
                    }
                    val tierSuffix = when {
                        result.fromDedupCache -> " [45s Dedup Cache • 0 tokens]"
                        result.tierUsed == AiCapabilityTier.TIER_2_ECO_LITE -> " [Eco Lite ⚡]"
                        else -> ""
                    }
                    _snackbarMessage.value = "Ranked ${recipes.size} recipes ($filterSummary)$tierSuffix!"
                } else {
                    _recipeGenState.value = RecipeGenerationUiState.Error("Model returned no recipes.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Recipe ranking timed out", e)
                _recipeGenState.value = RecipeGenerationUiState.Error(
                    "Recipe generation timed out after 60 seconds (0 credits charged)."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recipe ranking failed", e)
                if (PantryAiService.isQuotaOrRateLimitException(e)) {
                    _recipeGenState.value = RecipeGenerationUiState.QuotaRecoveryNeeded
                } else {
                    _recipeGenState.value = RecipeGenerationUiState.Error(
                        e.localizedMessage ?: "Failed to generate rescue recipes (0 credits charged)."
                    )
                }
            }
        }
    }

    fun openCookingMode(recipe: RecipeSuggestion) {
        _activeCookingRecipe.value = recipe
    }

    fun closeCookingMode() {
        _activeCookingRecipe.value = null
    }

    fun completeCookingRecipe(recipe: RecipeSuggestion) {
        viewModelScope.launch {
            repository.completeRescueMeal(recipe, pantryItems.value)
            _activeCookingRecipe.value = null
            _snackbarMessage.value = "Cooked '${recipe.title}'! Expiring ingredients rescued & widget updated."
        }
    }

    // --- Sous-Chef Chat & Tier 3 Instant Local Reflex Engine ---

    fun clearChatHistory() {
        if (_chatUiState.value is ChatUiState.Streaming) return
        _chatMessages.value = listOf(initialWelcomeMessage)
        _chatUiState.value = ChatUiState.Idle
    }

    fun sendLocalReflexChatMessage(customPrompt: String = "") {
        val promptToUse = customPrompt.ifBlank { lastPendingChatPrompt }
            .ifBlank { "What can I cook tonight with my expiring fridge items?" }

        val needsUserBubble = customPrompt.isNotBlank()
        val reflexReply = LocalCulinaryFallbackEngine.generateLocalReflexReply(
            userMessage = promptToUse,
            items = pantryItems.value,
            kitchenSetup = kitchenSetup.value
        )

        _chatMessages.update { current ->
            val withUser = if (needsUserBubble) {
                current + ChatMessage(isUser = true, text = promptToUse)
            } else {
                current
            }
            withUser + ChatMessage(isUser = false, text = reflexReply, isStreaming = false)
        }
        _chatUiState.value = ChatUiState.Idle
        _snackbarMessage.value = "Instant Local Reflex Chef replied (0 cloud credits used)!"
    }

    fun retryChatWithEcoLite() {
        val prompt = lastPendingChatPrompt.ifBlank { "How should I use my expiring produce tonight?" }
        viewModelScope.launch {
            quotaManager.setEcoLiteMode(true)
            sendChefChatMessage(
                prompt = prompt,
                forceTier = AiCapabilityTier.TIER_2_ECO_LITE,
                appendUserBubble = false
            )
        }
    }

    fun sendChefChatMessage(
        prompt: String,
        forceTier: AiCapabilityTier? = null,
        appendUserBubble: Boolean = true
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || _chatUiState.value is ChatUiState.Streaming) return
        lastPendingChatPrompt = trimmed

        viewModelScope.launch {
            val snap = quotaManager.getSnapshot()
            if (snap.forceLocalOfflineMode && forceTier == null) {
                sendLocalReflexChatMessage(trimmed)
                return@launch
            }

            if (appendUserBubble) {
                val userMsg = ChatMessage(isUser = true, text = trimmed)
                _chatMessages.update { it + userMsg }
            }

            if (snap.isExhausted && forceTier == null) {
                _chatUiState.value = ChatUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = "Daily cloud quota reached — choose Grace Credits, Eco Lite ⚡, or Local Reflex Chef!"
                return@launch
            }

            val preferredTier = forceTier ?: snap.activeCapabilityTier
            val historySnapshot = _chatMessages.value.map { it.isUser to it.text }
            val streamingId = java.util.UUID.randomUUID().toString()
            val placeholderAiMsg = ChatMessage(
                id = streamingId,
                isUser = false,
                text = "",
                isStreaming = true
            )

            _chatMessages.update { it + placeholderAiMsg }
            _chatUiState.value = ChatUiState.Streaming

            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    aiService.streamChefChatResponse(
                        userMessage = trimmed,
                        items = pantryItems.value,
                        kitchenSetup = kitchenSetup.value,
                        recentHistory = historySnapshot,
                        preferredTier = preferredTier
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        val currentText = accumulated.toString()
                        _chatMessages.update { list ->
                            list.map { msg ->
                                if (msg.id == streamingId) {
                                    msg.copy(text = currentText, isStreaming = true)
                                } else {
                                    msg
                                }
                            }
                        }
                    }
                }
                val finalText = accumulated.toString()
                if (finalText.isNotBlank()) {
                    quotaManager.recordSuccessfulInteraction(
                        feature = AiFeatureType.SOUS_CHEF_CHAT,
                        usedFallbackCascade = false
                    )
                    _chatMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == streamingId) msg.copy(text = finalText, isStreaming = false) else msg
                        }
                    }
                    _chatUiState.value = ChatUiState.Idle
                } else {
                    _chatMessages.update { list -> list.filterNot { it.id == streamingId } }
                    _chatUiState.value = ChatUiState.Error("Sous-Chef returned an empty response (0 credits charged).")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Sous-Chef streaming timed out", e)
                _chatMessages.update { list ->
                    list.map { msg ->
                        if (msg.id == streamingId) msg.copy(isStreaming = false) else msg
                    }.filter { it.text.isNotBlank() || it.isUser }
                }
                _chatUiState.value = ChatUiState.Error("Sous-Chef response timed out after 60 seconds (0 credits charged).")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sous-Chef streaming failed", e)
                _chatMessages.update { list -> list.filterNot { it.id == streamingId && it.text.isBlank() } }
                if (PantryAiService.isQuotaOrRateLimitException(e)) {
                    _chatUiState.value = ChatUiState.QuotaRecoveryNeeded
                } else {
                    _chatUiState.value = ChatUiState.Error(e.localizedMessage ?: "Sous-Chef chat failed (0 credits charged).")
                }
            }
        }
    }

    fun requestPinOrRefreshWidget() {
        val context = getApplication<Application>()
        PantryPulseWidgetProvider.updateAllWidgets(context)
        val pinned = PantryPulseWidgetProvider.requestPinWidget(context)
        _snackbarMessage.value = if (pinned) {
            "Home Screen Widget pin requested! Expiring Soon badge synced."
        } else {
            "Widget data refreshed! Long-press your Android Home Screen -> Widgets -> PantryPulse to add."
        }
    }
}
