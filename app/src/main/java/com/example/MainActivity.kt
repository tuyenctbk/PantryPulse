package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.PantryPulseViewModel
import com.example.ui.PantryTab
import com.example.ui.components.AiValueReceiptDialog
import com.example.ui.components.BrandedSplashOverlay
import com.example.ui.components.PersistentQuotaStatusBanner
import com.example.ui.components.SmartAutoSuggestionDialog
import com.example.ui.components.SupportAndEngagementHubDialog
import com.example.ui.components.SupporterHeaderActionChip
import com.example.ui.components.rememberIsAndroidTv
import com.example.ui.components.tvFocusHighlight
import com.example.ui.engagement.SmartAutoPromptType
import com.example.ui.screens.FridgeScannerScreen
import com.example.ui.screens.ManualIngredientInputScreen
import com.example.ui.screens.PantryDashboardScreen
import com.example.ui.screens.RescueRecipesScreen
import com.example.ui.screens.SmartShoppingListScreen
import com.example.ui.screens.SousChefChatScreen
import com.example.ui.theme.PantryPulseTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // CRITICAL: Configure Firebase App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            PantryPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PantryPulseApp()
                }
            }
        }
    }
}

private data class NavDestination(
    val tab: PantryTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryPulseApp(
    viewModel: PantryPulseViewModel = viewModel()
) {
    val uiContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val quotaState by viewModel.quotaState.collectAsStateWithLifecycle()
    val hasByokKey by viewModel.hasByokKey.collectAsStateWithLifecycle()
    val byokKeyPreview by viewModel.byokKeyPreview.collectAsStateWithLifecycle()
    val isLookingUpBarcode by viewModel.isLookingUpBarcode.collectAsStateWithLifecycle()
    val engagementState by viewModel.engagementState.collectAsStateWithLifecycle()
    val pantryItems by viewModel.pantryItems.collectAsStateWithLifecycle()
    val restockItems by viewModel.restockItems.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val recipeSearchQuery by viewModel.recipeSearchQuery.collectAsStateWithLifecycle()
    val selectedRecipeCategories by viewModel.selectedRecipeCategories.collectAsStateWithLifecycle()
    val selectedDietaryPreferences by viewModel.selectedDietaryPreferences.collectAsStateWithLifecycle()
    val selectedIngredientFilters by viewModel.selectedIngredientFilters.collectAsStateWithLifecycle()
    val scanCaptureMode by viewModel.scanCaptureMode.collectAsStateWithLifecycle()
    val kitchenSetup by viewModel.kitchenSetup.collectAsStateWithLifecycle()
    val capturedBitmaps by viewModel.capturedBitmaps.collectAsStateWithLifecycle()
    val visionScanState by viewModel.visionScanState.collectAsStateWithLifecycle()
    val savedRecipes by viewModel.savedRecipes.collectAsStateWithLifecycle()
    val recipeGenState by viewModel.recipeGenState.collectAsStateWithLifecycle()
    val activeCookingRecipe by viewModel.activeCookingRecipe.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatUiState by viewModel.chatUiState.collectAsStateWithLifecycle()
    val voiceParseState by viewModel.voiceParseState.collectAsStateWithLifecycle()
    val preservationState by viewModel.preservationState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSplashOverlay by rememberSaveable { mutableStateOf(true) }
    var showValueReceiptDialog by rememberSaveable { mutableStateOf(false) }
    var showSupportHubDialog by rememberSaveable { mutableStateOf(false) }

    // Keep offline expiry countdowns synced whenever the user returns to the app across days
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshOfflineExpiryCountdowns()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = !showSplashOverlay && activeCookingRecipe == null && selectedTab != PantryTab.PANTRY) {
        viewModel.selectTab(PantryTab.PANTRY)
    }

    LaunchedEffect(uiContext) {
        viewModel.bindBillingHostActivity(uiContext)
    }

    LaunchedEffect(Unit) {
        delay(1050L)
        showSplashOverlay = false
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val expiringCount = remember(pantryItems) {
        pantryItems.count { it.effectiveDaysUntilExpiry <= 3 }
    }

    val destinations = remember {
        listOf(
            NavDestination(
                tab = PantryTab.PANTRY,
                labelRes = R.string.nav_pantry,
                selectedIcon = Icons.Filled.Kitchen,
                unselectedIcon = Icons.Outlined.Kitchen,
                testTag = "nav_tab_pantry"
            ),
            NavDestination(
                tab = PantryTab.SCAN,
                labelRes = R.string.nav_scan,
                selectedIcon = Icons.Filled.CameraAlt,
                unselectedIcon = Icons.Outlined.CameraAlt,
                testTag = "nav_tab_scan"
            ),
            NavDestination(
                tab = PantryTab.RECIPES,
                labelRes = R.string.nav_recipes,
                selectedIcon = Icons.Filled.RestaurantMenu,
                unselectedIcon = Icons.Outlined.RestaurantMenu,
                testTag = "nav_tab_recipes"
            ),
            NavDestination(
                tab = PantryTab.SHOPPING_LIST,
                labelRes = R.string.nav_shopping_list,
                selectedIcon = Icons.Filled.ShoppingCart,
                unselectedIcon = Icons.Outlined.ShoppingCart,
                testTag = "nav_tab_shopping_list"
            ),
            NavDestination(
                tab = PantryTab.CHEF_CHAT,
                labelRes = R.string.nav_chef_chat,
                selectedIcon = Icons.Filled.ChatBubbleOutline,
                unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                testTag = "nav_tab_chef_chat"
            )
        )
    }

    if (showValueReceiptDialog) {
        AiValueReceiptDialog(
            quota = quotaState,
            rescuedMealsCount = kitchenSetup.rescuedMealsCount,
            savedDollarsTotal = kitchenSetup.savedDollarsTotal,
            hasByokKey = hasByokKey,
            byokKeyPreview = byokKeyPreview,
            onSaveByokKey = viewModel::saveByokApiKey,
            onClearByokKey = viewModel::clearByokApiKey,
            onPurchaseCreditPack = { credits -> viewModel.purchaseCreditPack(uiContext, credits) },
            onToggleEcoLite = viewModel::setEcoLiteMode,
            onToggleForceLocal = viewModel::setForceLocalMode,
            onClaimGraceCredits = viewModel::claimBonusGraceCredits,
            onSimulatePreset = viewModel::simulateQuotaPreset,
            onDismiss = { showValueReceiptDialog = false }
        )
    }

    if (showSupportHubDialog) {
        SupportAndEngagementHubDialog(
            engagement = engagementState,
            onGooglePlayDonate = { tier -> viewModel.completeGooglePlayDonation(uiContext, tier) },
            onPayPalDonate = { amount -> viewModel.launchPayPalDonation(uiContext, amount) },
            onKofiDonate = { viewModel.launchKofiDonation(uiContext) },
            onSubmitRating = { stars, note -> viewModel.submitAppRating(uiContext, stars, note) },
            onShareApp = { viewModel.shareAppWithFriends(uiContext) },
            onApplyUpdate = { viewModel.applyAppUpdate(uiContext) },
            onTriggerPreviewPrompt = { type ->
                showSupportHubDialog = false
                viewModel.triggerPromptPreview(type)
            },
            onResetCooldownsForTesting = viewModel::resetSupporterAndCooldownsForTesting,
            onDismiss = { showSupportHubDialog = false }
        )
    }

    if (engagementState.activeAutoPrompt != null) {
        SmartAutoSuggestionDialog(
            engagement = engagementState,
            onGooglePlayDonate = { tier -> viewModel.completeGooglePlayDonation(uiContext, tier) },
            onPayPalDonate = { amount -> viewModel.launchPayPalDonation(uiContext, amount) },
            onKofiDonate = { viewModel.launchKofiDonation(uiContext) },
            onSubmitRating = { stars, note -> viewModel.submitAppRating(uiContext, stars, note) },
            onShareApp = { viewModel.shareAppWithFriends(uiContext) },
            onApplyUpdate = { viewModel.applyAppUpdate(uiContext) },
            onDismiss = viewModel::dismissActiveAutoPrompt
        )
    }

    val isAndroidTv = rememberIsAndroidTv()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isAndroidTv) {
                    Modifier.padding(horizontal = 24.dp, vertical = 14.dp)
                } else {
                    Modifier
                }
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp || isAndroidTv
            val isCompactPhone = !isWideScreen

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            val emblemScale by animateFloatAsState(
                                targetValue = if (selectedTab == PantryTab.PANTRY) 1.04f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "top_emblem_scale"
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_pantrypulse_icon),
                                    contentDescription = stringResource(id = R.string.cd_pantrypulse_emblem),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .scale(emblemScale)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = if (isCompactPhone) {
                                        MaterialTheme.typography.titleLarge
                                    } else {
                                        MaterialTheme.typography.headlineSmall
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            SupporterHeaderActionChip(
                                engagement = engagementState,
                                onClick = { showSupportHubDialog = true },
                                compact = isCompactPhone,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .tvFocusHighlight(shape = RoundedCornerShape(50))
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 2.dp
                        ) {
                            destinations.forEach { dest ->
                                val selected = selectedTab == dest.tab ||
                                    (selectedTab == PantryTab.MANUAL_ADD && dest.tab == PantryTab.PANTRY)
                                val badgeCount = when (dest.tab) {
                                    PantryTab.PANTRY -> expiringCount
                                    PantryTab.SHOPPING_LIST -> restockItems.size
                                    else -> 0
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(dest.tab) },
                                    icon = {
                                        NavDestinationBadgeIcon(
                                            dest = dest,
                                            selected = selected,
                                            badgeCount = badgeCount
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(id = dest.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideScreen) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            destinations.forEach { dest ->
                                val selected = selectedTab == dest.tab ||
                                    (selectedTab == PantryTab.MANUAL_ADD && dest.tab == PantryTab.PANTRY)
                                val badgeCount = when (dest.tab) {
                                    PantryTab.PANTRY -> expiringCount
                                    PantryTab.SHOPPING_LIST -> restockItems.size
                                    else -> 0
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(dest.tab) },
                                    icon = {
                                        NavDestinationBadgeIcon(
                                            dest = dest,
                                            selected = selected,
                                            badgeCount = badgeCount
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(id = dest.labelRes),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier
                                        .tvFocusHighlight(shape = RoundedCornerShape(14.dp))
                                        .testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val contentMaxWidth = if (isAndroidTv) 1280.dp else 960.dp
                        PersistentQuotaStatusBanner(
                            quota = quotaState,
                            onToggleEcoLite = viewModel::setEcoLiteMode,
                            onOpenValueReceipt = { showValueReceiptDialog = true },
                            hasByokKey = hasByokKey,
                            modifier = Modifier.widthIn(max = contentMaxWidth)
                        )

                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { width ->
                                        (width * 0.06f * direction).toInt()
                                    }) togetherWith
                                    (fadeOut(tween(170, easing = FastOutSlowInEasing)) +
                                        slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { width ->
                                            (-width * 0.04f * direction).toInt()
                                        })
                            },
                            label = "pantry_tab_transition",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .widthIn(max = contentMaxWidth)
                        ) { activeTab ->
                            when (activeTab) {
                                PantryTab.PANTRY -> PantryDashboardScreen(
                                    items = pantryItems,
                                    restockItems = restockItems,
                                    selectedCategory = selectedCategory,
                                    searchQuery = searchQuery,
                                    sortOrder = sortOrder,
                                    kitchenSetup = kitchenSetup,
                                    engagementState = engagementState,
                                    preservationState = preservationState,
                                    onSelectCategory = viewModel::setCategoryFilter,
                                    onSearchQueryChange = viewModel::setSearchQuery,
                                    onSortOrderChange = viewModel::setSortOrder,
                                    onNavigateToScan = { viewModel.selectTab(PantryTab.SCAN) },
                                    onNavigateToManualInput = { viewModel.selectTab(PantryTab.MANUAL_ADD) },
                                    onNavigateToRecipes = { viewModel.selectTab(PantryTab.RECIPES) },
                                    onNavigateToShoppingList = { viewModel.selectTab(PantryTab.SHOPPING_LIST) },
                                    onAnalyzeShelfLife = { viewModel.analyzeInventoryShelfLife() },
                                    onRunLocalPreservation = viewModel::runLocalPreservationFallback,
                                    onRetryPreservationEcoLite = viewModel::retryPreservationWithEcoLite,
                                    onClaimGraceCredits = viewModel::claimBonusGraceCredits,
                                    onApplyPreservationGuide = viewModel::applyPreservationGuideToInventory,
                                    onAddManualItem = { name, cat, qty, days, note ->
                                        viewModel.addManualIngredient(name, cat, qty, days, note)
                                    },
                                    onUpdateItem = viewModel::updateExistingIngredient,
                                    onAdjustDays = viewModel::adjustItemDays,
                                    onAdjustStockPercent = viewModel::adjustItemStock,
                                    onMarkUsedUp = viewModel::markItemUsedUp,
                                    onDeleteItem = viewModel::deleteItem,
                                    onRestockToFridge = viewModel::restockItemToFridge,
                                    onDismissRestock = viewModel::dismissRestockItem,
                                    onPinOrRefreshWidget = viewModel::requestPinOrRefreshWidget,
                                    onOpenSupportHub = { showSupportHubDialog = true },
                                    onOpenRatePrompt = { viewModel.triggerPromptPreview(SmartAutoPromptType.RATE_APP) },
                                    onShareApp = { viewModel.shareAppWithFriends(uiContext) },
                                    onDismissHomeBanner = viewModel::dismissHomeCompactBanner,
                                    onDismissPostOpCard = viewModel::dismissPostOperationValueCard
                                )
                                PantryTab.SCAN -> FridgeScannerScreen(
                                    capturedBitmaps = capturedBitmaps,
                                    captureMode = scanCaptureMode,
                                    scanState = visionScanState,
                                    quotaState = quotaState,
                                    isLookingUpBarcode = isLookingUpBarcode,
                                    onLookupBarcode = viewModel::lookupBarcodeTier0,
                                    onScanPhotoBarcode = viewModel::scanPhotoForBarcodeTier0,
                                    onSelectCaptureMode = viewModel::setScanCaptureMode,
                                    onAddPhoto = viewModel::addCapturedPhoto,
                                    onCaptureAndTriggerIngestion = viewModel::captureAndTriggerGeminiIngestion,
                                    onRemovePhoto = viewModel::removeCapturedPhoto,
                                    onClearPhotos = viewModel::clearCapturedPhotos,
                                    onAnalyzePhotos = { note -> viewModel.analyzeCapturedPhotos(note) },
                                    onEnableEcoLite = { viewModel.setEcoLiteMode(true) },
                                    onClaimGraceCredits = viewModel::claimBonusGraceCredits,
                                    onRetryEcoLite = viewModel::retryVisionWithEcoLite,
                                    onUseLocalFallback = viewModel::runLocalVisionFallback,
                                    onOpenValueReceipt = { showValueReceiptDialog = true },
                                    onToggleDetectedSelection = viewModel::toggleDetectedItemSelection,
                                    onAdjustDetectedDays = viewModel::adjustDetectedItemDays,
                                    onSaveSelectedToPantry = viewModel::saveSelectedDetectedItemsToPantry,
                                    onNavigateToManualInput = { viewModel.selectTab(PantryTab.MANUAL_ADD) },
                                    onNavigateBack = { viewModel.selectTab(PantryTab.PANTRY) }
                                )
                                PantryTab.MANUAL_ADD -> ManualIngredientInputScreen(
                                    existingInventoryCount = pantryItems.size,
                                    quotaState = quotaState,
                                    voiceParseState = voiceParseState,
                                    onParseVoiceTranscript = { transcript, onParsed ->
                                        viewModel.parseVoiceMemoToIngredients(
                                            transcript = transcript,
                                            onItemsParsed = onParsed
                                        )
                                    },
                                    onRetryVoiceEcoLite = { transcript, onParsed ->
                                        viewModel.retryVoiceParseWithEcoLite(
                                            transcript = transcript,
                                            onItemsParsed = onParsed
                                        )
                                    },
                                    onRunLocalVoiceFallback = { transcript, onParsed ->
                                        viewModel.runLocalVoiceParseFallback(
                                            transcript = transcript,
                                            onItemsParsed = onParsed
                                        )
                                    },
                                    onEnableEcoLite = { viewModel.setEcoLiteMode(true) },
                                    onClaimGraceCredits = viewModel::claimBonusGraceCredits,
                                    onOpenValueReceipt = { showValueReceiptDialog = true },
                                    onSaveSingleItem = { name, cat, qty, days, note, tip, value, returnToPantry ->
                                        viewModel.addManualIngredient(
                                            name = name,
                                            category = cat,
                                            quantity = qty,
                                            daysUntilExpiry = days,
                                            conditionNote = note,
                                            storageTip = tip,
                                            estimatedValueDollars = value,
                                            returnToPantry = returnToPantry
                                        )
                                    },
                                    onSaveBatchItems = viewModel::addManualBatchIngredients,
                                    onNavigateBack = { viewModel.selectTab(PantryTab.PANTRY) }
                                )
                                PantryTab.RECIPES -> RescueRecipesScreen(
                                    recipes = savedRecipes,
                                    pantryItems = pantryItems,
                                    kitchenSetup = kitchenSetup,
                                    recipeSearchQuery = recipeSearchQuery,
                                    selectedCategories = selectedRecipeCategories,
                                    selectedDietaryPreferences = selectedDietaryPreferences,
                                    selectedIngredientFilters = selectedIngredientFilters,
                                    recipeGenState = recipeGenState,
                                    quotaState = quotaState,
                                    activeCookingRecipe = activeCookingRecipe,
                                    onSearchQueryChange = viewModel::setRecipeSearchQuery,
                                    onToggleRecipeCategory = viewModel::toggleRecipeCategoryFilter,
                                    onClearRecipeCategories = viewModel::clearRecipeCategoryFilters,
                                    onToggleDietaryPreference = viewModel::toggleDietaryPreference,
                                    onToggleIngredientFilter = viewModel::toggleIngredientFilter,
                                    onClearAllFilters = viewModel::clearAllRecipeFilters,
                                    onToggleAppliance = viewModel::toggleAppliance,
                                    onToggleSpice = viewModel::toggleSpice,
                                    onGenerateRecipes = { note -> viewModel.generateRankedRecipes(note) },
                                    onEnableEcoLite = { viewModel.setEcoLiteMode(true) },
                                    onClaimGraceCredits = viewModel::claimBonusGraceCredits,
                                    onRetryEcoLite = viewModel::retryRecipesWithEcoLite,
                                    onUseLocalFallback = viewModel::runLocalRecipeFallback,
                                    onOpenValueReceipt = { showValueReceiptDialog = true },
                                    onOpenCookingMode = viewModel::openCookingMode,
                                    onCloseCookingMode = viewModel::closeCookingMode,
                                    onCompleteCookingMeal = viewModel::completeCookingRecipe,
                                    onSyncWeeklyPlanStaples = viewModel::syncWeeklyPlanStaplesToShoppingList,
                                    onAskSousChefVoiceQuestion = viewModel::askSousChefFromCookingVoice,
                                    onNavigateBack = { viewModel.selectTab(PantryTab.PANTRY) }
                                )
                                PantryTab.SHOPPING_LIST -> SmartShoppingListScreen(
                                    shoppingItems = restockItems,
                                    pantryItems = pantryItems,
                                    kitchenSetup = kitchenSetup,
                                    onToggleItemChecked = viewModel::toggleShoppingItemChecked,
                                    onRestockSingleItemToPantry = viewModel::restockItemToFridge,
                                    onRestockAllCheckedToPantry = viewModel::restockAllCheckedShoppingItems,
                                    onDeleteShoppingItem = viewModel::dismissRestockItem,
                                    onAddManualShoppingItem = viewModel::addManualShoppingItem,
                                    onMoveLowSupplyItemToShoppingList = viewModel::moveLowSupplyItemToShoppingList,
                                    onMoveAllLowSupplyToShoppingList = viewModel::moveAllLowSupplyToShoppingList,
                                    onUpdateLowStockThreshold = viewModel::updateLowStockThreshold,
                                    onRunLowStockScanNow = viewModel::runManualLowStockScan,
                                    onNavigateToPantry = { viewModel.selectTab(PantryTab.PANTRY) }
                                )
                                PantryTab.CHEF_CHAT -> SousChefChatScreen(
                                    messages = chatMessages,
                                    chatUiState = chatUiState,
                                    quotaState = quotaState,
                                    pantryItems = pantryItems,
                                    kitchenSetup = kitchenSetup,
                                    onSendMessage = { prompt -> viewModel.sendChefChatMessage(prompt) },
                                    onEnableEcoLite = { viewModel.setEcoLiteMode(true) },
                                    onClaimGraceCredits = viewModel::claimBonusGraceCredits,
                                    onRetryEcoLite = viewModel::retryChatWithEcoLite,
                                    onUseLocalFallback = { prompt -> viewModel.sendLocalReflexChatMessage(prompt) },
                                    onOpenValueReceipt = { showValueReceiptDialog = true },
                                    onClearChat = viewModel::clearChatHistory,
                                    onNavigateBack = { viewModel.selectTab(PantryTab.PANTRY) }
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSplashOverlay,
            exit = fadeOut(tween(340, easing = FastOutSlowInEasing)) +
                scaleOut(targetScale = 1.04f, animationSpec = tween(340, easing = FastOutSlowInEasing))
        ) {
            BrandedSplashOverlay(onDismiss = { showSplashOverlay = false })
        }
    }
}

@Composable
private fun NavDestinationBadgeIcon(
    dest: NavDestination,
    selected: Boolean,
    badgeCount: Int
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_icon_scale"
    )
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (badgeCount > 99) "99+" else "$badgeCount")
                }
            }
        ) {
            Icon(
                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                contentDescription = stringResource(id = dest.labelRes),
                modifier = Modifier.scale(iconScale)
            )
        }
    } else {
        Icon(
            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
            contentDescription = stringResource(id = dest.labelRes),
            modifier = Modifier.scale(iconScale)
        )
    }
}
