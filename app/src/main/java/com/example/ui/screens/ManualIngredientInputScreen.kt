package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiFeatureType
import com.example.ai.UsageQuotaSnapshot
import com.example.data.DetectedIngredient
import com.example.data.PantryCategory
import com.example.ui.VoiceParseUiState
import com.example.ui.components.QuotaSoftNudgeBanner
import com.example.ui.components.SmartQuotaRecoveryCard
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.CriticalCrimsonContainer
import com.example.ui.theme.ExpiringAmber
import com.example.ui.theme.ExpiringAmberContainer
import com.example.ui.theme.FreshEmerald
import com.example.ui.theme.FreshEmeraldContainer
import java.util.Locale
import kotlin.math.roundToInt

private data class QuickIngredientPreset(
    val name: String,
    val category: PantryCategory,
    val defaultQuantity: String,
    val defaultDaysUntilExpiry: Int,
    val conditionNote: String,
    val storageTip: String,
    val estimatedValueDollars: Double
)

private val SPOKEN_DICTATION_PRESETS = listOf(
    "Half bag of baby spinach expiring in 2 days, 2 ripe avocados for tomorrow, and half jar of basil pesto for 3 days",
    "2 cups shredded rotisserie chicken expiring tomorrow, 6 oz crumbled feta for 4 days, and half loaf sourdough",
    "8 oz cremini mushrooms expiring in 2 days, half tub Greek yogurt for 4 days, and 2 bell peppers for 5 days"
)

