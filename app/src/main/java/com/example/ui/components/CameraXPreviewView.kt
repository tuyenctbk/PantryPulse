package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ai.BarcodeScanDebouncer
import com.example.ai.BarcodeScanningUtility
import com.google.mlkit.vision.barcode.BarcodeScanner
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "CameraXPreviewElement"

/**
 * Represents the live streaming lifecycle state of the CameraX Preview use case.
 */
sealed interface CameraPreviewStreamState {
    data object Initializing : CameraPreviewStreamState
    data object Streaming : CameraPreviewStreamState
    data class Unavailable(val reason: String) : CameraPreviewStreamState
}

/**
 * Snapshot of active camera hardware capabilities queried from CameraX [Camera.getCameraInfo].
 */
data class CameraHardwareCapabilities(
    val hasBackCamera: Boolean = true,
    val hasFrontCamera: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0
) {
    val canSwitchLens: Boolean get() = hasBackCamera && hasFrontCamera
    val supportsZoom: Boolean get() = maxZoomRatio > minZoomRatio + 0.1f
    val supportsExposureCompensation: Boolean get() = exposureMax > exposureMin
}

/**
 * State & action controller for [CameraXPreviewElement].
 * Encapsulates CameraX [ImageCapture], focus metering, zoom, torch, and exposure compensation
 * so Compose screens can trigger actions cleanly without coupling to CameraX internals.
 */
@Stable
class CameraXPreviewController internal constructor(
    private val context: Context
) {
    internal val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 960),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
        )
        .build()

    internal var previewView: PreviewView? = null
    internal var boundCamera by mutableStateOf<Camera?>(null)
    internal var activeIsFrontCamera: Boolean = false

    var streamState by mutableStateOf<CameraPreviewStreamState>(CameraPreviewStreamState.Initializing)
        internal set

    var capabilities by mutableStateOf(CameraHardwareCapabilities())
        internal set

    var currentZoomRatio by mutableFloatStateOf(1f)
        internal set

    /**
     * Triggers an autofocus & auto-exposure metering action at the given surface coordinates.
     */
    fun focusAt(x: Float, y: Float) {
        val cam = boundCamera ?: return
        val pv = previewView ?: return
        try {
            val point = pv.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            Log.d(TAG, "Focus metering ignored in current camera state", e)
        }
    }

    /**
     * Sets absolute camera zoom ratio clamped safely to the lens's supported range.
     */
    fun setZoomRatio(targetRatio: Float) {
        val cam = boundCamera ?: return
        if (!capabilities.supportsZoom) return
        val clamped = CameraBitmapProcessor.clampZoomRatio(
            desiredRatio = targetRatio,
            minRatio = capabilities.minZoomRatio,
            maxRatio = capabilities.maxZoomRatio
        )
        currentZoomRatio = clamped
        try {
            cam.cameraControl.setZoomRatio(clamped)
        } catch (e: Exception) {
            Log.d(TAG, "Zoom ratio adjustment ignored", e)
        }
    }

    /**
     * Applies a relative pinch-to-zoom scale delta.
     */
    fun zoomByFactor(scaleFactor: Float) {
        if (scaleFactor == 1f) return
        setZoomRatio(currentZoomRatio * scaleFactor)
    }

    /**
     * Toggles between 1.0x wide and 2.0x close-up (clamped to max zoom) for reading labels/dates.
     */
    fun toggleZoomPreset() {
        val nextTarget = if (currentZoomRatio < 1.5f) {
            2.0f.coerceAtMost(capabilities.maxZoomRatio)
        } else {
            1.0f.coerceAtLeast(capabilities.minZoomRatio)
        }
        setZoomRatio(nextTarget)
    }

    /**
     * Enables or disables the camera torch when hardware flash is available.
     */
    fun setTorchEnabled(enabled: Boolean) {
        val cam = boundCamera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) return
        try {
            cam.cameraControl.enableTorch(enabled)
        } catch (e: Exception) {
            Log.d(TAG, "Torch toggle ignored", e)
        }
    }

    /**
     * Adjusts hardware exposure compensation index (EV) for dim fridge interiors.
     */
    fun setExposureIndex(evIndex: Int) {
        val cam = boundCamera ?: return
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        if (range.upper > range.lower) {
            try {
                cam.cameraControl.setExposureCompensationIndex(
                    evIndex.coerceIn(range.lower, range.upper)
                )
            } catch (e: Exception) {
                Log.d(TAG, "Exposure adjustment ignored", e)
            }
        }
    }

    /**
     * Captures a single still frame from the bound CameraX [ImageCapture] use case,
     * normalizes orientation and dimensions for Gemini Vision, and delivers the Bitmap.
     */
    fun captureFridgeFrame(
        onShutter: () -> Unit,
        onBitmapReady: (Bitmap) -> Unit,
        onCaptureFailed: () -> Unit
    ) {
        onShutter()
        if (boundCamera == null) {
            Log.w(TAG, "No bound camera instance available; opening photo picker")
            onCaptureFailed()
            return
        }
        try {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = try {
                            CameraBitmapProcessor.normalizeImageProxy(
                                image = image,
                                mirrorHorizontally = activeIsFrontCamera
                            )
                        } finally {
                            image.close()
                        }
                        if (bmp != null) {
                            onBitmapReady(bmp)
                        } else {
                            onCaptureFailed()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "CameraX capture error", exception)
                        onCaptureFailed()
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "CameraX takePicture threw", e)
            onCaptureFailed()
        }
    }
}

