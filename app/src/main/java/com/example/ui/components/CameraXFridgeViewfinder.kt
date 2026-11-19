package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiCapabilityTier
import com.example.data.ScanCaptureMode
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Dedicated CameraX Fridge Viewfinder Card for PantryPulse.
 *
 * Composes [CameraXPreviewElement] (live CameraX Preview + ImageCapture + ImageAnalysis)
 * with HUD telemetry, tap-to-focus, pinch-to-zoom & zoom preset pill, low-light EV boost,
 * hardware-aware torch/lens toggles, 1-tap Gemini ingestion shutter, and 2-slot capture dock.
 */
@Composable
fun CameraXFridgeViewfinder(
    capturedBitmaps: List<Bitmap>,
    captureMode: ScanCaptureMode,
    activeTier: AiCapabilityTier,
    isIngesting: Boolean,
    focusNote: String,
    onFocusNoteChange: (String) -> Unit,
    onPhotoCapturedOnly: (Bitmap) -> Unit,
    onCaptureAndTriggerIngestion: (Bitmap, String) -> Unit,
    onTriggerIngestionForQueued: (String) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onClearPhotos: () -> Unit,
    onOpenPhotoPicker: () -> Unit,
    onRunSampleScan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val previewController = rememberCameraXPreviewController()

    var useBackCamera by rememberSaveable { mutableStateOf(true) }
    var torchEnabled by rememberSaveable { mutableStateOf(false) }
    var exposureStep by rememberSaveable { mutableIntStateOf(2) }
    var autoLowLightBoost by rememberSaveable { mutableStateOf(true) }
    var autoIngestOnSnap by rememberSaveable { mutableStateOf(true) }

    // Real-time ambient luminance (0..255) computed from CameraX ImageAnalysis Y-plane
    var ambientLuma by remember { mutableIntStateOf(115) }
    val isLowLightDetected = ambientLuma < 80

    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var showShutterFlash by remember { mutableStateOf(false) }
    var ingestionStage by remember { mutableIntStateOf(1) }

    LaunchedEffect(focusTapOffset) {
        if (focusTapOffset != null) {
            delay(1_100L)
            focusTapOffset = null
        }
    }

    LaunchedEffect(showShutterFlash) {
        if (showShutterFlash) {
            delay(140L)
            showShutterFlash = false
        }
    }

    LaunchedEffect(isIngesting) {
        if (isIngesting) {
            ingestionStage = 1
            delay(750L)
            ingestionStage = 2
            delay(1_400L)
            ingestionStage = 3
        } else {
            ingestionStage = 1
        }
    }

    // Auto-adjust exposure step when low light is detected inside a dim fridge
    LaunchedEffect(isLowLightDetected, autoLowLightBoost) {
        if (autoLowLightBoost) {
            exposureStep = if (isLowLightDetected) 3 else 1
        }
    }

    // Automatically turn off torch state when switching to a lens without flash hardware
    LaunchedEffect(previewController.capabilities.hasFlashUnit) {
        if (!previewController.capabilities.hasFlashUnit && torchEnabled) {
            torchEnabled = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("camerax_fridge_viewfinder_card"),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Live CameraX Preview Compose UI Element
            CameraXPreviewElement(
                controller = previewController,
                useBackCamera = useBackCamera,
                torchEnabled = torchEnabled,
                exposureStep = exposureStep,
                onLuminanceMeasured = { luma -> ambientLuma = luma },
                onTapFocus = { tapOffset -> focusTapOffset = tapOffset },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .testTag("camerax_viewfinder_surface")
            ) {
                // Initializing / Unavailable Camera Hardware Overlay
                CameraStreamStatusOverlay(
                    streamState = previewController.streamState,
                    onOpenPhotoPicker = onOpenPhotoPicker
                )

                // Custom Canvas Framing Guides + Animated Laser Scan Line + Tap-to-Focus Ring
                ViewfinderFramingOverlay(
                    captureMode = captureMode,
                    isIngesting = isIngesting,
                    focusTapOffset = focusTapOffset
                )

                // Top HUD Bar: Ambient Luminance Telemetry + Zoom Preset + Lens Switch + Torch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ambient Luma & EV Boost Badge
                    Surface(
                        color = Color(0xBF0B1911),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.semantics(mergeDescendants = true) {}
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brightness6,
                                contentDescription = null,
                                tint = if (isLowLightDetected) Color(0xFFFABD48) else Color(0xFF6EF2B4),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLowLightDetected) {
                                    stringResource(id = R.string.viewfinder_dim_light_fmt, ambientLuma, exposureStep)
                                } else {
                                    stringResource(id = R.string.viewfinder_clear_light_fmt, ambientLuma, exposureStep)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Interactive Zoom Preset Pill (1.0x <-> 2.0x) when lens supports zoom
                        if (previewController.capabilities.supportsZoom) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { previewController.toggleZoomPreset() }
                                    .testTag("camera_zoom_preset_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = Color(0xBF0B1911),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1fx", previewController.currentZoomRatio),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF6EF2B4),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // Switch Lens Button (48.dp accessible touch target)
                        IconButton(
                            onClick = { useBackCamera = !useBackCamera },
                            enabled = previewController.capabilities.canSwitchLens ||
                                previewController.streamState !is CameraPreviewStreamState.Unavailable,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("switch_camera_lens_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xBF0B1911), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = stringResource(id = R.string.cd_switch_camera_lens),
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Fridge Torch Toggle Button (48.dp accessible touch target, flash-hardware aware)
                        val flashAvailable = previewController.capabilities.hasFlashUnit
                        IconButton(
                            onClick = { if (flashAvailable) torchEnabled = !torchEnabled },
                            enabled = flashAvailable,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("toggle_torch_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Color(0xBF0B1911).copy(alpha = if (flashAvailable) 0.75f else 0.35f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled && flashAvailable) {
                                        Icons.Default.FlashOn
                                    } else {
                                        Icons.Default.FlashOff
                                    },
                                    contentDescription = stringResource(id = R.string.cd_toggle_fridge_torch),
                                    tint = when {
                                        !flashAvailable -> Color.White.copy(alpha = 0.45f)
                                        torchEnabled -> Color(0xFFFABD48)
                                        else -> Color.White
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom HUD Hint Pill inside Viewfinder
                Surface(
                    color = Color(0xB80B1911),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = if (captureMode == ScanCaptureMode.FRIDGE_SHELF) {
                            stringResource(id = R.string.viewfinder_hint_fridge)
                        } else {
                            stringResource(id = R.string.viewfinder_hint_receipt)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE5F6EC),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }

                // Live Multimodal Ingestion Overlay when Gemini Vision is running
                androidx.compose.animation.AnimatedVisibility(
                    visible = isIngesting,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    GeminiMultimodalIngestionOverlay(
                        captureMode = captureMode,
                        activeTier = activeTier,
                        currentStage = ingestionStage
                    )
                }

                // Crisp White Shutter Flash Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showShutterFlash,
                    enter = fadeIn(tween(40)),
                    exit = fadeOut(tween(120)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.78f))
                    )
                }
            }

            // 2. Viewfinder Controls & Gemini Multimodal Ingestion Trigger Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Exposure Compensation (EV) Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.viewfinder_ev_boost_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val evOptions = listOf(
                        0 to stringResource(id = R.string.viewfinder_ev_0),
                        2 to stringResource(id = R.string.viewfinder_ev_1_5),
                        4 to stringResource(id = R.string.viewfinder_ev_3_deep)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        evOptions.forEach { (ev, label) ->
                            FilterChip(
                                selected = exposureStep == ev,
                                onClick = {
                                    autoLowLightBoost = false
                                    exposureStep = ev
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Auto-Trigger Gemini Workflow Toggle
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(id = R.string.viewfinder_instant_ingest_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (autoIngestOnSnap) {
                                        stringResource(id = R.string.viewfinder_instant_ingest_on_desc)
                                    } else {
                                        stringResource(id = R.string.viewfinder_instant_ingest_off_desc)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = autoIngestOnSnap,
                            onCheckedChange = { autoIngestOnSnap = it },
                            modifier = Modifier.testTag("auto_ingest_on_snap_switch")
                        )
                    }
                }

                // Primary Shutter & Multimodal Ingestion Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            previewController.captureFridgeFrame(
                                onShutter = { showShutterFlash = true },
                                onBitmapReady = { bmp ->
                                    if (autoIngestOnSnap) {
                                        onCaptureAndTriggerIngestion(bmp, focusNote)
                                    } else {
                                        onPhotoCapturedOnly(bmp)
                                    }
                                },
                                onFallbackSample = { onRunSampleScan(focusNote) }
                            )
                        },
                        enabled = !isIngesting,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("capture_fridge_photo_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = if (autoIngestOnSnap) Icons.Default.AutoAwesome else Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (autoIngestOnSnap) {
                                stringResource(id = R.string.viewfinder_btn_snap_ingest)
                            } else if (capturedBitmaps.isEmpty()) {
                                stringResource(id = R.string.viewfinder_btn_snap_shelf_1)
                            } else {
                                stringResource(id = R.string.viewfinder_btn_snap_shelf_2)
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    if (autoIngestOnSnap && capturedBitmaps.size < 2) {
                        OutlinedButton(
                            onClick = {
                                previewController.captureFridgeFrame(
                                    onShutter = { showShutterFlash = true },
                                    onBitmapReady = { bmp -> onPhotoCapturedOnly(bmp) },
                                    onFallbackSample = { onRunSampleScan(focusNote) }
                                )
                            },
                            enabled = !isIngesting,
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("queue_shelf_photo_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = stringResource(id = R.string.cd_queue_shelf_photo),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenPhotoPicker,
                        enabled = !isIngesting,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("pick_gallery_photo_button"),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(id = R.string.viewfinder_btn_gallery))
                    }
                }

                // Instant Demo Trigger Button
                OutlinedButton(
                    onClick = { onRunSampleScan(focusNote) },
                    enabled = !isIngesting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sample_scan_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (captureMode == ScanCaptureMode.FRIDGE_SHELF) {
                            stringResource(id = R.string.scanner_demo_fridge_btn)
                        } else {
                            stringResource(id = R.string.scanner_demo_receipt_btn)
                        }
                    )
                }

                HorizontalDivider()

                // 3. Embedded 2-Slot Captured Frame Dock & Manual Ingestion Trigger
                CapturedFrameQueueDock(
                    capturedBitmaps = capturedBitmaps,
                    isIngesting = isIngesting,
                    activeTier = activeTier,
                    focusNote = focusNote,
                    onFocusNoteChange = onFocusNoteChange,
                    onRemovePhoto = onRemovePhoto,
                    onClearPhotos = onClearPhotos,
                    onTriggerIngestionForQueued = onTriggerIngestionForQueued
                )
            }
        }
    }
}

