package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Direct HTTPS pass-through client used ONLY when the user explicitly opts into
 * BYOK ("Bring Your Own Gemini API Key") stored encrypted in Android Keystore.
 *
 * Security: Sends the user's key exclusively in the `x-goog-api-key` HTTP header over TLS,
 * never in URL query parameters and never written to logs.
 */
object ByokGeminiRestClient {

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 25_000

    suspend fun generateContent(
        apiKey: String,
        modelName: String,
        prompt: String,
        bitmaps: List<Bitmap> = emptyList(),
        expectJson: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val safeModel = modelName.trim().ifBlank { "gemini-2.5-flash" }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$safeModel:generateContent"

        val parts = JSONArray()
        bitmaps.forEach { bmp ->
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, AiModelConfig.JPEG_UPLOAD_QUALITY, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "image/jpeg")
                        .put("data", b64)
                )
            )
        }
        parts.put(JSONObject().put("text", prompt))

        val payload = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", parts)
            )
        )
        if (expectJson) {
            payload.put(
                "generationConfig",
                JSONObject().put("responseMimeType", "application/json")
            )
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val rawBody = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }.orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("BYOK Gemini request failed (HTTP $code)")
            }

            val root = JSONObject(rawBody)
            val candidates = root.optJSONArray("candidates")
            val firstContent = candidates?.optJSONObject(0)?.optJSONObject("content")
            val outParts = firstContent?.optJSONArray("parts")
            val text = buildString {
                if (outParts != null) {
                    for (i in 0 until outParts.length()) {
                        append(outParts.optJSONObject(i)?.optString("text").orEmpty())
                    }
                }
            }.trim()

            if (text.isBlank()) {
                throw IllegalStateException("BYOK Gemini returned an empty candidate.")
            }
            text
        } finally {
            connection?.disconnect()
        }
    }
}