@Composable
fun rememberCameraXPreviewController(): CameraXPreviewController {
    val context = LocalContext.current
    return remember(context) { CameraXPreviewController(context.applicationContext) }
}

/**
 * Reusable Jetpack Compose UI element implementing the **CameraX `Preview` use case**
 * alongside synchronized `ImageCapture` and `ImageAnalysis` use cases via `UseCaseGroup`
 * and `ProcessCameraProvider`.
 *
 * Features:
 * - Lifecycle-aware binding & non-blocking unbinding on disposal.
 * - WYSIWYG framing via `PreviewView.viewPort` attached to `UseCaseGroup`.
 * - Pinch-to-zoom (`detectTransformGestures`) and tap-to-focus (`detectTapGestures`).
 * - Main-thread-safe ambient luminance telemetry from `ImageAnalysis`.
 * - Live `PreviewView.StreamState` tracking so callers can render initializing/unavailable overlays.
 */
@Composable
fun CameraXPreviewElement(
    controller: CameraXPreviewController,
    useBackCamera: Boolean,
    torchEnabled: Boolean,
    exposureStep: Int,
    onLuminanceMeasured: (Int) -> Unit,
    onTapFocus: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    liveBarcodeScanEnabled: Boolean = false,
    onLiveBarcodeDetected: ((String) -> Unit)? = null,
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanningUtility.createDefaultScanner() }
    val barcodeDebouncer = remember { BarcodeScanDebouncer(cooldownMs = 3_500L) }
    val latestBarcodeEnabled = rememberUpdatedState(liveBarcodeScanEnabled)
    val latestBarcodeCallback = rememberUpdatedState(onLiveBarcodeDetected)

    // TextureView-backed COMPATIBLE mode guarantees proper rounded-corner clipping & Compose overlay layering
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Keep controller synchronized with the remembered PreviewView
    DisposableEffect(controller, previewView) {
        controller.previewView = previewView
        onDispose {
            if (controller.previewView === previewView) {
                controller.previewView = null
            }
        }
    }

    // Observe PreviewView stream state so UI knows when live frames are rendering
    DisposableEffect(lifecycleOwner, previewView) {
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            when (state) {
                PreviewView.StreamState.STREAMING -> {
                    controller.streamState = CameraPreviewStreamState.Streaming
                }
                PreviewView.StreamState.IDLE -> {
                    if (controller.streamState !is CameraPreviewStreamState.Unavailable) {
                        controller.streamState = CameraPreviewStreamState.Initializing
                    }
                }
                null -> Unit
            }
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        onDispose {
            previewView.previewStreamState.removeObserver(streamObserver)
        }
    }

    // Bind CameraX Preview + ImageCapture + ImageAnalysis to lifecycle safely without blocking onDispose
    DisposableEffect(lifecycleOwner, useBackCamera, previewView) {
        var isDisposed = false
        var boundProvider: ProcessCameraProvider? = null
        var zoomObserver: Observer<ZoomState>? = null
        var observedCamera: Camera? = null

        controller.streamState = CameraPreviewStreamState.Initializing

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        val bindRunnable = Runnable {
            if (isDisposed) return@Runnable
            try {
                val cameraProvider = cameraProviderFuture.get()
                if (isDisposed) return@Runnable
                boundProvider = cameraProvider

                val hasBack = runCatching {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                }.getOrDefault(false)
                val hasFront = runCatching {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                }.getOrDefault(false)

                if (!hasBack && !hasFront) {
                    controller.boundCamera = null
                    controller.capabilities = CameraHardwareCapabilities(
                        hasBackCamera = false,
                        hasFrontCamera = false
                    )
                    controller.streamState = CameraPreviewStreamState.Unavailable(
                        context.getString(R.string.camerax_no_hardware)
                    )
                    return@Runnable
                }

                val activeSelector = when {
                    useBackCamera && hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    !useBackCamera && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> CameraSelector.DEFAULT_FRONT_CAMERA
                }
                controller.activeIsFrontCamera = (activeSelector == CameraSelector.DEFAULT_FRONT_CAMERA)

                val previewResolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                val previewUseCase = Preview.Builder()
                    .setResolutionSelector(previewResolutionSelector)
                    .build()
                    .also { preview ->
                        preview.surfaceProvider = previewView.surfaceProvider
                    }

                val imageAnalysisUseCase = ImageAnalysis.Builder()
                    .setResolutionSelector(previewResolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            analysisExecutor,
                            FridgeLuminanceAndBarcodeAnalyzer(
                                onLuminanceMeasured = { luma ->
                                    if (!isDisposed) {
                                        mainHandler.post {
                                            if (!isDisposed) {
                                                onLuminanceMeasured(luma)
                                            }
                                        }
                                    }
                                },
                                scanner = barcodeScanner,
                                isBarcodeEnabled = {
                                    !isDisposed && latestBarcodeEnabled.value && latestBarcodeCallback.value != null
                                },
                                onBarcodeDetected = { rawCode ->
                                    if (!isDisposed && barcodeDebouncer.shouldAccept(rawCode)) {
                                        mainHandler.post {
                                            if (!isDisposed) {
                                                latestBarcodeCallback.value?.invoke(rawCode)
                                            }
                                        }
                                    }
                                }
                            )
                        )
                    }

                cameraProvider.unbindAll()

                // Group Preview + ImageCapture + ImageAnalysis with shared ViewPort for WYSIWYG framing
                val useCaseGroupBuilder = UseCaseGroup.Builder()
                    .addUseCase(previewUseCase)
                    .addUseCase(controller.imageCapture)
                    .addUseCase(imageAnalysisUseCase)

                previewView.viewPort?.let { viewPort ->
                    useCaseGroupBuilder.setViewPort(viewPort)
                }

                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    activeSelector,
                    useCaseGroupBuilder.build()
                )
                controller.boundCamera = boundCamera
                observedCamera = boundCamera

                val zoomStateValue = boundCamera.cameraInfo.zoomState.value
                val exposureRange = boundCamera.cameraInfo.exposureState.exposureCompensationRange

                controller.capabilities = CameraHardwareCapabilities(
                    hasBackCamera = hasBack,
                    hasFrontCamera = hasFront,
                    hasFlashUnit = boundCamera.cameraInfo.hasFlashUnit(),
                    minZoomRatio = zoomStateValue?.minZoomRatio ?: 1f,
                    maxZoomRatio = zoomStateValue?.maxZoomRatio ?: 1f,
                    exposureMin = exposureRange.lower,
                    exposureMax = exposureRange.upper
                )
                controller.currentZoomRatio = zoomStateValue?.zoomRatio ?: 1f

                val zObserver = Observer<ZoomState> { zState ->
                    if (zState != null && !isDisposed) {
                        controller.currentZoomRatio = zState.zoomRatio
                        controller.capabilities = controller.capabilities.copy(
                            minZoomRatio = zState.minZoomRatio,
                            maxZoomRatio = zState.maxZoomRatio
                        )
                    }
                }
                zoomObserver = zObserver
                boundCamera.cameraInfo.zoomState.observe(lifecycleOwner, zObserver)

                // Apply initial torch & EV boost state
                if (boundCamera.cameraInfo.hasFlashUnit()) {
                    boundCamera.cameraControl.enableTorch(torchEnabled)
                }
                if (exposureRange.upper > exposureRange.lower) {
                    boundCamera.cameraControl.setExposureCompensationIndex(
                        exposureStep.coerceIn(exposureRange.lower, exposureRange.upper)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "CameraX Preview bind failed in current environment", e)
                controller.boundCamera = null
                controller.streamState = CameraPreviewStreamState.Unavailable(
                    e.localizedMessage ?: context.getString(R.string.camerax_preview_unavailable)
                )
            }
        }

        cameraProviderFuture.addListener(bindRunnable, mainExecutor)

        onDispose {
            isDisposed = true
            zoomObserver?.let { obs ->
                observedCamera?.cameraInfo?.zoomState?.removeObserver(obs)
            }
            controller.boundCamera = null
            try {
                // Non-blocking unbind: only unbind if provider was already acquired or completed
                val provider = boundProvider ?: if (cameraProviderFuture.isDone && !cameraProviderFuture.isCancelled) {
                    cameraProviderFuture.get()
                } else {
                    null
                }
                provider?.unbindAll()
            } catch (e: Exception) {
                Log.d(TAG, "Non-fatal unbind notice on dispose", e)
            }
        }
    }

    // Shut down analyzer executor and ML Kit BarcodeScanner when composable leaves composition
    DisposableEffect(analysisExecutor, barcodeScanner) {
        onDispose {
            runCatching { barcodeScanner.close() }
            analysisExecutor.shutdown()
        }
    }

    // Reactively update torch and exposure index on active bound camera
    LaunchedEffect(torchEnabled, controller.boundCamera) {
        controller.setTorchEnabled(torchEnabled)
    }

    LaunchedEffect(exposureStep, controller.boundCamera) {
        controller.setExposureIndex(exposureStep)
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0A160F))
            .pointerInput(controller) {
                detectTransformGestures { _, _, zoomChange, _ ->
                    if (zoomChange != 1f) {
                        controller.zoomByFactor(zoomChange)
                    }
                }
            }
            .pointerInput(controller) {
                detectTapGestures { tapOffset ->
                    onTapFocus(tapOffset)
                    controller.focusAt(tapOffset.x, tapOffset.y)
                }
            }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        overlayContent()
    }
}

