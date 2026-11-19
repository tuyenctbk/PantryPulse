package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AiCapabilityTier
import com.example.data.ScanCaptureMode
import com.example.ui.theme.ExpiringAmber
import com.example.ui.theme.FreshEmerald

/**
 * Custom Canvas overlay drawing the CameraX shelf/receipt alignment brackets,
 * animated laser scan sweep during multimodal ingestion, and tap-to-focus reticle.
 */
@Composable
internal fun ViewfinderFramingOverlay(
    captureMode: ScanCaptureMode,
    isIngesting: Boolean,
    focusTapOffset: Offset?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "viewfinder_scan")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line_y"
    )

    val focusScale by animateFloatAsState(
        targetValue = if (focusTapOffset != null) 1f else 1.35f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "focus_ring_scale"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val pad = 22.dp.toPx()
        val rectLeft = pad
        val rectTop = 58.dp.toPx()
        val rectRight = size.width - pad
        val rectBottom = size.height - 42.dp.toPx()
        val rectWidth = (rectRight - rectLeft).coerceAtLeast(10f)
        val rectHeight = (rectBottom - rectTop).coerceAtLeast(10f)

        // Subtle outer framing border
        drawRoundRect(
            color = Color(0x668BD6AF),
            topLeft = Offset(rectLeft, rectTop),
            size = Size(rectWidth, rectHeight),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Corner brackets
        val bracketLen = 24.dp.toPx()
        val bracketStroke = 3.2.dp.toPx()
        val bracketColor = Color(0xFF6EF2B4)

        // Top-Left corner
        drawLine(bracketColor, Offset(rectLeft, rectTop + bracketLen), Offset(rectLeft, rectTop), bracketStroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(rectLeft, rectTop), Offset(rectLeft + bracketLen, rectTop), bracketStroke, StrokeCap.Round)

        // Top-Right corner
        drawLine(bracketColor, Offset(rectRight - bracketLen, rectTop), Offset(rectRight, rectTop), bracketStroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(rectRight, rectTop), Offset(rectRight, rectTop + bracketLen), bracketStroke, StrokeCap.Round)

        // Bottom-Left corner
        drawLine(bracketColor, Offset(rectLeft, rectBottom - bracketLen), Offset(rectLeft, rectBottom), bracketStroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(rectLeft, rectBottom), Offset(rectLeft + bracketLen, rectBottom), bracketStroke, StrokeCap.Round)

        // Bottom-Right corner
        drawLine(bracketColor, Offset(rectRight - bracketLen, rectBottom), Offset(rectRight, rectBottom), bracketStroke, StrokeCap.Round)
        drawLine(bracketColor, Offset(rectRight, rectBottom - bracketLen), Offset(rectRight, rectBottom), bracketStroke, StrokeCap.Round)

        // Mode-specific dashed alignment guide (Horizontal Shelf Divider vs Receipt Column Guides)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
        if (captureMode == ScanCaptureMode.FRIDGE_SHELF) {
            val midY = rectTop + rectHeight * 0.52f
            drawLine(
                color = Color(0x55FFFFFF),
                start = Offset(rectLeft + 16.dp.toPx(), midY),
                end = Offset(rectRight - 16.dp.toPx(), midY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        } else {
            val colLeft = rectLeft + rectWidth * 0.15f
            val colRight = rectRight - rectWidth * 0.15f
            drawLine(
                color = Color(0x55FFFFFF),
                start = Offset(colLeft, rectTop + 10.dp.toPx()),
                end = Offset(colLeft, rectBottom - 10.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            drawLine(
                color = Color(0x55FFFFFF),
                start = Offset(colRight, rectTop + 10.dp.toPx()),
                end = Offset(colRight, rectBottom - 10.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        // Animated laser scan sweep when Gemini Vision is actively ingesting
        if (isIngesting) {
            val scanY = rectTop + rectHeight * scanLineProgress
            drawLine(
                color = Color(0xFF6EF2B4),
                start = Offset(rectLeft + 8.dp.toPx(), scanY),
                end = Offset(rectRight - 8.dp.toPx(), scanY),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Tap-to-focus targeting reticle
        focusTapOffset?.let { tap ->
            val radius = 26.dp.toPx() * focusScale
            drawCircle(
                color = Color(0xFFFABD48),
                radius = radius,
                center = tap,
                style = Stroke(width = 2.2.dp.toPx())
            )
            drawCircle(
                color = Color(0x66FABD48),
                radius = 5.dp.toPx(),
                center = tap
            )
        }
    }
}

/**
 * Displays a subtle initializing progress state while CameraX spins up the Preview stream,
 * or a clean recovery state when running on a device/emulator without physical camera hardware.
 */
@Composable
internal fun CameraStreamStatusOverlay(
    streamState: CameraPreviewStreamState,
    onOpenPhotoPicker: () -> Unit
) {
    when (streamState) {
        is CameraPreviewStreamState.Streaming -> Unit
        is CameraPreviewStreamState.Initializing -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x9908150E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF6EF2B4),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.scanner_permission_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFDFF8EB)
                    )
                }
            }
        }
        is CameraPreviewStreamState.Unavailable -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD90A160F))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF6EF2B4),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.scanner_permission_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    FilledTonalButton(
                        onClick = onOpenPhotoPicker,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.scanner_photo_picker_btn))
                    }
                }
            }
        }
    }
}

/**
 * Live 3-Stage Multimodal Ingestion Overlay displayed inside the CameraX Viewfinder
 * while Gemini Vision is inspecting the captured frames.
 */
@Composable
internal fun GeminiMultimodalIngestionOverlay(
    captureMode: ScanCaptureMode,
    activeTier: AiCapabilityTier,
    currentStage: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD908150E))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF6EF2B4),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (captureMode == ScanCaptureMode.FRIDGE_SHELF) {
                            stringResource(id = R.string.viewfinder_overlay_title_fridge)
                        } else {
                            stringResource(id = R.string.viewfinder_overlay_title_receipt)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(id = R.string.receipt_active_model_fmt, activeTier.modelDisplayName),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA6F2CA)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val stages = listOf(
                1 to stringResource(id = R.string.viewfinder_overlay_stage_1),
                2 to stringResource(id = R.string.viewfinder_overlay_stage_2),
                3 to stringResource(id = R.string.viewfinder_overlay_stage_3)
            )

            stages.forEach { (stageNum, label) ->
                val done = currentStage > stageNum
                val active = currentStage == stageNum
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = when {
                            done -> FreshEmerald
                            active -> ExpiringAmber
                            else -> Color(0xFF6B8576)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            done -> Color(0xFFA6F2CA)
                            active -> Color.White
                            else -> Color(0xFF8AA394)
                        }
                    )
                }
            }
        }
    }
}
