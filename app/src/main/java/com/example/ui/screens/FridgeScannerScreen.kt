package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.R
import com.example.ai.AiFeatureType
import com.example.ai.UsageQuotaSnapshot
import com.example.data.DetectedIngredient
import com.example.data.ScanCaptureMode
import com.example.ui.VisionScanUiState
import com.example.ui.components.CameraBitmapProcessor
import com.example.ui.components.CameraXFridgeViewfinder
import com.example.ui.components.QuotaSoftNudgeBanner
import com.example.ui.components.SmartQuotaRecoveryCard
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.CriticalCrimsonContainer
import com.example.ui.theme.ExpiringAmber
import com.example.ui.theme.ExpiringAmberContainer
import com.example.ui.theme.FreshEmerald
import com.example.ui.theme.FreshEmeraldContainer

@Composable
fun FridgeScannerScreen(
    capturedBitmaps: List<Bitmap>,
    captureMode: ScanCaptureMode,
    scanState: VisionScanUiState,
    quotaState: UsageQuotaSnapshot,
    onSelectCaptureMode: (ScanCaptureMode) -> Unit,
    onAddPhoto: (Bitmap) -> Unit,
    onCaptureAndTriggerIngestion: (Bitmap, String) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onClearPhotos: () -> Unit,
    onAnalyzePhotos: (String) -> Unit,
    onEnableEcoLite: () -> Unit,
    onClaimGraceCredits: () -> Unit,
    onRetryEcoLite: (String) -> Unit,
    onUseLocalFallback: (String) -> Unit,
    onOpenValueReceipt: () -> Unit,
    onToggleDetectedSelection: (Int) -> Unit,
    onAdjustDetectedDays: (Int, Int) -> Unit,
    onSaveSelectedToPantry: () -> Unit,
    onNavigateToManualInput: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    var focusNote by rememberSaveable { mutableStateOf("") }

    // Zero-permission Google Play compliant Photo Picker for up to 2 fridge/receipt photos
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(2)
    ) { uris: List<Uri> ->
        var addedAny = false
        uris.take(2).forEach { uri ->
            CameraBitmapProcessor.decodeSampledBitmapFromUri(context, uri)?.let { bmp ->
                onAddPhoto(bmp)
                addedAny = true
            }
        }
        if (addedAny) {
            onAnalyzePhotos(focusNote)
        }
    }

    val isIngesting = scanState is VisionScanUiState.Analyzing

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("fridge_scanner_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.scanner_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = captureMode.subtitleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onNavigateToManualInput,
                    modifier = Modifier.testTag("switch_to_manual_input_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(id = R.string.scanner_manual_input_btn))
                }
            }
        }

        // Stage 1 (80% Soft Nudge Banner)
        if (quotaState.isSoftNudgeThreshold && !quotaState.ecoLiteMode) {
            item {
                QuotaSoftNudgeBanner(
                    quota = quotaState,
                    onEnableEcoLite = onEnableEcoLite,
                    onOpenValueReceipt = onOpenValueReceipt
                )
            }
        }

        // Mode switcher: Fridge / Pantry Shelf vs Grocery Receipt / Label
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScanCaptureMode.entries.forEach { mode ->
                    val selected = captureMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCaptureMode(mode) },
                        label = {
                            Text(
                                text = stringResource(id = mode.titleRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (mode == ScanCaptureMode.FRIDGE_SHELF) {
                                    Icons.Default.Kitchen
                                } else {
                                    Icons.Default.ReceiptLong
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_mode_${mode.name.lowercase()}")
                    )
                }
            }
        }

        // Dedicated CameraX Viewfinder Component (or Permission Gate with Fallback Dock)
        item {
            if (hasCameraPermission) {
                CameraXFridgeViewfinder(
                    capturedBitmaps = capturedBitmaps,
                    captureMode = captureMode,
                    activeTier = quotaState.activeCapabilityTier,
                    isIngesting = isIngesting,
                    focusNote = focusNote,
                    onFocusNoteChange = { focusNote = it },
                    onPhotoCapturedOnly = onAddPhoto,
                    onCaptureAndTriggerIngestion = onCaptureAndTriggerIngestion,
                    onTriggerIngestionForQueued = onAnalyzePhotos,
                    onRemovePhoto = onRemovePhoto,
                    onClearPhotos = onClearPhotos,
                    onOpenPhotoPicker = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                CameraPermissionGateWithIngestionDock(
                    capturedBitmaps = capturedBitmaps,
                    focusNote = focusNote,
                    isAnalyzing = isIngesting,
                    onFocusNoteChange = { focusNote = it },
                    onRequestCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onPickFromGallery = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemovePhoto = onRemovePhoto,
                    onAnalyze = { onAnalyzePhotos(focusNote) }
                )
            }
        }

        // Stage 2: Smart Quota Recovery Card when 100% or 429 hit
        if (scanState is VisionScanUiState.QuotaRecoveryNeeded || (quotaState.isExhausted && scanState !is VisionScanUiState.Success)) {
            item {
                SmartQuotaRecoveryCard(
                    feature = AiFeatureType.VISION_SCAN,
                    quota = quotaState,
                    onClaimGraceCredits = onClaimGraceCredits,
                    onRetryEcoLite = { onRetryEcoLite(focusNote) },
                    onUseLocalFallback = { onUseLocalFallback(focusNote) },
                    onOpenValueReceipt = onOpenValueReceipt
                )
            }
        }

        when (scanState) {
            is VisionScanUiState.Idle,
            is VisionScanUiState.QuotaRecoveryNeeded -> Unit
            is VisionScanUiState.Analyzing -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (captureMode == ScanCaptureMode.FRIDGE_SHELF) {
                                        stringResource(id = R.string.scanner_inspecting_shelves, stringResource(id = quotaState.activeCapabilityTier.badgeLabelRes))
                                    } else {
                                        stringResource(id = R.string.scanner_extracting_receipt, stringResource(id = quotaState.activeCapabilityTier.badgeLabelRes))
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(id = R.string.scanner_hygiene_subtitle),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            is VisionScanUiState.Error -> {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vision_error_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(id = R.string.scanner_error_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = scanState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onUseLocalFallback(focusNote) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(id = R.string.scanner_local_fallback_btn))
                            }
                        }
                    }
                }
            }
            is VisionScanUiState.Success -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = stringResource(id = R.string.scanner_inspection_complete),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.scanner_inspection_complete),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = stringResource(id = scanState.tierUsed.badgeLabelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = scanState.summaryNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                itemsIndexed(scanState.detectedItems) { index, detected ->
                    DetectedIngredientItemCard(
                        item = detected,
                        onToggle = { onToggleDetectedSelection(index) },
                        onAdjustDays = { delta -> onAdjustDetectedDays(index, delta) }
                    )
                }

                item {
                    Button(
                        onClick = onSaveSelectedToPantry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_scanned_items_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        val selectedCount = scanState.detectedItems.count { it.isSelected }
                        Text(
                            text = stringResource(id = R.string.scanner_save_selected_btn, selectedCount),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionGateWithIngestionDock(
    capturedBitmaps: List<Bitmap>,
    focusNote: String,
    isAnalyzing: Boolean,
    onFocusNoteChange: (String) -> Unit,
    onRequestCamera: () -> Unit,
    onPickFromGallery: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onAnalyze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.scanner_permission_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.scanner_permission_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRequestCamera,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("grant_camera_permission_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.scanner_enable_camerax_btn),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onPickFromGallery,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("gallery_picker_button")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.scanner_photo_picker_btn),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (capturedBitmaps.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    capturedBitmaps.forEachIndexed { slotIndex, bmp ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(14.dp)
                                )
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(id = R.string.cd_selected_photo_fmt, slotIndex + 1),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { onRemovePhoto(slotIndex) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(48.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(Color(0x99000000), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(id = R.string.cd_remove_photo),
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = focusNote,
                onValueChange = onFocusNoteChange,
                label = {
                    Text(
                        text = stringResource(id = R.string.scanner_context_hint_label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_focus_note_input")
            )

            val photoWord = if (capturedBitmaps.size == 1) {
                stringResource(id = R.string.word_photo_singular)
            } else {
                stringResource(id = R.string.word_photo_plural)
            }
            Button(
                onClick = onAnalyze,
                enabled = capturedBitmaps.isNotEmpty() && !isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("analyze_photos_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(id = R.string.scanner_inspect_photos_btn_fmt, capturedBitmaps.size, photoWord))
            }
        }
    }
}

@Composable
private fun DetectedIngredientItemCard(
    item: DetectedIngredient,
    onToggle: () -> Unit,
    onAdjustDays: (Int) -> Unit
) {
    val (badgeBg, badgeFg) = when {
        item.daysUntilExpiry <= 1 -> CriticalCrimsonContainer to CriticalCrimson
        item.daysUntilExpiry <= 3 -> ExpiringAmberContainer to ExpiringAmber
        else -> FreshEmeraldContainer to FreshEmerald
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.category.emoji} ${item.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onAdjustDays(-1) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = stringResource(id = R.string.cd_decrease_detected_days), modifier = Modifier.size(18.dp))
                        }
                        Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                            Text(
                                text = stringResource(id = R.string.scanner_days_left_short_fmt, item.daysUntilExpiry),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeFg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        IconButton(
                            onClick = { onAdjustDays(1) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.cd_increase_detected_days), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(
                    text = "${item.quantity} • ${item.visualConditionNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