/**
 * Pure, memory-safe image processing helpers for CameraX frames and Photo Picker images.
 */
object CameraBitmapProcessor {

    const val DEFAULT_MAX_EDGE_PX = 1280

    /**
     * Computes a uniform scale factor (<= 1.0f) so neither width nor height exceeds [maxEdgePx].
     */
    fun computeDownscaleFactor(
        width: Int,
        height: Int,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX
    ): Float {
        if (width <= 0 || height <= 0 || maxEdgePx <= 0) return 1f
        val longestEdge = max(width, height)
        return if (longestEdge <= maxEdgePx) 1f else maxEdgePx.toFloat() / longestEdge.toFloat()
    }

    /**
     * Clamps desired camera zoom ratio to valid hardware bounds.
     */
    fun clampZoomRatio(desiredRatio: Float, minRatio: Float, maxRatio: Float): Float {
        val safeMin = minRatio.coerceAtLeast(0.5f)
        val safeMax = maxRatio.coerceAtLeast(safeMin)
        return desiredRatio.coerceIn(safeMin, safeMax)
    }

    /**
     * Computes average Y-plane luminance (0..255) across sampled bytes for low-light detection.
     */
    fun computeAverageLuminance(bytes: ByteArray, sampleTarget: Int = 256): Int {
        if (bytes.isEmpty()) return 128
        val step = (bytes.size / sampleTarget.coerceAtLeast(1)).coerceAtLeast(1)
        var sum = 0L
        var count = 0
        var idx = 0
        while (idx < bytes.size) {
            sum += (bytes[idx].toInt() and 0xFF)
            count++
            idx += step
        }
        if (count == 0) return 128
        return (sum.toDouble() / count.toDouble()).roundToInt().coerceIn(0, 255)
    }

