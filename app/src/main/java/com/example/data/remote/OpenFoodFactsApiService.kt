package com.example.data.remote

import com.example.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Retrofit service interface for the public Open Food Facts API v2.
 */
interface OpenFoodFactsApiService {

    @GET("api/v2/product/{barcode}.json")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = DEFAULT_FIELDS
    ): Response<OpenFoodFactsResponseDto>

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
        const val DEFAULT_FIELDS =
            "product_name,generic_name,brands,quantity,categories_tags,expiration_date,image_front_small_url,nutriscore_grade"
    }
}

/**
 * Provides configured OkHttp + Gson + Retrofit instances for the Open Food Facts data layer.
 */
object OpenFoodFactsNetworkModule {

    private const val CONNECT_TIMEOUT_SEC = 8L
    private const val READ_TIMEOUT_SEC = 8L
    private const val WRITE_TIMEOUT_SEC = 8L
    private const val USER_AGENT = "PantryPulse-Android/1.3 (Zero-Waste Kitchen Companion)"

    val gson: Gson by lazy {
        GsonBuilder()
            .setLenient()
            .create()
    }

    val okHttpClient: OkHttpClient by lazy {
        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun createRetrofit(
        baseUrl: String = OpenFoodFactsApiService.BASE_URL,
        client: OkHttpClient = okHttpClient
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: OpenFoodFactsApiService by lazy {
        createRetrofit().create(OpenFoodFactsApiService::class.java)
    }
}
