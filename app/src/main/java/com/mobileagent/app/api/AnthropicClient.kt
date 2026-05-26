package com.mobileagent.app.api

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnthropicClient(private val config: ApiConfig) : VlmApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun predictWithImages(
        systemPrompt: String?,
        textPrompt: String,
        images: List<Bitmap>
    ): Result<String> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        repeat(MAX_RETRIES) { attempt ->
            try {
                Log.d("AnthropicClient", "Attempt ${attempt + 1}/$MAX_RETRIES")
                val result = doRequest(systemPrompt, textPrompt, images)
                return@withContext Result.success(result)
            } catch (e: Exception) {
                Log.e("AnthropicClient", "Attempt ${attempt + 1} failed: ${e.message}")
                lastError = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        Result.failure(lastError ?: Exception("Unknown error"))
    }

    private fun doRequest(
        systemPrompt: String?,
        textPrompt: String,
        images: List<Bitmap>
    ): String {
        val userContent = buildJsonArray {
            for (image in images) {
                addJsonObject {
                    put("type", "image")
                    putJsonObject("source") {
                        put("type", "base64")
                        put("media_type", "image/jpeg")
                        put("data", ImageEncoder.encode(image))
                    }
                }
            }
            addJsonObject {
                put("type", "text")
                put("text", textPrompt)
            }
        }

        val requestBody = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 4096)
            if (systemPrompt != null) {
                put("system", systemPrompt)
            }
            put("messages", buildJsonArray {
                addJsonObject {
                    put("role", "user")
                    put("content", userContent)
                }
            })
        }

        val endpoint = config.endpoint.trimEnd('/')
        val url = if (endpoint.endsWith("/messages")) endpoint
        else "$endpoint/messages"

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d("AnthropicClient", "Sending request to $url, images=${images.size}")
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response body")
        Log.d("AnthropicClient", "Response ${response.code}, body length=${body.length}")

        if (!response.isSuccessful) {
            Log.e("AnthropicClient", "API error: $body")
            throw Exception("API error ${response.code}: $body")
        }

        val jsonResponse = json.parseToJsonElement(body).jsonObject
        return jsonResponse["content"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content
    }

    companion object {
        private const val MAX_RETRIES = 10
        private const val RETRY_DELAY_MS = 20_000L
    }
}