    /**
     * Converts a CameraX [ImageProxy] into a properly rotated, optionally mirrored, and
     * memory-bounded [Bitmap] in a single matrix pass while recycling temporary bitmaps.
     */
    fun normalizeImageProxy(
        image: ImageProxy,
        mirrorHorizontally: Boolean,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX
    ): Bitmap? {
        return try {
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val scale = computeDownscaleFactor(raw.width, raw.height, maxEdgePx)

            val needsTransform = rotation != 0 || mirrorHorizontally || scale < 0.999f
            if (!needsTransform) {
                return raw
            }

            val matrix = Matrix().apply {
                if (scale < 0.999f) {
                    postScale(scale, scale)
                }
                if (rotation != 0) {
                    postRotate(rotation.toFloat())
                }
                if (mirrorHorizontally) {
                    postScale(-1f, 1f)
                }
            }
            val transformed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (transformed !== raw && !raw.isRecycled) {
                raw.recycle()
            }
            transformed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to normalize ImageProxy to Bitmap", e)
            null
        }
    }

    /**
     * Safely decodes a gallery [Uri] using subsampling (`inSampleSize`) so high-megapixel
     * photos never cause OutOfMemoryError.
     */
    fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }

            var inSampleSize = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while ((longest / inSampleSize) > maxEdgePx * 2) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode sampled URI bitmap", e)
            null
        }
    }
}

