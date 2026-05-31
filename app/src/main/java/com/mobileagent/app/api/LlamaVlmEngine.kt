package com.mobileagent.app.api

/**
 * Thin Kotlin facade over the native llama.cpp + libmtmd engine (libvlmjni.so).
 *
 * One instance owns one native handle (a cached llama_model + llama_context +
 * mtmd_context). Construction is cheap; [load] does the expensive model load and
 * must be called before [predict]. Not thread-safe — callers serialize access
 * (see LocalVlmClient's Mutex).
 */
class LlamaVlmEngine {

    private var handle: Long = 0L

    val isLoaded: Boolean get() = handle != 0L

    /** Loads the base GGUF + mmproj projector. Returns true on success. */
    fun load(modelPath: String, mmprojPath: String, nThreads: Int, nCtx: Int): Boolean {
        if (handle != 0L) return true
        ensureLibraryLoaded()
        handle = nativeInit(modelPath, mmprojPath, nThreads, nCtx)
        return handle != 0L
    }

    /** Runs one stateless prediction over a text prompt + JPEG/PNG-encoded images. */
    fun predict(prompt: String, images: Array<ByteArray>, maxTokens: Int): String {
        check(handle != 0L) { "LlamaVlmEngine not loaded" }
        return nativePredict(handle, prompt, images, maxTokens)
    }

    /** Result of a text-only latency benchmark. */
    data class BenchResult(val ttftMs: Double, val interTokenMs: Double, val tokens: Int) {
        /** Steady-state decode throughput, tokens/second. */
        val tokensPerSec: Double get() = if (interTokenMs > 0) 1000.0 / interTokenMs else 0.0
    }

    /**
     * Runs a text-only generation and reports time-to-first-token and average inter-token
     * latency. Stateless (clears KV first). Use prompts of varying length to see how TTFT
     * scales with input size.
     */
    fun benchmark(prompt: String, maxTokens: Int): BenchResult {
        check(handle != 0L) { "LlamaVlmEngine not loaded" }
        val r = nativeBenchmark(handle, prompt, maxTokens) // [ttftMicros, decodeMicros, nTokens]
        val ttftMs = if (r.isNotEmpty()) r[0] / 1000.0 else 0.0
        val nTok = if (r.size >= 3) r[2].toInt() else 0
        val interTokenMs = if (nTok > 1) (r[1] / 1000.0) / (nTok - 1) else 0.0
        return BenchResult(ttftMs, interTokenMs, nTok)
    }

    /** Frees the native handle. Safe to call multiple times. */
    fun free() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    private external fun nativeInit(modelPath: String, mmprojPath: String, nThreads: Int, nCtx: Int): Long
    private external fun nativePredict(handle: Long, prompt: String, images: Array<ByteArray>, maxTokens: Int): String
    private external fun nativeBenchmark(handle: Long, prompt: String, maxTokens: Int): LongArray
    private external fun nativeFree(handle: Long)

    companion object {
        @Volatile private var libraryLoaded = false

        private fun ensureLibraryLoaded() {
            if (libraryLoaded) return
            synchronized(this) {
                if (!libraryLoaded) {
                    System.loadLibrary("vlmjni")
                    libraryLoaded = true
                }
            }
        }
    }
}