@Composable
private fun CapturedFrameQueueDock(
    capturedBitmaps: List<Bitmap>,
    isIngesting: Boolean,
    activeTier: AiCapabilityTier,
    focusNote: String,
    onFocusNoteChange: (String) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onClearPhotos: () -> Unit,
    onTriggerIngestionForQueued: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.viewfinder_queue_title_fmt, capturedBitmaps.size),
            style = MaterialTheme.typography.titleSmall
        )
        if (capturedBitmaps.isNotEmpty() && !isIngesting) {
            Text(
                text = stringResource(id = R.string.viewfinder_btn_clear_queue),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onClearPhotos() }
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .testTag("clear_captured_photos_button")
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (slotIndex in 0..1) {
            val bmp = capturedBitmaps.getOrNull(slotIndex)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(104.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = if (bmp != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        },
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(id = R.string.cd_captured_frame_fmt, slotIndex + 1),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color(0xB3000000),
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = stringResource(id = R.string.viewfinder_frame_badge_fmt, slotIndex + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = { onRemovePhoto(slotIndex) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xB3000000), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.cd_remove_frame),
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (slotIndex == 0) {
                                stringResource(id = R.string.viewfinder_slot_1)
                            } else {
                                stringResource(id = R.string.viewfinder_slot_2)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(id = R.string.viewfinder_ready_for_capture),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    OutlinedTextField(
        value = focusNote,
        onValueChange = onFocusNoteChange,
        label = { Text(stringResource(id = R.string.scanner_context_hint_label)) },
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
        onClick = { onTriggerIngestionForQueued(focusNote) },
        enabled = capturedBitmaps.isNotEmpty() && !isIngesting,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("analyze_photos_button"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(
                id = R.string.viewfinder_inspect_btn_fmt,
                capturedBitmaps.size,
                photoWord,
                stringResource(id = activeTier.badgeLabelRes)
            )
        )
    }
}
