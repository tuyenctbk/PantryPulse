package com.example.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import com.example.R
import java.io.ByteArrayOutputStream

/**
 * Centralized Model-Agnostic Capability Tiers.
 * Never hardcode model names inside UI screens or ViewModels.
 */
enum class AiCapabilityTier(
    val badgeLabel: String,
    val modelDisplayName: String,
    val description: String,
    @StringRes val badgeLabelRes: Int
) {
    TIER_1_PRIMARY(
        badgeLabel = "Tier 1 • Standard AI",
        modelDisplayName = "Gemini 3.8 Flash",
        description = "Full multimodal reasoning & deep culinary context",
        badgeLabelRes = R.string.tier_badge_1
    ),
    TIER_2_ECO_LITE(
        badgeLabel = "Tier 2 • Eco Lite ⚡",
        modelDisplayName = "Gemini 3.5 Flash-Lite",
        description = "High-efficiency lite model (stretches quota 3× further)",
        badgeLabelRes = R.string.tier_badge_2
    ),
    TIER_3_LOCAL_FALLBACK(
        badgeLabel = "Tier 3 • Local Zero-Cost 🛡️",
        modelDisplayName = "On-Device Culinary Engine",
        description = "Deterministic offline synthesis & local reflex (0 cloud credits)",
        badgeLabelRes = R.string.tier_badge_3
    )
}

enum class AiFeatureType(val displayName: String) {
    VISION_SCAN("Fridge & Receipt Vision"),
    RECIPE_RANKING("Rescue Recipe Generator"),
    SOUS_CHEF_CHAT("Sous-Chef Chat"),
    VOICE_MEMO_PARSE("Voice-to-Inventory AI"),
    SHELF_LIFE_PRESERVATION("Shelf-Life & Preservation Coach")
}

object AiModelConfig {
    // Tier 1 — Primary Standard (used when quota < 80% and Eco Mode is off)
    const val PRIMARY_REASONING_MODEL = "gemini-3.8-flash"
    const val PRIMARY_VISION_MODEL = "gemini-3.8-flash"

    // Tier 2 — High-Efficiency Lite (used on 429, >= 80% quota, or Eco Lite Mode)
    const val ECONOMY_LITE_MODEL = "gemini-3.5-flash-lite"
    const val ECONOMY_VISION_MODEL = "gemini-3.5-flash-lite"

    // Deduplication cache TTL (45 seconds)
    const val DEDUP_CACHE_TTL_MS = 45_000L

    // Max downscaled dimension for camera/gallery bitmaps before upload
    const val MAX_UPLOAD_DIMENSION_PX = 896
    const val JPEG_UPLOAD_QUALITY = 82

    fun selectModelForTier(tier: AiCapabilityTier, feature: AiFeatureType): String {
        return when (tier) {
            AiCapabilityTier.TIER_1_PRIMARY -> when (feature) {
                AiFeatureType.VISION_SCAN -> PRIMARY_VISION_MODEL
                AiFeatureType.RECIPE_RANKING,
                AiFeatureType.SOUS_CHEF_CHAT,
                AiFeatureType.VOICE_MEMO_PARSE,
                AiFeatureType.SHELF_LIFE_PRESERVATION -> PRIMARY_REASONING_MODEL
            }
            AiCapabilityTier.TIER_2_ECO_LITE -> when (feature) {
                AiFeatureType.VISION_SCAN -> ECONOMY_VISION_MODEL
                AiFeatureType.RECIPE_RANKING,
                AiFeatureType.SOUS_CHEF_CHAT,
                AiFeatureType.VOICE_MEMO_PARSE,
                AiFeatureType.SHELF_LIFE_PRESERVATION -> ECONOMY_LITE_MODEL
            }
            AiCapabilityTier.TIER_3_LOCAL_FALLBACK -> "on-device-local-v1"
        }
    }

    /**
     * Downscales bitmap to <= 896px and compresses at JPEG 82% for token & bandwidth hygiene.
     */
    fun optimizeBitmapForUpload(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val scaled = if (width <= MAX_UPLOAD_DIMENSION_PX && height <= MAX_UPLOAD_DIMENSION_PX) {
            source
        } else {
            val ratio = width.toFloat() / height.toFloat()
            val (targetW, targetH) = if (width >= height) {
                MAX_UPLOAD_DIMENSION_PX to (MAX_UPLOAD_DIMENSION_PX / ratio).toInt().coerceAtLeast(1)
            } else {
                (MAX_UPLOAD_DIMENSION_PX * ratio).toInt().coerceAtLeast(1) to MAX_UPLOAD_DIMENSION_PX
            }
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }
        return try {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_UPLOAD_QUALITY, out)
            val bytes = out.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: scaled
        } catch (_: Exception) {
            scaled
        }
    }

    /**
     * Computes a fast deterministic fingerprint of prompt + media dimensions/center pixel
     * for 45-second deduplication so double-taps cost 0 extra tokens.
     */
    fun computeRequestFingerprint(prompt: String, bitmaps: List<Bitmap> = emptyList()): String {
        val bmpHash = bitmaps.fold(17) { acc, bmp ->
            val centerPixel = if (bmp.width > 0 && bmp.height > 0) {
                bmp.getPixel(bmp.width / 2, bmp.height / 2)
            } else 0
            31 * acc + bmp.width * 7 + bmp.height * 13 + centerPixel
        }
        return "${prompt.trim().lowercase().hashCode()}_$bmpHash"
    }
}