private val QUICK_INGREDIENT_PRESETS = listOf(
    // Produce
    QuickIngredientPreset(
        name = "Baby Spinach",
        category = PantryCategory.PRODUCE,
        defaultQuantity = "5 oz bag",
        defaultDaysUntilExpiry = 2,
        conditionNote = "Tender leafy greens — use soon in skillets or smoothies",
        storageTip = "Place a dry paper towel inside the bag in the crisper drawer.",
        estimatedValueDollars = 3.49
    ),
    QuickIngredientPreset(
        name = "Ripe Hass Avocados",
        category = PantryCategory.PRODUCE,
        defaultQuantity = "2 avocados",
        defaultDaysUntilExpiry = 2,
        conditionNote = "Yields to gentle pressure — ready to eat today or tomorrow",
        storageTip = "Keep in refrigerator once ripe to slow down browning.",
        estimatedValueDollars = 2.99
    ),
    QuickIngredientPreset(
        name = "Fresh Cilantro Bunch",
        category = PantryCategory.PRODUCE,
        defaultQuantity = "1 bunch",
        defaultDaysUntilExpiry = 3,
        conditionNote = "Delicate aromatic herb stems",
        storageTip = "Store upright in a jar with 1 inch of water, loosely covered.",
        estimatedValueDollars = 1.79
    ),
    QuickIngredientPreset(
        name = "Cremini Mushrooms",
        category = PantryCategory.PRODUCE,
        defaultQuantity = "8 oz pack",
        defaultDaysUntilExpiry = 3,
        conditionNote = "Whole mushrooms in carton",
        storageTip = "Transfer to a breathable paper bag on the middle shelf.",
        estimatedValueDollars = 3.99
    ),
    QuickIngredientPreset(
        name = "Bell Peppers",
        category = PantryCategory.PRODUCE,
        defaultQuantity = "2 peppers",
        defaultDaysUntilExpiry = 5,
        conditionNote = "Crisp sweet peppers",
        storageTip = "Store unwashed in the low-humidity crisper drawer.",
        estimatedValueDollars = 2.80
    ),
    // Dairy & Cheese
    QuickIngredientPreset(
        name = "Crumbled Feta Cheese",
        category = PantryCategory.DAIRY_CHEESE,
        defaultQuantity = "6 oz tub",
        defaultDaysUntilExpiry = 4,
        conditionNote = "Opened deli tub",
        storageTip = "Keep lid tightly sealed on the top or middle shelf.",
        estimatedValueDollars = 4.79
    ),
    QuickIngredientPreset(
        name = "Greek Yogurt Tub",
        category = PantryCategory.DAIRY_CHEESE,
        defaultQuantity = "1/2 tub (16 oz)",
        defaultDaysUntilExpiry = 4,
        conditionNote = "Opened plain Greek yogurt — great for marinades or sauces",
        storageTip = "Smooth surface and seal tightly; never store in fridge door.",
        estimatedValueDollars = 5.49
    ),
    QuickIngredientPreset(
        name = "Sharp Cheddar Block",
        category = PantryCategory.DAIRY_CHEESE,
        defaultQuantity = "6 oz block",
        defaultDaysUntilExpiry = 9,
        conditionNote = "Half-used cheese block",
        storageTip = "Wrap in parchment paper, then place in a loosely sealed bag.",
        estimatedValueDollars = 4.50
    ),
    // Sauces & Jars
    QuickIngredientPreset(
        name = "Opened Marinara Sauce",
        category = PantryCategory.SAUCES_JARS,
        defaultQuantity = "1/2 jar (12 oz)",
        defaultDaysUntilExpiry = 3,
        conditionNote = "Opened pasta sauce jar — priority rescue",
        storageTip = "Wipe jar rim clean and refrigerate on middle shelf; freeze if unused by day 4.",
        estimatedValueDollars = 3.99
    ),
    QuickIngredientPreset(
        name = "Basil Pesto Jar",
        category = PantryCategory.SAUCES_JARS,
        defaultQuantity = "1/3 jar",
        defaultDaysUntilExpiry = 3,
        conditionNote = "Opened basil pesto",
        storageTip = "Pour a thin film of olive oil on top to prevent oxidation.",
        estimatedValueDollars = 4.99
    ),
    QuickIngredientPreset(
        name = "Chipotle Peppers in Adobo",
        category = PantryCategory.SAUCES_JARS,
        defaultQuantity = "4 tbsp left",
        defaultDaysUntilExpiry = 5,
        conditionNote = "Transferred from opened can",
        storageTip = "Always move leftover canned items to a glass or plastic container.",
        estimatedValueDollars = 2.49
    ),
    // Proteins
    QuickIngredientPreset(
        name = "Shredded Rotisserie Chicken",
        category = PantryCategory.PROTEIN,
        defaultQuantity = "2 cups",
        defaultDaysUntilExpiry = 2,
        conditionNote = "Cooked poultry — use within 48 hours",
        storageTip = "Store in shallow airtight container on the coldest bottom shelf.",
        estimatedValueDollars = 6.99
    ),
    QuickIngredientPreset(
        name = "Extra-Firm Tofu",
        category = PantryCategory.PROTEIN,
        defaultQuantity = "1/2 block (7 oz)",
        defaultDaysUntilExpiry = 3,
        conditionNote = "Opened tofu block",
        storageTip = "Submerge in fresh cold water in a sealed container and change water daily.",
        estimatedValueDollars = 2.79
    ),
    QuickIngredientPreset(
        name = "Pasture-Raised Eggs",
        category = PantryCategory.PROTEIN,
        defaultQuantity = "6 eggs",
        defaultDaysUntilExpiry = 12,
        conditionNote = "Half carton remaining",
        storageTip = "Keep in original carton on an interior shelf, not the door.",
        estimatedValueDollars = 4.29
    ),
    // Leftovers
    QuickIngredientPreset(
        name = "Cooked Jasmine Rice",
        category = PantryCategory.LEFTOVERS,
        defaultQuantity = "2 cups",
        defaultDaysUntilExpiry = 2,
        conditionNote = "Chilled cooked rice — ideal for crispy skillet fried rice",
        storageTip = "Cool rapidly and keep sealed in fridge; use within 2–3 days.",
        estimatedValueDollars = 2.25
    ),
    QuickIngredientPreset(
        name = "Roasted Vegetables",
        category = PantryCategory.LEFTOVERS,
        defaultQuantity = "1 container",
        defaultDaysUntilExpiry = 2,
        conditionNote = "Leftover sheet-pan veggies",
        storageTip = "Crisp up in an Air Fryer or hot skillet for 4 minutes.",
        estimatedValueDollars = 4.00
    ),
    // Pantry Staples
    QuickIngredientPreset(
        name = "Artisan Sourdough Half-Loaf",
        category = PantryCategory.PANTRY_STAPLE,
        defaultQuantity = "1/2 loaf",
        defaultDaysUntilExpiry = 3,
        conditionNote = "Slightly firm crust — great for paninis, melts, or croutons",
        storageTip = "Keep cut-side down on a board or freeze slices for toast.",
        estimatedValueDollars = 3.50
    ),
    QuickIngredientPreset(
        name = "Chickpeas (Opened Can)",
        category = PantryCategory.PANTRY_STAPLE,
        defaultQuantity = "1 cup",
        defaultDaysUntilExpiry = 3,
        conditionNote = "Drained garbanzo beans",
        storageTip = "Store in a sealed glass jar in the fridge.",
        estimatedValueDollars = 1.69
    )
)

