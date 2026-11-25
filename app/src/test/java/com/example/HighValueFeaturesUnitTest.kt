package com.example

import com.example.ai.CookingVoiceCommand
import com.example.ai.CookingVoiceCommandParser
import com.example.ai.OpenFoodFactsClient
import com.example.ai.SecureByokKeyStore
import com.example.ai.UsageQuotaSnapshot
import com.example.data.KitchenSetupState
import com.example.data.PantryCategory
import com.example.data.PantryItemEntity
import com.example.data.WeeklyMealPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighValueFeaturesUnitTest {

    @Test
    fun openFoodFactsParserMapsDairyProductZeroCredits() {
        val sampleJson = """
            {
              "status": 1,
              "product": {
                "product_name": "Greek Plain Yogurt",
                "brands": "Chobani",
                "quantity": "500 g",
                "categories_tags": ["en:dairies", "en:fermented-foods", "en:yogurts"]
              }
            }
        """.trimIndent()

        val result = OpenFoodFactsClient.parseOpenFoodFactsResponse("0894700010045", sampleJson)
        assertTrue(result.found)
        val item = result.ingredient
        assertNotNull(item)
        assertEquals("Chobani Greek Plain Yogurt", item!!.name)
        assertEquals(PantryCategory.DAIRY_CHEESE, item.category)
        assertEquals("500 g", item.quantity)
        assertEquals(7, item.daysUntilExpiry)
        assertTrue(item.isSelected)
    }

    @Test
    fun openFoodFactsParserHandlesUnknownBarcodeGracefully() {
        val missingJson = """{"status": 0, "status_verbose": "product not found"}"""
        val result = OpenFoodFactsClient.parseOpenFoodFactsResponse("0000000000000", missingJson)
        assertFalse(result.found)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun cookingVoiceCommandParserHandlesTimersStepsAndSubstitutions() {
        assertEquals(CookingVoiceCommand.NextStep, CookingVoiceCommandParser.parse("Next step"))
        assertEquals(CookingVoiceCommand.PreviousStep, CookingVoiceCommandParser.parse("Go back"))
        assertEquals(CookingVoiceCommand.RepeatStep, CookingVoiceCommandParser.parse("Repeat please"))
        assertEquals(
            CookingVoiceCommand.SetTimerSeconds(420, autoStart = true),
            CookingVoiceCommandParser.parse("Set a 7 minute timer")
        )
        assertEquals(
            CookingVoiceCommand.SetTimerSeconds(300, autoStart = true),
            CookingVoiceCommandParser.parse("five minute timer")
        )
        assertEquals(CookingVoiceCommand.PauseTimer, CookingVoiceCommandParser.parse("Pause timer"))

        val swapCmd = CookingVoiceCommandParser.parse("What can I swap for heavy cream?")
        assertTrue(swapCmd is CookingVoiceCommand.AskSousChef)
        assertEquals("What can I swap for heavy cream?", (swapCmd as CookingVoiceCommand.AskSousChef).question)
    }

    @Test
    fun weeklyMealPlannerPrioritizesExpiringItemsAndExtractsMissingStaples() {
        val items = listOf(
            PantryItemEntity(
                id = 1L,
                name = "Baby Spinach",
                category = PantryCategory.PRODUCE.name,
                quantity = "1 bag",
                daysUntilExpiry = 1,
                visualConditionNote = "Slightly wilted leaves",
                storageTip = "Paper towel wrap in crisper",
                estimatedValueDollars = 4.50
            ),
            PantryItemEntity(
                id = 2L,
                name = "Crumbled Feta",
                category = PantryCategory.DAIRY_CHEESE.name,
                quantity = "4 oz",
                daysUntilExpiry = 2,
                visualConditionNote = "Opened tub",
                storageTip = "Keep sealed on middle shelf",
                estimatedValueDollars = 5.00
            ),
            PantryItemEntity(
                id = 3L,
                name = "Half-Jar Marinara",
                category = PantryCategory.SAUCES_JARS.name,
                quantity = "1/2 jar",
                daysUntilExpiry = 6,
                visualConditionNote = "Opened jar",
                storageTip = "Wipe rim and refrigerate",
                estimatedValueDollars = 3.25
            )
        )

        val plan = WeeklyMealPlanner.buildZeroWastePlan(
            pantryItems = items,
            kitchenSetup = KitchenSetupState(),
            daysCount = 5
        )
        assertEquals(5, plan.daysCount)
        assertEquals(5, plan.days.size)
        assertTrue(plan.days.first().mealTitle.contains("Baby Spinach"))
        assertTrue(plan.totalAtRiskDollarsRescued >= 9.50)
        assertTrue(plan.consolidatedMissingStaples.isNotEmpty())
    }

    @Test
    fun purchasedCreditPacksSurviveDailyMidnightRollover() {
        val yesterday = UsageQuotaSnapshot(
            dateIso = "2026-11-24",
            visionUsed = 10,
            bonusGraceCredits = 3,
            purchasedCreditBalance = 30,
            graceClaimed = true,
            creditUnitsUsed = 30
        )
        assertEquals(43, yesterday.totalAllowance)
        assertEquals(33, yesterday.remainingCredits)

        val today = yesterday.rolloverForDate("2026-11-25")
        assertEquals(0, today.bonusGraceCredits)
        assertFalse(today.graceClaimed)
        assertEquals(30, today.purchasedCreditBalance)
        assertEquals(40, today.totalAllowance)
        assertEquals(40, today.remainingCredits)
    }

    @Test
    fun byokKeyMaskingHidesMiddleCharacters() {
        assertEquals("AIza••••••••7890", SecureByokKeyStore.buildMaskedPreview("AIzaSyFakeExampleKey1234567890"))
    }

    @Test
    fun unwrapJsonPayloadStripsMarkdownCodeFences() {
        val fenced = "```json\n{\"summaryNote\":\"ok\"}\n```"
        assertEquals("{\"summaryNote\":\"ok\"}", com.example.ai.PantryAiService.unwrapJsonPayload(fenced))
        assertEquals("{\"a\":1}", com.example.ai.PantryAiService.unwrapJsonPayload("  {\"a\":1}  "))
    }

    @Test
    fun offlineBarcodeCacheRoundtripsAndDebouncerThrottlesDuplicates() {
        val sample = OpenFoodFactsClient.parseOpenFoodFactsResponse(
            "0894700010045",
            """{"status":1,"product":{"product_name":"Plain Yogurt","brands":"Fage","quantity":"500 g","categories_tags":["en:dairies"]}}"""
        ).ingredient!!

        val encoded = com.example.ai.OfflineBarcodeCache.encodeCachedIngredient(sample)
        val decoded = com.example.ai.OfflineBarcodeCache.decodeCachedIngredient("0894700010045", encoded)
        assertNotNull(decoded)
        assertEquals("Fage Plain Yogurt", decoded!!.name)
        assertEquals(PantryCategory.DAIRY_CHEESE, decoded.category)
        assertEquals("500 g", decoded.quantity)

        val emptyPlan = WeeklyMealPlanner.buildZeroWastePlan(
            pantryItems = emptyList(),
            kitchenSetup = KitchenSetupState(),
            daysCount = 5
        )
        assertTrue(emptyPlan.days.isEmpty())

        val debouncer = com.example.ai.BarcodeScanDebouncer(cooldownMs = 3_500L)
        assertTrue(debouncer.shouldAccept("3017620422003", nowMs = 1_000L))
        assertFalse(debouncer.shouldAccept("3017620422003", nowMs = 2_000L))
        assertTrue(debouncer.shouldAccept("0894700010045", nowMs = 2_100L))
        assertTrue(debouncer.shouldAccept("3017620422003", nowMs = 6_000L))
    }

    @Test
    fun repositoryMapsOpenFoodFactsProductIntoPopulatedIngredientEntity() {
        val baseNow = 1_764_000_000_000L
        val detected = OpenFoodFactsClient.parseOpenFoodFactsResponse(
            "3017620422003",
            """{"status":1,"product":{"product_name":"Hazelnut Spread","brands":"Ferrero","quantity":"400 g","categories_tags":["en:spreads"]}}"""
        ).ingredient!!

        val ingredient = com.example.data.PantryRepository.mapDetectedProductToIngredient(
            detected = detected,
            fromTimestampMillis = baseNow
        )
        assertEquals("Ferrero Hazelnut Spread", ingredient.name)
        assertEquals(400.0, ingredient.quantity, 0.01)
        assertEquals("g", ingredient.unit)
        assertTrue(ingredient.expiryDate > baseNow)

        assertTrue(com.example.ai.BarcodeScanningUtility.isEanOrUpcLabel("EAN-13"))
        assertTrue(com.example.ai.BarcodeScanningUtility.isEanOrUpcLabel("UPC-A"))
        assertFalse(com.example.ai.BarcodeScanningUtility.isEanOrUpcLabel("CODE-128"))
    }

    @Test
    fun gsonDeserializesOpenFoodFactsResponseDtoAndRemoteDataSourceMapsIngredient() =
        kotlinx.coroutines.runBlocking {
            val payload = """
                {
                  "status": 1,
                  "status_verbose": "product found",
                  "code": "0041303001234",
                  "product": {
                    "generic_name": "Organic Baby Spinach",
                    "brands": "Earthbound Farm",
                    "quantity": "142 g",
                    "categories_tags": ["en:plant-based-foods", "en:vegetables", "en:produce"],
                    "nutriscore_grade": "a",
                    "image_front_small_url": "https://images.openfoodfacts.org/sample.jpg"
                  }
                }
            """.trimIndent()

            val dto = com.example.data.remote.OpenFoodFactsNetworkModule.gson.fromJson(
                payload,
                com.example.data.remote.OpenFoodFactsResponseDto::class.java
            )
            assertEquals(1, dto.status)
            assertEquals("Organic Baby Spinach", dto.product?.genericName)
            assertEquals("a", dto.product?.nutriscoreGrade)

            val fakeService = object : com.example.data.remote.OpenFoodFactsApiService {
                override suspend fun getProductByBarcode(
                    barcode: String,
                    fields: String
                ): retrofit2.Response<com.example.data.remote.OpenFoodFactsResponseDto> {
                    return if (barcode == "0041303001234") {
                        retrofit2.Response.success(dto)
                    } else {
                        retrofit2.Response.error(
                            404,
                            okhttp3.ResponseBody.Companion.let {
                                with(it) { """{"status":0}""".toResponseBody(null) }
                            }
                        )
                    }
                }
            }

            val remote = com.example.data.remote.OpenFoodFactsRemoteDataSource(
                apiService = fakeService,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            )

            val foundResult = remote.fetchProductByBarcode("0041303001234")
            assertTrue(foundResult.found)
            val detected = foundResult.ingredient!!
            assertEquals("Earthbound Farm Organic Baby Spinach", detected.name)
            assertEquals(PantryCategory.PRODUCE, detected.category)
            assertTrue(detected.visualConditionNote.contains("Nutri-Score A"))

            val entity = com.example.data.PantryRepository.mapDetectedProductToIngredient(detected)
            assertEquals("Earthbound Farm Organic Baby Spinach", entity.name)
            assertEquals(142.0, entity.quantity, 0.01)
            assertEquals("g", entity.unit)

            val missingResult = remote.fetchProductByBarcode("9999999999999")
            assertFalse(missingResult.found)
            assertTrue(missingResult.errorMessage!!.contains("HTTP 404"))
        }
}
