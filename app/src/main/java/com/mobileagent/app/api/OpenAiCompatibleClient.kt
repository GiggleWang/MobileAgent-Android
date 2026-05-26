package com.mobileagent.app.api

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClient(private val config: ApiConfig) : VlmApiClient {

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
                val result = doRequest(systemPrompt, textPrompt, images)
                return@withContext Result.success(result)
            } catch (e: Exception) {
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
        val messages = buildJsonArray {
            if (systemPrompt != null) {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
            }
            addJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    for (image in images) {
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,${ImageEncoder.encode(image)}")
                            }
                        }
                    }
                    addJsonObject {
                        put("type", "text")
                        put("text", textPrompt)
                    }
                })
            }
        }

        val requestBody = buildJsonObject {
            put("model", config.model)
            put("messages", messages)
            put("max_tokens", 4096)
        }

        val endpoint = config.endpoint.trimEnd('/')
        val url = if (endpoint.endsWith("/chat/completions")) endpoint
        else "$endpoint/chat/completions"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("Empty response body")

        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: $body")
        }

        val jsonResponse = json.parseToJsonElement(body).jsonObject
        return jsonResponse["choices"]!!.jsonArray[0]
            .jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
    }

    companion object {
        private const val MAX_RETRIES = 10
        private const val RETRY_DELAY_MS = 20_000L
    }
}
