package com.example.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Gson response DTO for the Open Food Facts v2 Product endpoint:
 * `GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json`
 */
data class OpenFoodFactsResponseDto(
    @SerializedName("status")
    val status: Int = 0,
    @SerializedName("status_verbose")
    val statusVerbose: String? = null,
    @SerializedName("code")
    val code: String? = null,
    @SerializedName("product")
    val product: OpenFoodFactsProductDto? = null
)

/**
 * Product payload returned inside [OpenFoodFactsResponseDto].
 */
data class OpenFoodFactsProductDto(
    @SerializedName("product_name")
    val productName: String? = null,
    @SerializedName("generic_name")
    val genericName: String? = null,
    @SerializedName("brands")
    val brands: String? = null,
    @SerializedName("quantity")
    val quantity: String? = null,
    @SerializedName("categories_tags")
    val categoriesTags: List<String>? = null,
    @SerializedName("expiration_date")
    val expirationDate: String? = null,
    @SerializedName("image_front_small_url")
    val imageFrontSmallUrl: String? = null,
    @SerializedName("nutriscore_grade")
    val nutriscoreGrade: String? = null
)
