package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.AiCapabilityTier
import com.example.ai.AiFeatureType
import com.example.ai.BarcodeLookupSource
import com.example.ai.BarcodeScanningUtility
import com.example.ai.LocalCulinaryFallbackEngine
import com.example.ai.PantryAiService
import com.example.ai.SecureByokKeyStore
import com.example.ai.UsageQuotaManager
import com.example.ai.UsageQuotaSnapshot
import com.example.data.ChatMessage
import com.example.data.DetectedIngredient
import com.example.data.Ingredient
import com.example.data.KitchenSetupState
import com.example.data.PantryCategory
import com.example.data.PantryDatabase
import com.example.data.PantryItemEntity
import com.example.data.PantryRepository
import com.example.data.PreservationGuide
import com.example.data.RecipeCategoryFilter
import com.example.data.RecipeDietaryPreference
import com.example.data.RecipeSuggestion
import com.example.data.RestockItemEntity
import com.example.data.ScanCaptureMode
import com.example.data.computeExpiryEpochMillis
import android.content.Context
import com.example.ui.engagement.AppEngagementAndDonationManager
import com.example.ui.engagement.EngagementSnapshot
import com.example.ui.engagement.GooglePlayDonationTier
import com.example.ui.engagement.SmartAutoPromptType
import com.example.ui.engagement.findActivity
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
    SHOPPING_LIST,
    CHEF_CHAT
}

enum class PantrySortOrder(val label: String, @get:StringRes val labelRes: Int) {
    URGENCY("Expiring First", R.string.sort_urgency),
    VALUE_HIGH("Highest Value $", R.string.sort_value_high),
    RECENT("Recently Added", R.string.sort_recent)
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

sealed interface PreservationCoachUiState {
    data object Idle : PreservationCoachUiState
    data object Analyzing : PreservationCoachUiState
    data class Success(
        val summaryNote: String,
        val guides: List<PreservationGuide>,
        val tierUsed: AiCapabilityTier = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
    ) : PreservationCoachUiState
    data object QuotaRecoveryNeeded : PreservationCoachUiState
    data class Error(val message: String) : PreservationCoachUiState
}

class PantryPulseViewModel(application: Application) : AndroidViewModel(application) {

    private fun str(@StringRes resId: Int, vararg formatArgs: Any): String =
        getApplication<Application>().getString(resId, *formatArgs)

    private val database = PantryDatabase.getInstance(application)
    private val repository = PantryRepository(
        appContext = application,
        dao = database.pantryDao(),
        ingredientDao = database.ingredientDao(),
        recipeDao = database.recipeDao()
    )
    private val byokKeyStore = SecureByokKeyStore(application)
    private val barcodeScanningUtility = BarcodeScanningUtility(application)
    private val aiService = PantryAiService(
        byokApiKeyProvider = { byokKeyStore.getDecryptedApiKey() }
    )
    private val quotaManager = UsageQuotaManager(application)
    private val engagementManager = AppEngagementAndDonationManager(application)

    val hasByokKey: StateFlow<Boolean> = byokKeyStore.hasByokKey
    val byokKeyPreview: StateFlow<String> = byokKeyStore.maskedKeyPreview

    private val _isLookingUpBarcode = MutableStateFlow(false)
    val isLookingUpBarcode: StateFlow<Boolean> = _isLookingUpBarcode.asStateFlow()

    private val _lastPopulatedIngredient = MutableStateFlow<Ingredient?>(null)
    val lastPopulatedIngredient: StateFlow<Ingredient?> = _lastPopulatedIngredient.asStateFlow()

