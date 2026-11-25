package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.example.data.DetectedIngredient
import com.example.data.Ingredient
import com.example.data.computeExpiryEpochMillis
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume

data class ScannedBarcodeResult(
    val rawValue: String,
    val formatLabel: String
)

/**
 * Pure Kotlin debouncer preventing duplicate network/cache lookups when holding
 * the same grocery package barcode in front of the live CameraX viewfinder.
 */
class BarcodeScanDebouncer(
    private val cooldownMs: Long = 3_500L
) {
    private var lastBarcode: String? = null
    private var lastAcceptedAtMs: Long = 0L

    fun shouldAccept(barcode: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val normalized = OfflineBarcodeCache.normalizeBarcodeDigits(barcode)
        if (!OfflineBarcodeCache.isValidGroceryBarcode(normalized)) return false
        val sameAsLast = normalized == lastBarcode
        if (sameAsLast && (nowMs - lastAcceptedAtMs) < cooldownMs) {
            return false
        }
        lastBarcode = normalized
        lastAcceptedAtMs = nowMs
        return true
    }

    fun reset() {
        lastBarcode = null
        lastAcceptedAtMs = 0L
    }
}

/**
 * Google ML Kit Barcode Scanner + Tier 0 Offline-First Open Food Facts Utility.
 *
 * Resolution chain ($0 cloud AI cost):
 * 1. On-device ML Kit Barcode Detection (live CameraX frames or still Gallery bitmaps).
 * 2. Local DataStore offline product cache (instantaneous, airplane-mode friendly).
 * 3. Public Open Food Facts v2 API lookup with automatic write-through caching.
 * 4. Built-in offline grocery catalog fallback when network is unavailable.
 */
