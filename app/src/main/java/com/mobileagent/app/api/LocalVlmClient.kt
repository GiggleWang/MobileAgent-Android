package com.mobileagent.app.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.mobileagent.app.data.ModelDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * On-device VLM client — the "自带模型 (built-in)" path. Runs MiniCPM-V 4.6 fully on
 * the phone via llama.cpp + libmtmd ([LlamaVlmEngine]); no network, no API key.
 *
 * The model files (base GGUF + mmproj) live in [ModelDownloadManager.modelsDir] and
 * are fetched by the in-app download button. The native engine is loaded lazily on
 * first call and cached for the client's lifetime; [close] frees it (called when the
 * agent loop stops — see AgentForegroundService).
 *
 * Gemma-class pixel grounding isn't this model's strength, but the agent feeds the
 * accessibility element list with real coordinates (AgentLoop), so the model selects
 * from those rather than grounding from raw pixels.
 *
 * Calls are serialized with a [Mutex] (one native context, not concurrency-safe).
 */
class LocalVlmClient(
    private val config: ApiConfig,
    private val context: Context
) : VlmApiClient {

    private val engine = LlamaVlmEngine()
    private val mutex = Mutex()
    @Volatile private var ready = false

    private fun ensureEngine(): Boolean {
        if (ready) return true
        val entry = ModelManifest.findById(config.localModelId)
        if (entry == null) {
            Log.e(TAG, "unknown local model id: ${config.localModelId}")
            return false
        }
        val dir = ModelDownloadManager.modelsDir(context)
        val base = File(dir, entry.base.fileName)
        val mmproj = File(dir, entry.mmproj.fileName)
        if (!base.exists() || !mmproj.exists()) {
            Log.e(TAG, "model files missing for ${entry.id} (base=${base.exists()}, mmproj=${mmproj.exists()})")
            return false
        }
        ready = engine.load(base.absolutePath, mmproj.absolutePath, threadCount(), N_CTX)
        if (!ready) Log.e(TAG, "native engine failed to load: ${entry.id}")
        return ready
    }

    override suspend fun predictWithImages(
        systemPrompt: String?,
        textPrompt: String,
        images: List<Bitmap>
    ): Result<String> = withContext(Dispatchers.Default) {
        mutex.withLock {
            runCatching {
                if (!ensureEngine()) {
                    error("On-device model not ready. Download \"${config.localModelId}\" in Settings.")
                }
                val prompt = if (systemPrompt.isNullOrBlank()) textPrompt
                else "$systemPrompt\n\n$textPrompt"
                val jpegs = images.map { encodeJpeg(it) }.toTypedArray()
                val out = engine.predict(prompt, jpegs, MAX_TOKENS)
                if (out.isBlank()) error("Empty response from on-device model")
                out
            }.onFailure { Log.e(TAG, "local inference failed: ${it.message}", it) }
        }
    }

    /** Frees the cached native engine. Safe to call multiple times. */
    fun close() {
        engine.free()
        ready = false
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val resized = ImageEncoder.resizeForModel(bitmap)
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (resized !== bitmap) resized.recycle()
        return out.toByteArray()
    }

    private fun threadCount(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    companion object {
        private const val TAG = "LocalVlmClient"
        private const val N_CTX = 8192
        private const val MAX_TOKENS = 1024
        private const val JPEG_QUALITY = 85
    }
}