/**
 * Dedicated Manual & Voice Ingredient Input Screen for users who prefer not to use the camera.
 * Supports:
 * 0. Hands-Free Voice-to-Inventory AI Dictation (with Tier 1 -> Tier 2 -> Tier 3 0-Credit Local Transcript Parser).
 * 1. 1-Tap Quick-Add Presets across all 6 fridge/pantry categories.
 * 2. Custom Ingredient Builder with unit chips, spoilage countdown presets, value estimate, and smart storage tips.
 * 3. Multi-Item Batch Queue ("Manual Haul Basket") so users can add a full grocery haul in one sitting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualIngredientInputScreen(
    existingInventoryCount: Int,
    quotaState: UsageQuotaSnapshot,
    voiceParseState: VoiceParseUiState,
    onParseVoiceTranscript: (String, (List<DetectedIngredient>) -> Unit) -> Unit,
    onRetryVoiceEcoLite: (String, (List<DetectedIngredient>) -> Unit) -> Unit,
    onRunLocalVoiceFallback: (String, (List<DetectedIngredient>) -> Unit) -> Unit,
    onEnableEcoLite: () -> Unit,
    onClaimGraceCredits: () -> Unit,
    onOpenValueReceipt: () -> Unit,
    onSaveSingleItem: (
        name: String,
        category: PantryCategory,
        quantity: String,
        daysUntilExpiry: Int,
        conditionNote: String,
        storageTip: String,
        estimatedValueDollars: Double,
        returnToPantry: Boolean
    ) -> Unit,
    onSaveBatchItems: (List<DetectedIngredient>) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    var selectedPresetCategoryFilter by rememberSaveable { mutableStateOf<PantryCategory?>(null) }
    var voiceTranscriptText by rememberSaveable { mutableStateOf("") }

    // Custom Form State
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(PantryCategory.PRODUCE) }
    var quantity by rememberSaveable { mutableStateOf("1 pack") }
    var daysSlider by rememberSaveable { mutableFloatStateOf(3f) }
    var conditionNote by rememberSaveable { mutableStateOf("") }
    var storageTip by rememberSaveable {
        mutableStateOf(defaultStorageTipForCategory(PantryCategory.PRODUCE))
    }
    var estimatedValue by rememberSaveable { mutableDoubleStateOf(3.75) }

    // Multi-Item Batch Staging Queue
    val stagedBatch = remember { mutableStateListOf<DetectedIngredient>() }

    // Zero-permission Android system speech recognizer launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                voiceTranscriptText = spoken
                onParseVoiceTranscript(spoken) { parsedList ->
                    stagedBatch.addAll(parsedList)
                }
            }
        }
    }

    val filteredPresets = remember(selectedPresetCategoryFilter) {
        if (selectedPresetCategoryFilter == null) {
            QUICK_INGREDIENT_PRESETS
        } else {
            QUICK_INGREDIENT_PRESETS.filter { it.category == selectedPresetCategoryFilter }
        }
    }

    val daysInt = daysSlider.roundToInt().coerceIn(1, 30)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("manual_ingredient_input_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Header Bar with Back Navigation & Camera-Free Badge
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("manual_input_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Pantry"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.manual_screen_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(id = R.string.manual_screen_subtitle, existingInventoryCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Stage 1: 80% Soft Nudge Banner
        if (quotaState.isSoftNudgeThreshold && !quotaState.ecoLiteMode) {
            item {
                QuotaSoftNudgeBanner(
                    quota = quotaState,
                    onEnableEcoLite = onEnableEcoLite,
                    onOpenValueReceipt = onOpenValueReceipt
                )
            }
        }

        // 2. Staged Batch Basket Card (Visible at top whenever items are queued for batch save)
        if (stagedBatch.isNotEmpty()) {
            item {
                StagedManualBatchCard(
                    stagedItems = stagedBatch,
                    onAdjustStagedDays = { index, delta ->
                        val item = stagedBatch[index]
                        stagedBatch[index] = item.copy(
                            daysUntilExpiry = (item.daysUntilExpiry + delta).coerceIn(1, 45)
                        )
                    },
                    onRemoveStagedItem = { index ->
                        stagedBatch.removeAt(index)
                    },
                    onClearBatch = { stagedBatch.clear() },
                    onSaveAllBatch = {
                        onSaveBatchItems(stagedBatch.toList())
                        stagedBatch.clear()
                    }
                )
            }
        }

        // 2B. Hands-Free Voice-to-Inventory AI Dictation Card (with Tier 1 -> Tier 2 -> Tier 3 Fallback)
        item {
            VoiceToInventoryDictationCard(
                transcript = voiceTranscriptText,
                quotaState = quotaState,
                voiceParseState = voiceParseState,
                onTranscriptChange = { voiceTranscriptText = it },
                onLaunchSystemVoiceMic = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(
                                RecognizerIntent.EXTRA_PROMPT,
                                "Speak your groceries & expiry days (e.g., 'Half bag spinach for 2 days, 6 eggs')"
                            )
                        }
                        speechRecognizerLauncher.launch(intent)
                    } catch (_: Exception) {
                        // Graceful fallback if the streaming emulator does not have a voice activity installed
                        val sample = SPOKEN_DICTATION_PRESETS.first()
                        voiceTranscriptText = sample
                        onParseVoiceTranscript(sample) { parsed ->
                            stagedBatch.addAll(parsed)
                        }
                    }
                },
                onParseWithAi = {
                    onParseVoiceTranscript(voiceTranscriptText) { parsed ->
                        stagedBatch.addAll(parsed)
                    }
                },
                onParseLocallyZeroCredits = {
                    onRunLocalVoiceFallback(voiceTranscriptText) { parsed ->
                        stagedBatch.addAll(parsed)
                    }
                }
            )
        }

        // Stage 2: Smart Quota Recovery Card if Voice-to-Inventory hits 100% or 429
        if (voiceParseState is VoiceParseUiState.QuotaRecoveryNeeded) {
            item {
                SmartQuotaRecoveryCard(
                    feature = AiFeatureType.VOICE_MEMO_PARSE,
                    quota = quotaState,
                    onClaimGraceCredits = onClaimGraceCredits,
                    onRetryEcoLite = {
                        onRetryVoiceEcoLite(voiceTranscriptText) { parsed ->
                            stagedBatch.addAll(parsed)
                        }
                    },
                    onUseLocalFallback = {
                        onRunLocalVoiceFallback(voiceTranscriptText) { parsed ->
                            stagedBatch.addAll(parsed)
                        }
                    },
                    onOpenValueReceipt = onOpenValueReceipt
                )
            }
        }

        // 3. 1-Tap Quick-Add Common Fridge & Pantry Staples Card
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_presets_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(id = R.string.quick_presets_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(id = R.string.quick_presets_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Category Filter Row for Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedPresetCategoryFilter == null,
                            onClick = { selectedPresetCategoryFilter = null },
                            label = { Text(stringResource(id = R.string.filter_all_count, QUICK_INGREDIENT_PRESETS.size)) }
                        )
                        PantryCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedPresetCategoryFilter == cat,
                                onClick = {
                                    selectedPresetCategoryFilter = if (selectedPresetCategoryFilter == cat) null else cat
                                },
                                label = { Text("${cat.emoji} ${cat.displayName}") }
                            )
                        }
                    }

                    // Horizontal / Flow Quick-Add Preset Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredPresets.forEachIndexed { idx, preset ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .clickable {
                                        // Populate custom builder form
                                        name = preset.name
                                        category = preset.category
                                        quantity = preset.defaultQuantity
                                        daysSlider = preset.defaultDaysUntilExpiry.toFloat()
                                        conditionNote = preset.conditionNote
                                        storageTip = preset.storageTip
                                        estimatedValue = preset.estimatedValueDollars
                                    }
                                    .testTag("quick_preset_chip_$idx")
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${preset.category.emoji} ${preset.name}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${preset.defaultQuantity} • ${preset.defaultDaysUntilExpiry}d • $${String.format("%.2f", preset.estimatedValueDollars)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            stagedBatch.add(
                                                DetectedIngredient(
                                                    name = preset.name,
                                                    category = preset.category,
                                                    quantity = preset.defaultQuantity,
                                                    daysUntilExpiry = preset.defaultDaysUntilExpiry,
                                                    visualConditionNote = preset.conditionNote,
                                                    storageTip = preset.storageTip,
                                                    estimatedValueDollars = preset.estimatedValueDollars,
                                                    isSelected = true
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .testTag("quick_preset_stage_button_$idx")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircleOutline,
                                            contentDescription = "Add ${preset.name} to batch queue",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Custom Ingredient Builder Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_ingredient_builder_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.custom_builder_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Ingredient Name Input
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(id = R.string.custom_name_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_name_input")
                    )

                    // Category Selection
                    Text(
                        text = stringResource(id = R.string.custom_category_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PantryCategory.entries.forEach { cat ->
                            val selected = category == cat
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    category = cat
                                    storageTip = defaultStorageTipForCategory(cat)
                                },
                                label = { Text("${cat.emoji} ${cat.displayName}") },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag("manual_category_${cat.name.lowercase()}")
                            )
                        }
                    }

                    // Quantity Input + Quick Unit Chips
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text(stringResource(id = R.string.custom_quantity_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_quantity_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("1 bunch", "1 pack", "1/2 jar", "8 oz", "2 cups", "3 pieces", "1 container").forEach { unitPreset ->
                            FilterChip(
                                selected = quantity == unitPreset,
                                onClick = { quantity = unitPreset },
                                label = { Text(unitPreset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // Spoilage Countdown / Days Until Expiry Section
                    val (urgencyBg, urgencyFg, urgencyLabel) = when {
                        daysInt <= 1 -> Triple(CriticalCrimsonContainer, CriticalCrimson, "CRITICAL • USE TODAY")
                        daysInt <= 3 -> Triple(ExpiringAmberContainer, ExpiringAmber, "EXPIRING SOON • PRIORITY RESCUE")
                        else -> Triple(FreshEmeraldContainer, FreshEmerald, "FRESH")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.custom_spoilage_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Surface(
                            color = urgencyBg,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "$daysInt d • $urgencyLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = urgencyFg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Quick Expiry Presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            1 to "Use Today (1d)",
                            2 to "Expiring Soon (2d)",
                            3 to "3 Days",
                            5 to "5 Days",
                            7 to "1 Week (7d)",
                            14 to "2 Weeks (14d)"
                        ).forEach { (d, label) ->
                            FilterChip(
                                selected = daysInt == d,
                                onClick = { daysSlider = d.toFloat() },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("manual_expiry_preset_$d")
                            )
                        }
                    }

                    Slider(
                        value = daysSlider,
                        onValueChange = { daysSlider = it },
                        valueRange = 1f..30f,
                        steps = 28,
                        modifier = Modifier.testTag("manual_expiry_slider")
                    )

                    // Estimated Grocery Value Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.custom_value_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "$${String.format("%.2f", estimatedValue)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2.00, 3.50, 4.75, 6.50, 9.00).forEach { price ->
                            FilterChip(
                                selected = estimatedValue == price,
                                onClick = { estimatedValue = price },
                                label = { Text("$${String.format("%.2f", price)}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Condition / Shelf Note
                    OutlinedTextField(
                        value = conditionNote,
                        onValueChange = { conditionNote = it },
                        label = { Text(stringResource(id = R.string.custom_note_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_condition_note_input")
                    )

                    // Smart Storage Tip Box
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.freshness_tip_prefix, storageTip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Primary & Secondary Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (name.isNotBlank()) {
                                    stagedBatch.add(
                                        DetectedIngredient(
                                            name = name.trim(),
                                            category = category,
                                            quantity = quantity.ifBlank { "1 unit" }.trim(),
                                            daysUntilExpiry = daysInt,
                                            visualConditionNote = conditionNote.ifBlank { "Added via Manual Input" }.trim(),
                                            storageTip = storageTip,
                                            estimatedValueDollars = estimatedValue,
                                            isSelected = true
                                        )
                                    )
                                    // Reset name and note for quick entry of the next item
                                    name = ""
                                    conditionNote = ""
                                }
                            },
                            enabled = name.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("manual_stage_batch_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.btn_queue_in_batch))
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    onSaveSingleItem(
                                        name.trim(),
                                        category,
                                        quantity.ifBlank { "1 unit" }.trim(),
                                        daysInt,
                                        conditionNote.ifBlank { "Added via Manual Input" }.trim(),
                                        storageTip,
                                        estimatedValue,
                                        stagedBatch.isEmpty()
                                    )
                                    name = ""
                                    conditionNote = ""
                                }
                            },
                            enabled = name.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("manual_save_single_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(id = R.string.dialog_save_to_pantry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StagedManualBatchCard(
    stagedItems: List<DetectedIngredient>,
    onAdjustStagedDays: (Int, Int) -> Unit,
    onRemoveStagedItem: (Int) -> Unit,
    onClearBatch: () -> Unit,
    onSaveAllBatch: () -> Unit
) {
    val totalValue = stagedItems.sumOf { it.estimatedValueDollars }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staged_manual_batch_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAddCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.staged_basket_title, stagedItems.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Est. $${String.format("%.2f", totalValue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                TextButton(onClick = onClearBatch) {
                    Text(stringResource(id = R.string.recipes_reset_all))
                }
            }

            stagedItems.forEachIndexed { idx, item ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.category.emoji} ${item.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item.quantity} • $${String.format("%.2f", item.estimatedValueDollars)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onAdjustStagedDays(idx, -1) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease days", modifier = Modifier.size(15.dp))
                            }
                            Text(
                                text = "${item.daysUntilExpiry}d",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { onAdjustStagedDays(idx, 1) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase days", modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = { onRemoveStagedItem(idx) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove item", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onSaveAllBatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("manual_save_batch_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Kitchen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.staged_save_all_btn, stagedItems.size))
            }
        }
    }
}

private fun defaultStorageTipForCategory(category: PantryCategory): String {
    return when (category) {
        PantryCategory.PRODUCE -> "Wrap leafy greens in a dry paper towel inside the crisper drawer to prevent excess moisture."
        PantryCategory.DAIRY_CHEESE -> "Store on an interior top or middle shelf (not the warm door) in parchment or a sealed tub."
        PantryCategory.SAUCES_JARS -> "Wipe the jar rim clean before closing tightly; swirl with 2 tbsp water when nearly empty to deglaze pans."
        PantryCategory.PROTEIN -> "Keep on the coldest bottom shelf in a sealed container and cook or freeze promptly."
        PantryCategory.LEFTOVERS -> "Store in shallow airtight containers so air circulates; crisp up in a skillet or Air Fryer."
        PantryCategory.PANTRY_STAPLE -> "Keep in a cool, dry cupboard or sealed jar away from stove steam."
    }
}

@Composable
private fun VoiceToInventoryDictationCard(
    transcript: String,
    quotaState: UsageQuotaSnapshot,
    voiceParseState: VoiceParseUiState,
    onTranscriptChange: (String) -> Unit,
    onLaunchSystemVoiceMic: () -> Unit,
    onParseWithAi: () -> Unit,
    onParseLocallyZeroCredits: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_to_inventory_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.voice_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.voice_card_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onLaunchSystemVoiceMic,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("voice_dictation_mic_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(id = R.string.voice_btn_speak), style = MaterialTheme.typography.labelMedium)
                }
            }

            // 1-Tap Spoken Dictation Presets (for instant testing or emulator use)
            Text(
                text = stringResource(id = R.string.voice_sample_dictations_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SPOKEN_DICTATION_PRESETS.forEachIndexed { idx, presetPhrase ->
                    FilterChip(
                        selected = transcript == presetPhrase,
                        onClick = { onTranscriptChange(presetPhrase) },
                        label = {
                            Text(
                                text = "🎙️ #${idx + 1}: \"${presetPhrase.take(36)}...\"",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("voice_preset_chip_$idx")
                    )
                }
            }

            OutlinedTextField(
                value = transcript,
                onValueChange = onTranscriptChange,
                label = {
                    Text(stringResource(id = R.string.voice_transcript_label))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_transcript_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onParseWithAi,
                    enabled = transcript.isNotBlank() && voiceParseState !is VoiceParseUiState.Parsing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("voice_ai_parse_button")
                ) {
                    if (voiceParseState is VoiceParseUiState.Parsing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Parsing (${quotaState.activeCapabilityTier.badgeLabel})...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.voice_btn_ai_parse))
                    }
                }

                OutlinedButton(
                    onClick = onParseLocallyZeroCredits,
                    enabled = transcript.isNotBlank() && voiceParseState !is VoiceParseUiState.Parsing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("voice_local_parse_button")
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(id = R.string.voice_btn_local_parse))
                }
            }
        }
    }
}