class BarcodeScanningUtility(
    context: Context
) : Closeable {

    private val appContext = context.applicationContext
    private val offlineCache = OfflineBarcodeCache(appContext)

    private val scannerOptions: BarcodeScannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_ITF
        )
        .build()

    val scanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(scannerOptions)
    }

    /**
     * Extracts valid grocery barcodes from an already-decoded [Bitmap] (e.g., Photo Picker or capture).
     */
    suspend fun scanBitmapForBarcodes(bitmap: Bitmap): List<ScannedBarcodeResult> =
        suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        if (continuation.isActive) {
                            continuation.resume(mapBarcodes(barcodes))
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "ML Kit bitmap barcode scan failed", e)
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Failed creating InputImage from Bitmap", e)
                if (continuation.isActive) {
                    continuation.resume(emptyList())
                }
            }
        }

    /**
     * Convenience wrapper focused strictly on grocery EAN-13, EAN-8, UPC-A, and UPC-E codes
     * extracted from a still [Bitmap].
     */
    suspend fun detectEan13OrUpcCodes(bitmap: Bitmap): List<ScannedBarcodeResult> =
        scanBitmapForBarcodes(bitmap).filter { isEanOrUpcLabel(it.formatLabel) }

    /**
     * Converts a resolved [DetectedIngredient] into the canonical Room [Ingredient] entity
     * with structured `quantity`, `unit`, and `expiryDate`.
     */
    fun toIngredientEntity(
        detected: DetectedIngredient,
        fromTimestampMillis: Long = System.currentTimeMillis()
    ): Ingredient {
        val (qty, unit) = Ingredient.parseQuantityAndUnit(detected.quantity)
        return Ingredient(
            name = detected.name.trim(),
            quantity = qty,
            unit = unit,
            expiryDate = computeExpiryEpochMillis(detected.daysUntilExpiry, fromTimestampMillis)
        )
    }

    /**
     * Offline-first Tier 0 lookup chain:
     * Local DataStore Cache -> Open Food Facts API v2 (write-through cached).
     */
    suspend fun resolveIngredientOfflineFirst(rawBarcode: String): Tier0BarcodeResolution {
        val barcode = OfflineBarcodeCache.normalizeBarcodeDigits(rawBarcode)
        if (!OfflineBarcodeCache.isValidGroceryBarcode(barcode)) {
            return Tier0BarcodeResolution(
                barcode = barcode,
                found = false,
                source = BarcodeLookupSource.NOT_FOUND,
                errorMessage = appContext.getString(com.example.R.string.tier0_barcode_invalid_error)
            )
        }

        // 1. Offline-first check in persistent local DataStore cache
        offlineCache.getCachedIngredient(barcode)?.let { cachedItem ->
            return Tier0BarcodeResolution(
                barcode = barcode,
                found = true,
                ingredient = cachedItem,
                source = BarcodeLookupSource.OFFLINE_CACHE
            )
        }

        // 2. Live Open Food Facts v2 API lookup
        val apiResult = OpenFoodFactsClient.lookupBarcode(barcode)
        val apiIngredient = apiResult.ingredient
        if (apiResult.found && apiIngredient != null) {
            offlineCache.putCachedIngredient(barcode, apiIngredient)
            return Tier0BarcodeResolution(
                barcode = barcode,
                found = true,
                ingredient = apiIngredient,
                source = BarcodeLookupSource.OPEN_FOOD_FACTS_API
            )
        }

        return Tier0BarcodeResolution(
            barcode = barcode,
            found = false,
            source = BarcodeLookupSource.NOT_FOUND,
            errorMessage = apiResult.errorMessage
                ?: appContext.getString(com.example.R.string.tier0_barcode_not_found_fmt, barcode)
        )
    }

    override fun close() {
        runCatching { scanner.close() }
    }

    companion object {
        private const val TAG = "BarcodeScanningUtil"

        fun createDefaultScanner(): BarcodeScanner {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_ITF
                )
                .build()
            return BarcodeScanning.getClient(options)
        }

        /**
         * Processes a live CameraX [ImageProxy] through Google ML Kit [BarcodeScanner].
         * Caller MUST NOT close [imageProxy] prematurely — this helper ensures [imageProxy.close]
         * is called safely in `addOnCompleteListener` (or immediately on early return/exception).
         */
        @OptIn(ExperimentalGetImage::class)
        fun analyzeCameraFrame(
            imageProxy: ImageProxy,
            scanner: BarcodeScanner,
            onBarcodesFound: (List<ScannedBarcodeResult>) -> Unit,
            onFinished: () -> Unit
        ) {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                onFinished()
                return
            }
            try {
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        val mapped = mapBarcodes(barcodes)
                        if (mapped.isNotEmpty()) {
                            onBarcodesFound(mapped)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.d(TAG, "ML Kit frame scan skipped", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                        onFinished()
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Error preparing InputImage for ML Kit", e)
                imageProxy.close()
                onFinished()
            }
        }

        internal fun mapBarcodes(barcodes: List<Barcode>): List<ScannedBarcodeResult> {
            return barcodes.mapNotNull { barcode ->
                val raw = barcode.rawValue?.trim().orEmpty()
                val digits = OfflineBarcodeCache.normalizeBarcodeDigits(raw)
                if (!OfflineBarcodeCache.isValidGroceryBarcode(digits)) {
                    null
                } else {
                    ScannedBarcodeResult(
                        rawValue = digits,
                        formatLabel = formatName(barcode.format)
                    )
                }
            }.distinctBy { it.rawValue }
        }

        fun isEanOrUpcFormat(format: Int): Boolean = when (format) {
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E -> true
            else -> false
        }

        fun isEanOrUpcLabel(label: String): Boolean =
            label == "EAN-13" || label == "EAN-8" || label == "UPC-A" || label == "UPC-E"

        fun formatName(format: Int): String = when (format) {
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_CODE_128 -> "CODE-128"
            Barcode.FORMAT_CODE_39 -> "CODE-39"
            Barcode.FORMAT_ITF -> "ITF"
            else -> "BARCODE"
        }
    }
}