/**
 * Dual-purpose CameraX [ImageAnalysis.Analyzer] that:
 * 1. Samples average luminance (0..255) from the Y-plane every 400ms for low-light fridge EV boost.
 * 2. Runs on-device Google ML Kit Barcode Scanning every 450ms when enabled (Tier 0, $0 API cost),
 *    closing [ImageProxy] safely after ML Kit completes.
 */
private class FridgeLuminanceAndBarcodeAnalyzer(
    private val onLuminanceMeasured: (Int) -> Unit,
    private val scanner: BarcodeScanner,
    private val isBarcodeEnabled: () -> Boolean,
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastLuminanceTimestamp = 0L
    private var lastBarcodeTimestamp = 0L
    private val barcodeInFlight = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        var delegatedToMlKit = false
        try {
            val now = System.currentTimeMillis()
            if (now - lastLuminanceTimestamp >= 400L) {
                val plane = image.planes.firstOrNull()
                if (plane != null) {
                    val buffer: ByteBuffer = plane.buffer
                    buffer.rewind()
                    val remaining = buffer.remaining()
                    if (remaining > 0) {
                        var sum = 0L
                        var count = 0
                        val step = (remaining / 256).coerceAtLeast(1)
                        var idx = 0
                        while (idx < remaining) {
                            sum += (buffer.get(idx).toInt() and 0xFF)
                            count++
                            idx += step
                        }
                        if (count > 0) {
                            val avg = (sum.toDouble() / count.toDouble()).roundToInt().coerceIn(0, 255)
                            onLuminanceMeasured(avg)
                        }
                    }
                }
                lastLuminanceTimestamp = now
            }

            if (isBarcodeEnabled() &&
                (now - lastBarcodeTimestamp >= 450L) &&
                barcodeInFlight.compareAndSet(false, true)
            ) {
                lastBarcodeTimestamp = now
                delegatedToMlKit = true
                BarcodeScanningUtility.analyzeCameraFrame(
                    imageProxy = image,
                    scanner = scanner,
                    onBarcodesFound = { results ->
                        results.firstOrNull()?.let { found ->
                            onBarcodeDetected(found.rawValue)
                        }
                    },
                    onFinished = {
                        barcodeInFlight.set(false)
                    }
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Analyzer frame skipped", e)
            if (delegatedToMlKit) {
                barcodeInFlight.set(false)
            }
        } finally {
            if (!delegatedToMlKit) {
                image.close()
            }
        }
    }
}
