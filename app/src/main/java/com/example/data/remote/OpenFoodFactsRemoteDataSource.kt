package com.example.data.remote

import com.example.ai.BarcodeLookupResult
import com.example.ai.OfflineBarcodeCache
import com.example.ai.OpenFoodFactsClient
import com.example.data.DetectedIngredient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Retrofit + OkHttp + Gson remote data source for querying product nutrition/metadata
 * from the public Open Food Facts v2 API (Tier 0, $0 AI cost).
 */
class OpenFoodFactsRemoteDataSource(
    private val apiService: OpenFoodFactsApiService = OpenFoodFactsNetworkModule.apiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun fetchProductByBarcode(rawBarcode: String): BarcodeLookupResult =
        withContext(ioDispatcher) {
            val barcode = OfflineBarcodeCache.normalizeBarcodeDigits(rawBarcode)
            if (!OfflineBarcodeCache.isValidGroceryBarcode(barcode)) {
                return@withContext BarcodeLookupResult(
                    barcode = barcode,
                    found = false,
                    errorMessage = "Enter a valid 6–14 digit grocery barcode (UPC / EAN)."
                )
            }

            try {
                val response = apiService.getProductByBarcode(barcode = barcode)
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    return@withContext BarcodeLookupResult(
                        barcode = barcode,
                        found = false,
                        errorMessage = "Product not found in Open Food Facts (HTTP ${response.code()})."
                    )
                }
                mapDtoToResult(barcode = barcode, dto = body)
            } catch (e: Exception) {
                BarcodeLookupResult(
                    barcode = barcode,
                    found = false,
                    errorMessage = "Offline or unable to reach Open Food Facts: ${e.message ?: "network error"}"
                )
            }
        }

    fun mapDtoToResult(
        barcode: String,
        dto: OpenFoodFactsResponseDto
    ): BarcodeLookupResult {
        val product = dto.product
        if (dto.status != 1 || product == null) {
            return BarcodeLookupResult(
                barcode = barcode,
                found = false,
                errorMessage = dto.statusVerbose?.takeIf { it.isNotBlank() }
                    ?: "No product entry matched barcode $barcode."
            )
        }

        val rawName = product.productName?.trim().orEmpty()
            .ifBlank { product.genericName?.trim().orEmpty() }
        if (rawName.isBlank()) {
            return BarcodeLookupResult(
                barcode = barcode,
                found = false,
                errorMessage = "Barcode $barcode exists but has no product name yet."
            )
        }

        val brands = product.brands?.trim().orEmpty()
        val displayName = if (brands.isNotBlank() && !rawName.contains(brands, ignoreCase = true)) {
            "$brands $rawName".take(64)
        } else {
            rawName.take(64)
        }

        val quantity = product.quantity?.trim().orEmpty().ifBlank { "1 package" }
        val categoriesLower = product.categoriesTags.orEmpty().joinToString(" ") { it.lowercase() }
        val rawExpiration = product.expirationDate?.trim().orEmpty()
        val parsedDaysFromHeader = OpenFoodFactsClient.computeDaysUntilExpirationString(rawExpiration)
        val category = OpenFoodFactsClient.inferCategory(displayName, categoriesLower)
        val defaultShelfLife = parsedDaysFromHeader ?: OpenFoodFactsClient.estimateShelfLifeDays(category)
        val storageTip = OpenFoodFactsClient.defaultStorageTip(category)
        val estValue = OpenFoodFactsClient.estimateGroceryDollars(category)

        val nutriSuffix = product.nutriscoreGrade
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.uppercase()
            ?.let { " • Nutri-Score $it" }
            .orEmpty()

        return BarcodeLookupResult(
            barcode = barcode,
            found = true,
            ingredient = DetectedIngredient(
                name = displayName,
                category = category,
                quantity = quantity,
                daysUntilExpiry = defaultShelfLife,
                visualConditionNote = "Tier 0 Barcode #$barcode (Open Food Facts • 0 AI credits$nutriSuffix)",
                storageTip = storageTip,
                estimatedValueDollars = estValue,
                isSelected = true
            )
        )
    }
}
