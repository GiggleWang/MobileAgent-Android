package com.mobileagent.app.data

import android.content.Context
import android.util.Log
import com.mobileagent.app.api.ModelManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * App-scoped singleton that manages downloading multiple on-device VLM model families
 * (see [ModelManifest]). Each model is tracked independently in [states].
 *
 * Downloads are resumable (HTTP Range), progress-tracked, and sha256-verified where a
 * hash is available. Callers observe [states] and call [start]/[cancel]/[delete] with
 * the target modelId.
 */
object ModelDownloadManager {

    sealed class State {
        data object NotDownloaded : State()
        data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : State()
        data object Verifying : State()
        data object Ready : State()
        data class Failed(val message: String) : State()
    }

    private const val TAG = "ModelDownloadManager"
    private const val PART_SUFFIX = ".part"

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = mutableMapOf<String, Job>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun modelsDir(context: Context): File =
        File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    private fun setState(modelId: String, state: State) {
        _states.value = _states.value.toMutableMap().also { it[modelId] = state }
    }

    fun stateOf(modelId: String): State = _states.value[modelId] ?: State.NotDownloaded

    fun isReady(context: Context, modelId: String): Boolean {
        val entry = ModelManifest.findById(modelId) ?: return false
        val dir = modelsDir(context)
        return entry.files.all { f ->
            val file = File(dir, f.fileName)
            file.exists() && file.length() == f.sizeBytes
        }
    }

    /** Refreshes disk state for all models. No-op for any model currently active. */
    fun refresh(context: Context) {
        ModelManifest.ALL.forEach { entry ->
            val id = entry.id
            if (_states.value[id] is State.Downloading || _states.value[id] is State.Verifying) return@forEach
            setState(id, if (isReady(context, id)) State.Ready else State.NotDownloaded)
        }
    }

    fun start(context: Context, modelId: String) {
        if (jobs[modelId]?.isActive == true) return
        val entry = ModelManifest.findById(modelId) ?: return
        jobs[modelId] = scope.launch {
            try {
                val dir = modelsDir(context)
                val total = entry.totalBytes
                var completedBytes = 0L
                for (f in entry.files) {
                    val target = File(dir, f.fileName)
                    if (target.exists() && target.length() == f.sizeBytes) {
                        completedBytes += f.sizeBytes
                        continue
                    }
                    downloadOne(modelId, f, target, completedBytes, total)
                    completedBytes += f.sizeBytes
                }

                setState(modelId, State.Verifying)
                for (f in entry.files) {
                    val expectedHash = f.sha256 ?: continue
                    val target = File(dir, f.fileName)
                    val actual = sha256(target)
                    if (!actual.equals(expectedHash, ignoreCase = true)) {
                        target.delete()
                        throw IllegalStateException("Checksum mismatch: ${f.fileName}")
                    }
                }
                setState(modelId, State.Ready)
            } catch (e: CancellationException) {
                setState(modelId, if (isReady(context, modelId)) State.Ready else State.NotDownloaded)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "download failed for $modelId", e)
                setState(modelId, State.Failed(e.message ?: "download failed"))
            }
        }
    }

    fun cancel(modelId: String) {
        jobs[modelId]?.cancel()
        jobs.remove(modelId)
    }

    fun delete(context: Context, modelId: String) {
        cancel(modelId)
        val entry = ModelManifest.findById(modelId) ?: return
        val dir = modelsDir(context)
        entry.files.forEach { f ->
            File(dir, f.fileName).delete()
            File(dir, f.fileName + PART_SUFFIX).delete()
        }
        setState(modelId, State.NotDownloaded)
    }

    private suspend fun downloadOne(
        modelId: String,
        f: ModelManifest.ModelFile,
        target: File,
        completedBytes: Long,
        grandTotal: Long
    ) {
        val part = File(target.parentFile, f.fileName + PART_SUFFIX)
        var have = if (part.exists()) part.length() else 0L

        val builder = Request.Builder().url(f.url)
        if (have > 0) builder.addHeader("Range", "bytes=$have-")

        client.newCall(builder.build()).execute().use { resp ->
            if (resp.code == 416 && have == f.sizeBytes) {
                // part is already complete — fall through to size check / rename
            } else if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code} for ${f.fileName}")
            }
            if (have > 0 && resp.code == 200) {
                have = 0L
                part.delete()
            }

            val body = resp.body
            if (body != null && resp.code != 416) {
                RandomAccessFile(part, "rw").use { sink ->
                    sink.seek(have)
                    body.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        var written = have
                        var lastTick = -1L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            written += n
                            val done = completedBytes + written
                            val tick = done * 200 / grandTotal
                            if (tick != lastTick) {
                                lastTick = tick
                                setState(modelId, State.Downloading(
                                    progress = done.toFloat() / grandTotal,
                                    downloadedBytes = done,
                                    totalBytes = grandTotal
                                ))
                            }
                        }
                    }
                }
            }
        }

        if (part.length() != f.sizeBytes) {
            throw IllegalStateException("size mismatch ${f.fileName}: ${part.length()} != ${f.sizeBytes}")
        }
        target.delete()
        if (!part.renameTo(target)) {
            throw IllegalStateException("rename failed for ${f.fileName}")
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
