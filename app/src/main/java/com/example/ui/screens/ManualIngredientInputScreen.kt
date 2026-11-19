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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiFeatureType
import com.example.ai.UsageQuotaSnapshot
import com.example.data.DetectedIngredient
import com.example.data.PantryCategory
import com.example.data.computeExpiryEpochMillis
import com.example.data.formatExpiryDate
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
    @androidx.annotation.StringRes val nameRes: Int,
    val category: PantryCategory,
    @androidx.annotation.StringRes val defaultQuantityRes: Int,
    val defaultDaysUntilExpiry: Int,
    @androidx.annotation.StringRes val conditionNoteRes: Int,
    @androidx.annotation.StringRes val storageTipRes: Int,
    val estimatedValueDollars: Double
)

private val SPOKEN_DICTATION_PRESET_RES = listOf(
    R.string.manual_spoken_preset_1,
    R.string.manual_spoken_preset_2,
    R.string.manual_spoken_preset_3
)

private val QUICK_INGREDIENT_PRESETS = listOf(
    // Produce
    QuickIngredientPreset(
        nameRes = R.string.preset_spinach_name,
        category = PantryCategory.PRODUCE,
        defaultQuantityRes = R.string.preset_spinach_qty,
        defaultDaysUntilExpiry = 2,
        conditionNoteRes = R.string.preset_spinach_note,
        storageTipRes = R.string.preset_spinach_tip,
        estimatedValueDollars = 3.49
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_avocado_name,
        category = PantryCategory.PRODUCE,
        defaultQuantityRes = R.string.preset_avocado_qty,
        defaultDaysUntilExpiry = 2,
        conditionNoteRes = R.string.preset_avocado_note,
        storageTipRes = R.string.preset_avocado_tip,
        estimatedValueDollars = 2.99
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_cilantro_name,
        category = PantryCategory.PRODUCE,
        defaultQuantityRes = R.string.qty_1_bunch,
        defaultDaysUntilExpiry = 3,
        conditionNoteRes = R.string.preset_cilantro_note,
        storageTipRes = R.string.preset_cilantro_tip,
        estimatedValueDollars = 1.79
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_mushrooms_name,
        category = PantryCategory.PRODUCE,
        defaultQuantityRes = R.string.preset_mushrooms_qty,
        defaultDaysUntilExpiry = 3,
        conditionNoteRes = R.string.preset_mushrooms_note,
        storageTipRes = R.string.preset_mushrooms_tip,
        estimatedValueDollars = 3.99
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_peppers_name,
        category = PantryCategory.PRODUCE,
        defaultQuantityRes = R.string.preset_peppers_qty,
        defaultDaysUntilExpiry = 5,
        conditionNoteRes = R.string.preset_peppers_note,
        storageTipRes = R.string.preset_peppers_tip,
        estimatedValueDollars = 2.80
    ),
    // Dairy & Cheese
    QuickIngredientPreset(
        nameRes = R.string.preset_feta_name,
        category = PantryCategory.DAIRY_CHEESE,
        defaultQuantityRes = R.string.preset_feta_qty,
        defaultDaysUntilExpiry = 4,
        conditionNoteRes = R.string.preset_feta_note,
        storageTipRes = R.string.preset_feta_tip,
        estimatedValueDollars = 4.79
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_yogurt_name,
        category = PantryCategory.DAIRY_CHEESE,
        defaultQuantityRes = R.string.preset_yogurt_qty,
        defaultDaysUntilExpiry = 4,
        conditionNoteRes = R.string.preset_yogurt_note,
        storageTipRes = R.string.preset_yogurt_tip,
        estimatedValueDollars = 5.49
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_cheddar_name,
        category = PantryCategory.DAIRY_CHEESE,
        defaultQuantityRes = R.string.preset_cheddar_qty,
        defaultDaysUntilExpiry = 9,
        conditionNoteRes = R.string.preset_cheddar_note,
        storageTipRes = R.string.preset_cheddar_tip,
        estimatedValueDollars = 4.50
    ),
    // Sauces & Jars
    QuickIngredientPreset(
        nameRes = R.string.preset_marinara_name,
        category = PantryCategory.SAUCES_JARS,
        defaultQuantityRes = R.string.preset_marinara_qty,
        defaultDaysUntilExpiry = 3,
        conditionNoteRes = R.string.preset_marinara_note,
        storageTipRes = R.string.preset_marinara_tip,
        estimatedValueDollars = 3.99
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_pesto_name,
        category = PantryCategory.SAUCES_JARS,
        defaultQuantityRes = R.string.preset_pesto_qty,
        defaultDaysUntilExpiry = 3,
        conditionNoteRes = R.string.preset_pesto_note,
        storageTipRes = R.string.preset_pesto_tip,
        estimatedValueDollars = 4.99
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_chipotle_name,
        category = PantryCategory.SAUCES_JARS,
        defaultQuantityRes = R.string.preset_chipotle_qty,
        defaultDaysUntilExpiry = 5,
        conditionNoteRes = R.string.preset_chipotle_note,
        storageTipRes = R.string.preset_chipotle_tip,
        estimatedValueDollars = 2.49
    ),
    // Proteins
    QuickIngredientPreset(
        nameRes = R.string.preset_chicken_name,
        category = PantryCategory.PROTEIN,
        defaultQuantityRes = R.string.qty_2_cups,
        defaultDaysUntilExpiry = 2,
        conditionNoteRes = R.string.preset_chicken_note,
        storageTipRes = R.string.preset_chicken_tip,
        estimatedValueDollars = 6.99
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_tofu_name,
        category = PantryCategory.PROTEIN,
        defaultQuantityRes = R.string.preset_tofu_qty,
        defaultDaysUntilExpiry = 3,
        conditionNoteRes = R.string.preset_tofu_note,
        storageTipRes = R.string.preset_tofu_tip,
        estimatedValueDollars = 2.79
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_eggs_name,
        category = PantryCategory.PROTEIN,
        defaultQuantityRes = R.string.preset_eggs_qty,
        defaultDaysUntilExpiry = 12,
        conditionNoteRes = R.string.preset_eggs_note,
        storageTipRes = R.string.preset_eggs_tip,
        estimatedValueDollars = 4.29
    ),
    // Leftovers
    QuickIngredientPreset(
        nameRes = R.string.preset_rice_name,
        category = PantryCategory.LEFTOVERS,
        defaultQuantityRes = R.string.qty_2_cups,
        defaultDaysUntilExpiry = 2,
        conditionNoteRes = R.string.preset_rice_note,
        storageTipRes = R.string.preset_rice_tip,
        estimatedValueDollars = 2.25
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_roasted_veg_name,
        category = PantryCategory.LEFTOVERS,
        defaultQuantityRes = R.string.qty_1_container,
        defaultDaysUntilExpiry = 2,
        conditionNoteRes = R.string.preset_roasted_veg_note,
        storageTipRes = R.string.preset_roasted_veg_tip,
        estimatedValueDollars = 4.00
    ),
    // Pantry Staples
    QuickIngredientPreset(
        nameRes = R.string.preset_sourdough_name,
        category = PantryCategory.PANTRY_STAPLE,
        defaultQuantityRes = R.string.preset_sourdough_qty,
        defaultDaysUntilExpiry = 3,
        conditionNoteRes = R.string.preset_sourdough_note,
        storageTipRes = R.string.preset_sourdough_tip,
        estimatedValueDollars = 3.50
    ),
    QuickIngredientPreset(
        nameRes = R.string.preset_chickpeas_name,
        category = PantryCategory.PANTRY_STAPLE,
        defaultQuantityRes = R.string.preset_chickpeas_qty,
        defaultDaysUntilExpiry = 3,
        conditionNoteRes = R.string.preset_chickpeas_note,
        storageTipRes = R.string.preset_chickpeas_tip,
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

    val context = LocalContext.current
    val defaultManualNote = stringResource(id = R.string.manual_default_note)
    val defaultQtyPack = stringResource(id = R.string.qty_1_pack)
    val defaultQtyUnit = stringResource(id = R.string.qty_1_unit)
    var selectedPresetCategoryFilter by rememberSaveable { mutableStateOf<PantryCategory?>(null) }
    var voiceTranscriptText by rememberSaveable { mutableStateOf("") }

    // Custom Form State
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(PantryCategory.PRODUCE) }
    var quantity by rememberSaveable { mutableStateOf(defaultQtyPack) }
    var daysSlider by rememberSaveable { mutableFloatStateOf(3f) }
    var conditionNote by rememberSaveable { mutableStateOf("") }
    var storageTip by rememberSaveable {
        mutableStateOf(defaultStorageTipForCategory(context, PantryCategory.PRODUCE))
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
                            contentDescription = stringResource(id = R.string.cd_back_to_pantry)
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
                                context.getString(R.string.manual_voice_prompt)
                            )
                        }
                        speechRecognizerLauncher.launch(intent)
                    } catch (_: Exception) {
                        // Graceful fallback if the streaming emulator does not have a voice activity installed
                        val sample = context.getString(SPOKEN_DICTATION_PRESET_RES.first())
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
                                label = { Text("${cat.emoji} ${stringResource(id = cat.displayNameRes)}") }
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
                            val presetName = stringResource(id = preset.nameRes)
                            val presetQty = stringResource(id = preset.defaultQuantityRes)
                            val presetNote = stringResource(id = preset.conditionNoteRes)
                            val presetTip = stringResource(id = preset.storageTipRes)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .clickable {
                                        // Populate custom builder form
                                        name = presetName
                                        category = preset.category
                                        quantity = presetQty
                                        daysSlider = preset.defaultDaysUntilExpiry.toFloat()
                                        conditionNote = presetNote
                                        storageTip = presetTip
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
                                            text = "${preset.category.emoji} $presetName",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "$presetQty • ${stringResource(id = R.string.expiry_short_days_fmt, preset.defaultDaysUntilExpiry)} • $${String.format("%.2f", preset.estimatedValueDollars)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = {
                                            stagedBatch.add(
                                                DetectedIngredient(
                                                    name = presetName,
                                                    category = preset.category,
                                                    quantity = presetQty,
                                                    daysUntilExpiry = preset.defaultDaysUntilExpiry,
                                                    visualConditionNote = presetNote,
                                                    storageTip = presetTip,
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
                                            contentDescription = stringResource(id = R.string.cd_add_to_batch_fmt, presetName),
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
                                    storageTip = defaultStorageTipForCategory(context, cat)
                                },
                                label = { Text("${cat.emoji} ${stringResource(id = cat.displayNameRes)}") },
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
                        listOf(
                            R.string.qty_1_bunch,
                            R.string.qty_1_pack,
                            R.string.qty_half_jar,
                            R.string.qty_8_oz,
                            R.string.qty_2_cups,
                            R.string.qty_3_pieces,
                            R.string.qty_1_container
                        ).forEach { unitRes ->
                            val unitPreset = stringResource(id = unitRes)
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
                        daysInt <= 1 -> Triple(CriticalCrimsonContainer, CriticalCrimson, stringResource(id = R.string.manual_urgency_critical))
                        daysInt <= 3 -> Triple(ExpiringAmberContainer, ExpiringAmber, stringResource(id = R.string.manual_urgency_soon))
                        else -> Triple(FreshEmeraldContainer, FreshEmerald, stringResource(id = R.string.manual_urgency_fresh))
                    }

                    val computedDateText = remember(daysInt) {
                        formatExpiryDate(computeExpiryEpochMillis(daysInt))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(id = R.string.custom_spoilage_label),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = computedDateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = urgencyBg,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = stringResource(id = R.string.manual_days_urgency_fmt, daysInt, urgencyLabel),
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
                            1 to R.string.manual_expiry_1d,
                            2 to R.string.manual_expiry_2d,
                            3 to R.string.manual_expiry_3d,
                            5 to R.string.manual_expiry_5d,
                            7 to R.string.manual_expiry_7d,
                            14 to R.string.manual_expiry_14d
                        ).forEach { (d, labelRes) ->
                            FilterChip(
                                selected = daysInt == d,
                                onClick = { daysSlider = d.toFloat() },
                                label = { Text(stringResource(id = labelRes), style = MaterialTheme.typography.labelSmall) },
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
                                            quantity = quantity.ifBlank { defaultQtyUnit }.trim(),
                                            daysUntilExpiry = daysInt,
                                            visualConditionNote = conditionNote.ifBlank { defaultManualNote }.trim(),
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
                                        quantity.ifBlank { defaultQtyUnit }.trim(),
                                        daysInt,
                                        conditionNote.ifBlank { defaultManualNote }.trim(),
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
                            text = stringResource(id = R.string.manual_est_value_fmt, totalValue),
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
                                Icon(Icons.Default.Remove, contentDescription = stringResource(id = R.string.cd_decrease_days), modifier = Modifier.size(15.dp))
                            }
                            Text(
                                text = stringResource(id = R.string.expiry_short_days_fmt, item.daysUntilExpiry),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                            IconButton(
                                onClick = { onAdjustStagedDays(idx, 1) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.cd_increase_days), modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = { onRemoveStagedItem(idx) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.cd_remove_item_short), modifier = Modifier.size(16.dp))
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

private fun defaultStorageTipForCategory(
    context: android.content.Context,
    category: PantryCategory
): String {
    val resId = when (category) {
        PantryCategory.PRODUCE -> R.string.manual_default_tip_produce
        PantryCategory.DAIRY_CHEESE -> R.string.manual_default_tip_dairy
        PantryCategory.SAUCES_JARS -> R.string.manual_default_tip_sauces
        PantryCategory.PROTEIN -> R.string.manual_default_tip_protein
        PantryCategory.LEFTOVERS -> R.string.manual_default_tip_leftovers
        PantryCategory.PANTRY_STAPLE -> R.string.manual_default_tip_dry_pantry
    }
    return context.getString(resId)
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
                SPOKEN_DICTATION_PRESET_RES.forEachIndexed { idx, presetRes ->
                    val presetPhrase = stringResource(id = presetRes)
                    FilterChip(
                        selected = transcript == presetPhrase,
                        onClick = { onTranscriptChange(presetPhrase) },
                        label = {
                            Text(
                                text = stringResource(
                                    id = R.string.manual_voice_chip_fmt,
                                    idx + 1,
                                    presetPhrase.take(36)
                                ),
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
                        Text(
                            stringResource(
                                id = R.string.manual_parsing_progress_fmt,
                                stringResource(id = quotaState.activeCapabilityTier.badgeLabelRes)
                            )
                        )
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
