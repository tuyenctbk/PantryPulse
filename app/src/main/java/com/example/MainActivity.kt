package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.RestaurantMenu
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.PantryPulseViewModel
import com.example.ui.PantryTab
import com.example.ui.components.AiQuotaStatusPill
import com.example.ui.components.AiValueReceiptDialog
import com.example.ui.screens.FridgeScannerScreen
import com.example.ui.screens.ManualIngredientInputScreen
import com.example.ui.screens.PantryDashboardScreen
import com.example.ui.screens.RescueRecipesScreen
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
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val quotaState by viewModel.quotaState.collectAsStateWithLifecycle()
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
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSplashOverlay by rememberSaveable { mutableStateOf(true) }
    var showValueReceiptDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(650L)
        showSplashOverlay = false
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val expiringCount = remember(pantryItems) {
        pantryItems.count { it.daysUntilExpiry <= 3 }
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
            onToggleEcoLite = viewModel::setEcoLiteMode,
            onToggleForceLocal = viewModel::setForceLocalMode,
            onClaimGraceCredits = viewModel::claimBonusGraceCredits,
            onSimulatePreset = viewModel::simulateQuotaPreset,
            onDismiss = { showValueReceiptDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        },
                        actions = {
                            AiQuotaStatusPill(
                                quota = quotaState,
                                onClick = { showValueReceiptDialog = true },
                                modifier = Modifier.padding(end = 12.dp)
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
                        NavigationBar {
                            destinations.forEach { dest ->
                                val selected = selectedTab == dest.tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(dest.tab) },
                                    icon = {
                                        if (dest.tab == PantryTab.PANTRY && expiringCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge { Text("$expiringCount") }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = stringResource(id = dest.labelRes)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(id = dest.labelRes)
                                            )
                                        }
                                    },
                                    label = { Text(stringResource(id = dest.labelRes)) },
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
                        NavigationRail {
                            destinations.forEach { dest ->
                                val selected = selectedTab == dest.tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(dest.tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(id = dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(id = dest.labelRes)) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            PantryTab.PANTRY -> PantryDashboardScreen(
                                items = pantryItems,
                                restockItems = restockItems,
                                selectedCategory = selectedCategory,
                                searchQuery = searchQuery,
                                sortOrder = sortOrder,
                                kitchenSetup = kitchenSetup,
                                onSelectCategory = viewModel::setCategoryFilter,
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onSortOrderChange = viewModel::setSortOrder,
                                onNavigateToScan = { viewModel.selectTab(PantryTab.SCAN) },
                                onNavigateToManualInput = { viewModel.selectTab(PantryTab.MANUAL_ADD) },
                                onNavigateToRecipes = { viewModel.selectTab(PantryTab.RECIPES) },
                                onAddManualItem = { name, cat, qty, days, note ->
                                    viewModel.addManualIngredient(name, cat, qty, days, note)
                                },
                                onUpdateItem = viewModel::updateExistingIngredient,
                                onAdjustDays = viewModel::adjustItemDays,
                                onMarkUsedUp = viewModel::markItemUsedUp,
                                onDeleteItem = viewModel::deleteItem,
                                onRestockToFridge = viewModel::restockItemToFridge,
                                onDismissRestock = viewModel::dismissRestockItem,
                                onPinOrRefreshWidget = viewModel::requestPinOrRefreshWidget
                            )
                            PantryTab.SCAN -> FridgeScannerScreen(
                                capturedBitmaps = capturedBitmaps,
                                captureMode = scanCaptureMode,
                                scanState = visionScanState,
                                quotaState = quotaState,
                                onSelectCaptureMode = viewModel::setScanCaptureMode,
                                onAddPhoto = viewModel::addCapturedPhoto,
                                onCaptureAndTriggerIngestion = viewModel::captureAndTriggerGeminiIngestion,
                                onRemovePhoto = viewModel::removeCapturedPhoto,
                                onClearPhotos = viewModel::clearCapturedPhotos,
                                onAnalyzePhotos = { note -> viewModel.analyzeCapturedPhotos(note) },
                                onRunSampleScan = viewModel::loadSamplePhotoAndAnalyze,
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
                                onNavigateBack = { viewModel.selectTab(PantryTab.PANTRY) }
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
                                onUseLocalFallback = viewModel::sendLocalReflexChatMessage,
                                onOpenValueReceipt = { showValueReceiptDialog = true },
                                onClearChat = viewModel::clearChatHistory,
                                onNavigateBack = { viewModel.selectTab(PantryTab.PANTRY) }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSplashOverlay,
            exit = fadeOut()
        ) {
            BrandedSplashOverlay(onDismiss = { showSplashOverlay = false })
        }
    }
}

@Composable
private fun BrandedSplashOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B3B2B),
                        Color(0xFF10251A)
                    )
                )
            )
            .clickable { onDismiss() }
            .testTag("splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_launcher_icon_1794705065014),
                contentDescription = "PantryPulse Emblem",
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = Color(0xFFF4F1EA)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.splash_tagline),
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFA6F2CA)
            )
        }
    }
}