    val quotaState: StateFlow<UsageQuotaSnapshot> = quotaManager.quotaFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), quotaManager.localMirrorSnapshot)

    val engagementState: StateFlow<EngagementSnapshot> = engagementManager.stateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EngagementSnapshot())

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

    private val initialWelcomeMessage: ChatMessage
        get() = ChatMessage(
            isUser = false,
            text = str(R.string.sous_chef_welcome_msg)
        )

    private val _chatMessages = MutableStateFlow(listOf(initialWelcomeMessage))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _chatUiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    private val _preservationState = MutableStateFlow<PreservationCoachUiState>(PreservationCoachUiState.Idle)
    val preservationState: StateFlow<PreservationCoachUiState> = _preservationState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.syncOnStartup()
            quotaManager.ensureDailyRollover()
        }
        viewModelScope.launch {
            repository.activeItemsFlow.collect { currentItems ->
                val currentPreservation = _preservationState.value
                if (currentItems.isEmpty()) {
                    if (currentPreservation !is PreservationCoachUiState.Idle) {
                        _preservationState.value = PreservationCoachUiState.Idle
                    }
                } else if (currentPreservation is PreservationCoachUiState.Idle ||
                    (currentPreservation is PreservationCoachUiState.Success &&
                        currentPreservation.tierUsed == AiCapabilityTier.TIER_3_LOCAL_FALLBACK)
                ) {
                    val (summary, localGuides) = LocalCulinaryFallbackEngine.synthesizePreservationGuidesLocally(currentItems)
                    _preservationState.value = PreservationCoachUiState.Success(
                        summaryNote = summary,
                        guides = localGuides,
                        tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
                    )
                }
            }
        }
    }

    fun selectTab(tab: PantryTab) {
        _selectedTab.value = tab
    }

    fun refreshOfflineExpiryCountdowns() {
        viewModelScope.launch {
            repository.refreshDynamicExpiryCountdowns()
        }
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
                str(R.string.vm_eco_lite_on)
            } else {
                str(R.string.vm_eco_lite_off)
            }
        }
    }

    fun setForceLocalMode(enabled: Boolean) {
        viewModelScope.launch {
            quotaManager.setForceLocalOfflineMode(enabled)
            _snackbarMessage.value = if (enabled) {
                str(R.string.vm_local_mode_on)
            } else {
                str(R.string.vm_local_mode_off)
            }
        }
    }

    fun claimBonusGraceCredits() {
        viewModelScope.launch {
            val claimed = quotaManager.claimBonusGraceCredits()
            if (claimed) {
                clearQuotaRecoveryStates()
                _snackbarMessage.value = str(R.string.vm_grace_claimed)
            } else {
                _snackbarMessage.value = str(R.string.vm_grace_already_claimed)
            }
        }
    }

    private fun clearQuotaRecoveryStates() {
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
        if (_preservationState.value is PreservationCoachUiState.QuotaRecoveryNeeded) {
            _preservationState.value = PreservationCoachUiState.Idle
        }
    }

    fun saveByokApiKey(rawKey: String) {
        val saved = byokKeyStore.saveApiKey(rawKey)
        if (saved) {
            clearQuotaRecoveryStates()
            _snackbarMessage.value = str(R.string.byok_saved_toast)
        } else {
            _snackbarMessage.value = str(R.string.byok_invalid_toast)
        }
    }

    fun clearByokApiKey() {
        byokKeyStore.clearApiKey()
        _snackbarMessage.value = str(R.string.byok_cleared_toast)
    }

    fun purchaseCreditPack(uiContext: Context, credits: Int) {
        val tier = if (credits >= 100) GooglePlayDonationTier.TIER_2 else GooglePlayDonationTier.TIER_1
        engagementManager.completeGooglePlayDonation(uiContext, tier) { _ ->
            viewModelScope.launch {
                quotaManager.grantPurchasedCreditPack(credits)
                clearQuotaRecoveryStates()
                _snackbarMessage.value = str(R.string.credit_pack_granted_toast_fmt, credits)
            }
        }
    }

    /**
     * Tier 0 ($0 API cost) offline-first grocery barcode lookup routed through
     * [PantryRepository.fetchProductAndPopulateIngredient] (DataStore Cache -> Open Food Facts API
     * -> Offline Catalog) to auto-populate the [Ingredient] Room entity.
     */
    fun lookupBarcodeTier0(rawBarcode: String, autoSaveToPantry: Boolean = false) {
        if (_isLookingUpBarcode.value) return
        viewModelScope.launch {
            _isLookingUpBarcode.value = true
            try {
                val result = repository.fetchProductAndPopulateIngredient(
                    rawBarcode = rawBarcode,
                    persistToDatabase = autoSaveToPantry
                )
                val foundItem = result.detectedIngredient
                val populatedEntity = result.ingredient
                if (result.found && foundItem != null && populatedEntity != null) {
                    _lastPopulatedIngredient.value = populatedEntity
                    val toastMsg = if (result.source == BarcodeLookupSource.OFFLINE_CACHE) {
                        str(R.string.tier0_barcode_cache_found_toast_fmt, foundItem.name)
                    } else {
                        str(R.string.tier0_barcode_found_toast_fmt, foundItem.name)
                    }
                    if (!autoSaveToPantry) {
                        val existing = (_visionScanState.value as? VisionScanUiState.Success)?.detectedItems.orEmpty()
                        _visionScanState.value = VisionScanUiState.Success(
                            summaryNote = toastMsg,
                            detectedItems = listOf(foundItem) + existing.filterNot {
                                it.name.equals(foundItem.name, ignoreCase = true)
                            },
                            tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
                        )
                    }
                    _snackbarMessage.value = toastMsg
                } else {
                    _snackbarMessage.value = result.errorMessage ?: str(R.string.tier0_barcode_lookup_failed)
                }
            } finally {
                _isLookingUpBarcode.value = false
            }
        }
    }

    /**
     * Scans a captured or Photo-Picker bitmap with Google ML Kit EAN-13/UPC detection
     * and resolves the product via [PantryRepository.fetchProductAndPopulateIngredient].
     */
    fun scanPhotoForBarcodeTier0(bitmap: Bitmap) {
        if (_isLookingUpBarcode.value) return
        viewModelScope.launch {
            _isLookingUpBarcode.value = true
            try {
                val codes = barcodeScanningUtility.detectEan13OrUpcCodes(bitmap)
                    .ifEmpty { barcodeScanningUtility.scanBitmapForBarcodes(bitmap) }
                val firstCode = codes.firstOrNull()?.rawValue
                if (firstCode != null) {
                    _isLookingUpBarcode.value = false
                    lookupBarcodeTier0(firstCode)
                } else {
                    _snackbarMessage.value = str(R.string.tier0_barcode_none_in_photo)
                }
            } finally {
                _isLookingUpBarcode.value = false
            }
        }
    }

    fun syncWeeklyPlanStaplesToShoppingList(staples: List<String>) {
        if (staples.isEmpty()) return
        viewModelScope.launch {
            staples.forEach { staple ->
                repository.addManualShoppingItem(
                    name = staple,
                    category = PantryCategory.PANTRY_STAPLE,
                    suggestedQuantity = "1 pack",
                    estimatedCostDollars = 2.99,
                    buyingTip = str(R.string.shopping_default_tip)
                )
            }
            _snackbarMessage.value = str(R.string.weekly_plan_synced_toast_fmt, staples.size)
        }
    }

    fun askSousChefFromCookingVoice(question: String) {
        _activeCookingRecipe.value = null
        _selectedTab.value = PantryTab.CHEF_CHAT
        sendChefChatMessage(question)
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
                    _snackbarMessage.value = str(R.string.vm_quota_sim_healthy)
                }
                "SOFT_NUDGE_80" -> {
                    _snackbarMessage.value = str(R.string.vm_quota_sim_soft)
                }
                "EXHAUSTED_100" -> {
                    _visionScanState.value = VisionScanUiState.QuotaRecoveryNeeded
                    _recipeGenState.value = RecipeGenerationUiState.QuotaRecoveryNeeded
                    _chatUiState.value = ChatUiState.QuotaRecoveryNeeded
                    _voiceParseState.value = VoiceParseUiState.QuotaRecoveryNeeded
                    _preservationState.value = PreservationCoachUiState.QuotaRecoveryNeeded
                    _snackbarMessage.value = str(R.string.vm_quota_sim_exhausted)
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

    fun runLocalVisionFallback(customFocusNote: String = "") {
        viewModelScope.launch {
            val photos = _capturedBitmaps.value
            val (summary, detected) = LocalCulinaryFallbackEngine.analyzeShelfLocally(
                bitmaps = photos,
                captureMode = _scanCaptureMode.value,
                customFocusNote = customFocusNote
            )
            if (detected.isEmpty()) {
                _visionScanState.value = VisionScanUiState.Error(
                    str(R.string.vm_vision_local_empty_hint)
                )
            } else {
                quotaManager.incrementQueuedScans()
                _visionScanState.value = VisionScanUiState.Success(
                    summaryNote = summary,
                    detectedItems = detected,
                    tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
                )
                _snackbarMessage.value = str(R.string.vm_vision_local_done)
            }
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
            _visionScanState.value = VisionScanUiState.Error(str(R.string.vm_vision_pick_first))
            return
        }

        viewModelScope.launch {
            val snap = quotaManager.getSnapshot()
            if (snap.forceLocalOfflineMode && forceTier == null) {
                runLocalVisionFallback(customFocusNote)
                return@launch
            }
            val byokActive = hasByokKey.value
            if (snap.isExhausted && forceTier == null && !byokActive) {
                _visionScanState.value = VisionScanUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = str(R.string.vm_vision_quota_reached)
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
                    if (!result.fromDedupCache && !result.usedByokKey) {
                        quotaManager.recordSuccessfulInteraction(
                            feature = AiFeatureType.VISION_SCAN,
                            usedFallbackCascade = result.autoCascadedToLite
                        )
                    } else if (result.fromDedupCache) {
                        _snackbarMessage.value = str(R.string.vm_vision_dedup_hit)
                    }
                    if (result.autoCascadedToLite) {
                        quotaManager.recordAutoCascade()
                        _snackbarMessage.value = str(R.string.vm_vision_auto_eco)
                    }
                    _visionScanState.value = VisionScanUiState.Success(
                        summaryNote = summary,
                        detectedItems = detected,
                        tierUsed = result.tierUsed
                    )
                } else {
                    _visionScanState.value = VisionScanUiState.Error(
                        str(R.string.vm_vision_none_detected)
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini Vision scan timed out; falling back to local engine", e)
                runLocalVisionFallback(customFocusNote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Vision scan failed", e)
                if (PantryAiService.isQuotaOrRateLimitException(e)) {
                    _visionScanState.value = VisionScanUiState.QuotaRecoveryNeeded
                } else {
                    // Graceful Tier 3 on-device fallback when offline or cloud backend unavailable
                    runLocalVisionFallback(customFocusNote)
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
                _snackbarMessage.value = str(R.string.vm_select_one_detected)
                return
            }
            viewModelScope.launch {
                val autoAdded = repository.addDetectedIngredients(selected)
                engagementManager.recordCoreOperationCompleted(
                    operationKey = "VISION_SCAN_SAVED",
                    humanReadableTitle = str(R.string.op_title_vision_saved_fmt, selected.size)
                )
                val autoNote = if (autoAdded.isNotEmpty()) {
                    str(R.string.vm_auto_added_shopping_fmt, autoAdded.size)
                } else ""
                _snackbarMessage.value = str(R.string.vm_saved_scanned_fmt, selected.size) + autoNote
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
        storageTip: String = "",
        estimatedValueDollars: Double = 3.75,
        returnToPantry: Boolean = false
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val clampedDays = daysUntilExpiry.coerceIn(0, 90)
            val now = System.currentTimeMillis()
            val resolvedUnit = quantity.ifBlank { str(R.string.qty_1_unit) }.trim()
            val resolvedNote = conditionNote.ifBlank { str(R.string.vm_default_condition_note) }.trim()
            val resolvedTip = storageTip.ifBlank { str(R.string.vm_default_storage_tip) }.trim()
            val autoAdded = repository.addItem(
                PantryItemEntity(
                    name = name.trim(),
                    category = category.name,
                    quantity = resolvedUnit,
                    daysUntilExpiry = clampedDays,
                    expiryDateEpochMillis = computeExpiryEpochMillis(clampedDays, now),
                    visualConditionNote = resolvedNote,
                    storageTip = resolvedTip,
                    estimatedValueDollars = estimatedValueDollars.coerceIn(0.50, 100.0),
                    stockPercent = PantryRepository.inferStockPercentFromQuantity(resolvedUnit),
                    lowStockThresholdPercent = kitchenSetup.value.lowStockThresholdPercent,
                    addedTimestamp = now,
                    updatedTimestamp = now
                )
            )
            engagementManager.recordCoreOperationCompleted(
                operationKey = "MANUAL_ITEM_ADDED",
                humanReadableTitle = str(R.string.op_title_manual_added_fmt, name.trim())
            )
            val autoSuffix = if (autoAdded.isNotEmpty()) str(R.string.vm_manual_auto_suffix) else ""
            _snackbarMessage.value = str(R.string.vm_manual_added_fmt, name.trim(), autoSuffix)
            if (returnToPantry) {
                _selectedTab.value = PantryTab.PANTRY
            }
        }
    }

    fun addManualBatchIngredients(items: List<DetectedIngredient>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val autoAdded = repository.addDetectedIngredients(items)
            engagementManager.recordCoreOperationCompleted(
                operationKey = "BATCH_ITEMS_ADDED",
                humanReadableTitle = str(R.string.op_title_batch_added_fmt, items.size)
            )
            val autoSuffix = if (autoAdded.isNotEmpty()) {
                str(R.string.vm_auto_added_shopping_fmt, autoAdded.size)
            } else ""
            _snackbarMessage.value = str(R.string.vm_saved_manual_batch_fmt, items.size, autoSuffix)
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
            _voiceParseState.value = VoiceParseUiState.Error(str(R.string.vm_voice_empty))
            return
        }
        val localItems = LocalCulinaryFallbackEngine.parseVoiceTranscriptLocally(transcript)
        if (localItems.isNotEmpty()) {
            _voiceParseState.value = VoiceParseUiState.Success(
                parsedItems = localItems,
                tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
            )
            onItemsParsed(localItems)
            _snackbarMessage.value = str(R.string.vm_voice_local_done_fmt, localItems.size)
        } else {
            _voiceParseState.value = VoiceParseUiState.Error(str(R.string.vm_voice_extract_failed))
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
            _voiceParseState.value = VoiceParseUiState.Error(str(R.string.vm_voice_dictation_empty))
            return
        }

        viewModelScope.launch {
            val snap = quotaManager.getSnapshot()
            if (snap.forceLocalOfflineMode && forceTier == null) {
                runLocalVoiceParseFallback(cleaned, onItemsParsed)
                return@launch
            }
            if (snap.isExhausted && forceTier == null && !hasByokKey.value) {
                _voiceParseState.value = VoiceParseUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = str(R.string.vm_voice_quota_reached)
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
                    if (!result.fromDedupCache && !result.usedByokKey) {
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
                        result.fromDedupCache -> str(R.string.vm_tier_suffix_dedup)
                        result.tierUsed == AiCapabilityTier.TIER_2_ECO_LITE -> str(R.string.vm_tier_suffix_eco)
                        else -> ""
                    }
                    _snackbarMessage.value = str(R.string.vm_voice_parsed_fmt, parsed.size, suffix)
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
            val clampedDays = newDays.coerceIn(0, 90)
            val now = System.currentTimeMillis()
            repository.updateItemDetails(
                item.copy(
                    name = newName.trim(),
                    category = newCategory.name,
                    quantity = newQuantity.ifBlank { item.quantity }.trim(),
                    daysUntilExpiry = clampedDays,
                    expiryDateEpochMillis = computeExpiryEpochMillis(clampedDays, now),
                    visualConditionNote = newNote.trim(),
                    updatedTimestamp = now
                )
            )
            _snackbarMessage.value = str(R.string.vm_updated_item_fmt, newName.trim())
        }
    }

    fun adjustItemDays(item: PantryItemEntity, delta: Int) {
        viewModelScope.launch {
            repository.adjustExpiryDays(item, delta)
        }
    }

    fun adjustItemStock(item: PantryItemEntity, deltaPercent: Int) {
        viewModelScope.launch {
            val newlyAutoAdded = repository.adjustItemStockPercent(item, deltaPercent)
            val newStock = (item.stockPercent + deltaPercent).coerceIn(0, 100)
            engagementManager.logCustomEvent(
                eventType = "STOCK_LEVEL_ADJUSTED",
                detail = "${item.name}: ${item.stockPercent}% -> $newStock%",
                algorithmNote = if (newlyAutoAdded.isNotEmpty()) {
                    "Low-stock threshold (≤${item.lowStockThresholdPercent}%) reached -> auto-added to Smart Shopping List."
                } else {
                    "Stock level above low-stock threshold."
                }
            )
            if (newStock == 0) {
                _snackbarMessage.value = str(R.string.vm_used_up_auto_fmt, item.name)
            } else if (newlyAutoAdded.isNotEmpty()) {
                _snackbarMessage.value = str(R.string.vm_stock_low_added_fmt, item.name, newStock, item.lowStockThresholdPercent)
            }
        }
    }

    fun updateLowStockThreshold(newThresholdPercent: Int) {
        viewModelScope.launch {
            val newlyAdded = repository.setGlobalLowStockThreshold(newThresholdPercent)
            engagementManager.logCustomEvent(
                eventType = "LOW_STOCK_THRESHOLD_UPDATED",
                detail = "Threshold set to ≤$newThresholdPercent%",
                algorithmNote = "Auto-added ${newlyAdded.size} low-stock inventory item(s) to Smart Shopping List."
            )
            _snackbarMessage.value = if (newlyAdded.isNotEmpty()) {
                str(R.string.vm_threshold_updated_auto_fmt, newThresholdPercent, newlyAdded.joinToString())
            } else {
                str(R.string.vm_threshold_updated_fmt, newThresholdPercent)
            }
        }
    }

    fun runManualLowStockScan() {
        viewModelScope.launch {
            val newlyAdded = repository.syncLowStockToShoppingList()
            _snackbarMessage.value = if (newlyAdded.isNotEmpty()) {
                str(R.string.vm_low_stock_found_fmt, newlyAdded.size, newlyAdded.joinToString())
            } else {
                str(R.string.vm_low_stock_all_listed_fmt, kitchenSetup.value.lowStockThresholdPercent)
            }
        }
    }

    fun toggleShoppingItemChecked(item: RestockItemEntity) {
        viewModelScope.launch {
            repository.toggleShoppingItemChecked(item)
        }
    }

    fun addManualShoppingItem(
        name: String,
        category: PantryCategory,
        quantity: String,
        estimatedCostUsd: Double,
        tip: String
    ) {
        viewModelScope.launch {
            repository.addManualShoppingItem(
                name = name,
                category = category,
                suggestedQuantity = quantity,
                estimatedCostDollars = estimatedCostUsd,
                buyingTip = tip
            )
            _snackbarMessage.value = str(R.string.vm_added_shopping_fmt, name)
        }
    }

    fun moveLowSupplyItemToShoppingList(item: PantryItemEntity) {
        viewModelScope.launch {
            val added = repository.movePantryItemToShoppingList(item)
            if (added) {
                engagementManager.recordCoreOperationCompleted(
                    operationKey = "MOVE_LOW_SUPPLY_TO_SHOPPING",
                    humanReadableTitle = str(R.string.op_title_moved_low_single_fmt, item.name)
                )
                _snackbarMessage.value = str(R.string.vm_moved_shopping_fmt, item.name, item.stockPercent)
            } else {
                _snackbarMessage.value = str(R.string.vm_already_in_shopping_fmt, item.name)
            }
        }
    }

    fun moveAllLowSupplyToShoppingList() {
        viewModelScope.launch {
            val count = repository.moveAllLowSupplyItemsToShoppingList()
            if (count > 0) {
                engagementManager.recordCoreOperationCompleted(
                    operationKey = "MOVE_ALL_LOW_SUPPLY",
                    humanReadableTitle = str(R.string.op_title_moved_low_all_fmt, count)
                )
                _snackbarMessage.value = str(R.string.vm_moved_all_low_fmt, count)
            } else {
                _snackbarMessage.value = str(R.string.vm_all_low_in_shopping)
            }
        }
    }

    fun restockAllCheckedShoppingItems() {
        viewModelScope.launch {
            val count = repository.restockAllCheckedItems()
            if (count > 0) {
                engagementManager.recordCoreOperationCompleted(
                    operationKey = "SMART_SHOPPING_RESTOCK",
                    humanReadableTitle = str(R.string.op_title_restocked_checked_fmt, count)
                )
                _snackbarMessage.value = str(R.string.vm_restocked_checked_fmt, count)
            }
        }
    }

    fun markItemUsedUp(item: PantryItemEntity) {
        viewModelScope.launch {
            repository.consumeItem(item)
            engagementManager.recordCoreOperationCompleted(
                operationKey = "ITEM_RESCUED_USED_UP",
                humanReadableTitle = str(R.string.op_title_used_up_fmt, item.name)
            )
            _snackbarMessage.value = str(R.string.vm_used_up_saved_fmt, item.name, item.estimatedValueDollars)
        }
    }

    fun restockItemToFridge(item: RestockItemEntity) {
        viewModelScope.launch {
            repository.restockItemToFridge(item)
            engagementManager.recordCoreOperationCompleted(
                operationKey = "ITEM_RESTOCKED_100",
                humanReadableTitle = str(R.string.op_title_restocked_single_fmt, item.name)
            )
            _snackbarMessage.value = str(R.string.vm_restocked_single_fmt, item.name, item.defaultExpiryDays)
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

    // --- Shelf-Life & Zero-Waste Preservation Coach (Cloud AI + Tier 3 Local Engine) ---

    fun runLocalPreservationFallback() {
        val currentItems = pantryItems.value
        if (currentItems.isEmpty()) {
            _preservationState.value = PreservationCoachUiState.Error(
                str(R.string.vm_preservation_empty_pantry)
            )
            return
        }
        val (summary, localGuides) = LocalCulinaryFallbackEngine.synthesizePreservationGuidesLocally(currentItems)
        _preservationState.value = PreservationCoachUiState.Success(
            summaryNote = summary,
            guides = localGuides,
            tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
        )
        _snackbarMessage.value = str(R.string.vm_preservation_local_fmt, localGuides.size)
    }

    fun retryPreservationWithEcoLite() {
        viewModelScope.launch {
            quotaManager.setEcoLiteMode(true)
            analyzeInventoryShelfLife(forceTier = AiCapabilityTier.TIER_2_ECO_LITE)
        }
    }

    fun analyzeInventoryShelfLife(forceTier: AiCapabilityTier? = null) {
        val currentItems = pantryItems.value
        if (currentItems.isEmpty()) {
            _preservationState.value = PreservationCoachUiState.Error(
                str(R.string.vm_preservation_empty_pantry)
            )
            return
        }

        viewModelScope.launch {
            val snap = quotaManager.getSnapshot()
            if (snap.forceLocalOfflineMode && forceTier == null) {
                runLocalPreservationFallback()
                return@launch
            }
            if (snap.isExhausted && forceTier == null && !hasByokKey.value) {
                _preservationState.value = PreservationCoachUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = str(R.string.vm_preservation_quota_reached)
                return@launch
            }

            val preferredTier = forceTier ?: snap.activeCapabilityTier
            _preservationState.value = PreservationCoachUiState.Analyzing
            try {
                val result = withTimeout(50_000L) {
                    aiService.analyzeShelfLifeAndPreservation(
                        items = currentItems,
                        preferredTier = preferredTier
                    )
                }
                val (summary, guides) = result.data
                if (guides.isNotEmpty()) {
                    if (!result.fromDedupCache && !result.usedByokKey) {
                        quotaManager.recordSuccessfulInteraction(
                            feature = AiFeatureType.SHELF_LIFE_PRESERVATION,
                            usedFallbackCascade = result.autoCascadedToLite
                        )
                    }
                    if (result.autoCascadedToLite) {
                        quotaManager.recordAutoCascade()
                    }
                    engagementManager.recordCoreOperationCompleted(
                        operationKey = "SHELF_LIFE_ANALYZED",
                        humanReadableTitle = str(R.string.op_title_shelf_life_analyzed_fmt, guides.size)
                    )
                    _preservationState.value = PreservationCoachUiState.Success(
                        summaryNote = summary,
                        guides = guides,
                        tierUsed = result.tierUsed
                    )
                    val tierSuffix = when {
                        result.fromDedupCache -> str(R.string.vm_tier_suffix_dedup)
                        result.tierUsed == AiCapabilityTier.TIER_2_ECO_LITE -> str(R.string.vm_tier_suffix_eco)
                        else -> ""
                    }
                    _snackbarMessage.value = str(R.string.vm_preservation_generated_fmt, guides.size, tierSuffix)
                } else {
                    runLocalPreservationFallback()
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Preservation analysis timed out, falling back to local coach", e)
                runLocalPreservationFallback()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Preservation analysis failed", e)
                if (PantryAiService.isQuotaOrRateLimitException(e)) {
                    _preservationState.value = PreservationCoachUiState.QuotaRecoveryNeeded
                } else {
                    runLocalPreservationFallback()
                }
            }
        }
    }

    fun applyPreservationGuideToInventory(guide: PreservationGuide) {
        viewModelScope.launch {
            val targetItem = pantryItems.value.firstOrNull { it.id == guide.itemId }
                ?: pantryItems.value.firstOrNull { it.name.equals(guide.ingredientName, ignoreCase = true) }

            if (targetItem != null) {
                val updatedItem = repository.applyPreservationGuide(targetItem, guide)
                engagementManager.recordCoreOperationCompleted(
                    operationKey = "PRESERVATION_METHOD_APPLIED",
                    humanReadableTitle = str(
                        R.string.op_title_preserved_item_fmt,
                        targetItem.name,
                        guide.methodType.label,
                        guide.extendedShelfLifeDays
                    )
                )
                val current = _preservationState.value
                if (current is PreservationCoachUiState.Success) {
                    val updatedGuides = current.guides.map { existing ->
                        if (existing.itemId == targetItem.id || existing.id == guide.id) {
                            existing.copy(
                                currentDaysLeft = updatedItem.daysUntilExpiry,
                                isApplied = true
                            )
                        } else {
                            existing
                        }
                    }
                    _preservationState.value = current.copy(guides = updatedGuides)
                }
                _snackbarMessage.value = str(
                    R.string.vm_preservation_applied_savings_fmt,
                    guide.methodType.badgeEmoji,
                    targetItem.name,
                    updatedItem.daysUntilExpiry,
                    guide.estimatedSavingsDollars
                )
            } else {
                _snackbarMessage.value = str(R.string.vm_preservation_applied_fmt, guide.ingredientName)
            }
        }
    }

    fun applyQuickPreservationForItem(item: PantryItemEntity) {
        val guide = LocalCulinaryFallbackEngine.buildPreservationGuideForItem(item)
        applyPreservationGuideToInventory(guide)
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
                str(R.string.vm_recipes_empty_pantry)
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
            engagementManager.recordCoreOperationCompleted(
                operationKey = "RECIPES_GENERATED_LOCAL",
                humanReadableTitle = str(R.string.op_title_recipes_synthesized_fmt, localRecipes.size)
            )
            _recipeGenState.value = RecipeGenerationUiState.Success(
                count = localRecipes.size,
                tierUsed = AiCapabilityTier.TIER_3_LOCAL_FALLBACK
            )
            _snackbarMessage.value = str(R.string.vm_recipes_local_done)
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
                str(R.string.vm_recipes_empty_pantry)
            )
            return
        }

        viewModelScope.launch {
            val snap = quotaManager.getSnapshot()
            if (snap.forceLocalOfflineMode && forceTier == null) {
                runLocalRecipeFallback(cravingOrNote)
                return@launch
            }
            if (snap.isExhausted && forceTier == null && !hasByokKey.value) {
                _recipeGenState.value = RecipeGenerationUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = str(R.string.vm_recipes_quota_reached)
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
                    if (!result.fromDedupCache && !result.usedByokKey) {
                        quotaManager.recordSuccessfulInteraction(
                            feature = AiFeatureType.RECIPE_RANKING,
                            usedFallbackCascade = result.autoCascadedToLite
                        )
                    }
                    if (result.autoCascadedToLite) {
                        quotaManager.recordAutoCascade()
                    }
                    engagementManager.recordCoreOperationCompleted(
                        operationKey = "RECIPES_GENERATED_CLOUD",
                        humanReadableTitle = str(R.string.op_title_recipes_ranked_fmt, recipes.size)
                    )
                    _recipeGenState.value = RecipeGenerationUiState.Success(
                        count = recipes.size,
                        tierUsed = result.tierUsed
                    )
                    val filterSummary = if (activeCats.isEmpty()) {
                        str(R.string.vm_recipes_all_categories)
                    } else {
                        activeCats.joinToString(", ") { str(it.displayNameRes) }
                    }
                    val tierSuffix = when {
                        result.fromDedupCache -> str(R.string.vm_tier_suffix_dedup)
                        result.tierUsed == AiCapabilityTier.TIER_2_ECO_LITE -> str(R.string.vm_tier_suffix_eco)
                        else -> ""
                    }
                    _snackbarMessage.value = str(R.string.vm_recipes_ranked_fmt, recipes.size, filterSummary, tierSuffix)
                } else {
                    _recipeGenState.value = RecipeGenerationUiState.Error(str(R.string.vm_recipes_none_returned))
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Recipe ranking timed out; falling back to local synthesis", e)
                runLocalRecipeFallback(cravingOrNote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recipe ranking failed", e)
                if (PantryAiService.isQuotaOrRateLimitException(e)) {
                    _recipeGenState.value = RecipeGenerationUiState.QuotaRecoveryNeeded
                } else {
                    // Graceful Tier 3 on-device fallback when offline or cloud backend unavailable
                    runLocalRecipeFallback(cravingOrNote)
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
            val newlyAutoAdded = repository.completeRescueMeal(recipe, pantryItems.value)
            _activeCookingRecipe.value = null
            engagementManager.recordCoreOperationCompleted(
                operationKey = "RESCUE_MEAL_COOKED",
                humanReadableTitle = str(R.string.op_title_meal_cooked_fmt, recipe.title)
            )
            val autoNote = if (newlyAutoAdded.isNotEmpty()) {
                str(R.string.vm_cooked_auto_added_fmt, newlyAutoAdded.joinToString())
            } else ""
            _snackbarMessage.value = str(R.string.vm_cooked_recipe_fmt, recipe.title, autoNote)
        }
    }

    // --- Sous-Chef Chat & Tier 3 Instant Local Reflex Engine ---

    fun clearChatHistory() {
        if (_chatUiState.value is ChatUiState.Streaming) return
        _chatMessages.value = listOf(initialWelcomeMessage)
        _chatUiState.value = ChatUiState.Idle
    }

    fun sendLocalReflexChatMessage(
        customPrompt: String = "",
        appendUserBubble: Boolean = customPrompt.isNotBlank()
    ) {
        val promptToUse = customPrompt.ifBlank { lastPendingChatPrompt }
            .ifBlank { str(R.string.vm_chat_default_tonight) }

        val reflexReply = LocalCulinaryFallbackEngine.generateLocalReflexReply(
            userMessage = promptToUse,
            items = pantryItems.value,
            kitchenSetup = kitchenSetup.value
        )

        _chatMessages.update { current ->
            val withUser = if (appendUserBubble) {
                current + ChatMessage(isUser = true, text = promptToUse)
            } else {
                current
            }
            withUser + ChatMessage(isUser = false, text = reflexReply, isStreaming = false)
        }
        _chatUiState.value = ChatUiState.Idle
        _snackbarMessage.value = str(R.string.vm_chat_local_replied)
    }

    fun retryChatWithEcoLite() {
        val prompt = lastPendingChatPrompt.ifBlank { str(R.string.vm_chat_default_produce) }
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

            val byokActive = hasByokKey.value
            if (snap.isExhausted && forceTier == null && !byokActive) {
                _chatUiState.value = ChatUiState.QuotaRecoveryNeeded
                _snackbarMessage.value = str(R.string.vm_chat_quota_reached)
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
                    if (!byokActive) {
                        quotaManager.recordSuccessfulInteraction(
                            feature = AiFeatureType.SOUS_CHEF_CHAT,
                            usedFallbackCascade = false
                        )
                    }
                    _chatMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == streamingId) msg.copy(text = finalText, isStreaming = false) else msg
                        }
                    }
                    _chatUiState.value = ChatUiState.Idle
                } else {
                    _chatMessages.update { list -> list.filterNot { it.id == streamingId } }
                    _chatUiState.value = ChatUiState.Error(str(R.string.vm_chat_empty_resp))
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Sous-Chef streaming timed out; falling back to local reflex", e)
                _chatMessages.update { list ->
                    list.filterNot { it.id == streamingId && it.text.isBlank() }
                }
                sendLocalReflexChatMessage(customPrompt = trimmed, appendUserBubble = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sous-Chef streaming failed", e)
                _chatMessages.update { list -> list.filterNot { it.id == streamingId && it.text.isBlank() } }
                if (PantryAiService.isQuotaOrRateLimitException(e)) {
                    _chatUiState.value = ChatUiState.QuotaRecoveryNeeded
                } else {
                    sendLocalReflexChatMessage(customPrompt = trimmed, appendUserBubble = false)
                }
            }
        }
    }

    fun requestPinOrRefreshWidget() {
        val context = getApplication<Application>()
        PantryPulseWidgetProvider.updateAllWidgets(context)
        val pinned = PantryPulseWidgetProvider.requestPinWidget(context)
        _snackbarMessage.value = if (pinned) {
            str(R.string.vm_widget_pin_requested)
        } else {
            str(R.string.vm_widget_refreshed)
        }
    }

    // --- App Engagement, Multi-Channel Donations, Rate, Share & Update ---

    fun completeGooglePlayDonation(uiContext: Context, tier: GooglePlayDonationTier) {
        engagementManager.completeGooglePlayDonation(uiContext, tier) { msg ->
            _snackbarMessage.value = msg
        }
    }

    fun launchPayPalDonation(uiContext: Context, amountDollars: Int) {
        engagementManager.launchPayPalDonation(uiContext, amountDollars) { msg ->
            _snackbarMessage.value = msg
        }
    }

    fun launchKofiDonation(uiContext: Context) {
        engagementManager.launchKofiDonation(uiContext) { msg ->
            _snackbarMessage.value = msg
        }
    }

    fun submitAppRating(uiContext: Context, stars: Int, feedbackNote: String) {
        engagementManager.submitAppRating(uiContext, stars, feedbackNote) { msg ->
            _snackbarMessage.value = msg
        }
    }

    fun shareAppWithFriends(uiContext: Context) {
        engagementManager.shareAppWithFriends(uiContext) { msg ->
            _snackbarMessage.value = msg
        }
    }

    fun applyAppUpdate(uiContext: Context) {
        engagementManager.applyAppUpdate(uiContext) { msg ->
            _snackbarMessage.value = msg
        }
    }

    fun triggerPromptPreview(type: SmartAutoPromptType) {
        engagementManager.triggerPromptPreview(type)
    }

    fun dismissActiveAutoPrompt() {
        engagementManager.dismissActiveAutoPrompt()
    }

    fun dismissHomeCompactBanner() {
        engagementManager.dismissHomeCompactBanner()
    }

    fun dismissPostOperationValueCard() {
        engagementManager.dismissPostOperationValueCard()
    }

    fun resetSupporterAndCooldownsForTesting() {
        engagementManager.resetSupporterAndCooldownsForTesting { msg ->
            _snackbarMessage.value = msg
        }
    }

    fun bindBillingHostActivity(uiContext: Context) {
        engagementManager.bindHostActivity(uiContext.findActivity())
    }

    override fun onCleared() {
        barcodeScanningUtility.close()
        engagementManager.closeBillingConnection()
        super.onCleared()
    }
}
