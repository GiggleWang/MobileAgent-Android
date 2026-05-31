package com.mobileagent.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mobileagent.app.R
import com.mobileagent.app.api.*
import com.mobileagent.app.data.ModelDownloadManager
import com.mobileagent.app.data.PreferencesManager
import com.mobileagent.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(preferencesManager: PreferencesManager) {
    // Wait for the first real load so the editor is seeded exactly once; this prevents
    // re-seeding from the flow on every auto-save (which would reset text-field cursors).
    val loaded by preferencesManager.settingsFlow.collectAsState(initial = null)
    val initial = loaded
    if (initial == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    SettingsContent(preferencesManager, initial)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    preferencesManager: PreferencesManager,
    initial: PreferencesManager.Settings
) {
    val scope = rememberCoroutineScope()

    // Seeded ONCE from the first load; thereafter the UI is the source of truth and every
    // change is persisted immediately — there is no Save button.
    var provider by remember { mutableStateOf(initial.provider) }
    var endpoint by remember { mutableStateOf(initial.endpoint) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var coordType by remember { mutableStateOf(initial.coordType) }
    var maxSteps by remember { mutableStateOf(initial.maxSteps.toString()) }
    var enableNotetaker by remember { mutableStateOf(initial.enableNotetaker) }
    var agentMode by remember { mutableStateOf(initial.agentMode) }
    var localModelId by remember { mutableStateOf(initial.localModelId) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val currentLang = com.mobileagent.app.ui.LocaleManager.current

    // Cloud latency-benchmark state
    var cloudBenchRunning by remember { mutableStateOf(false) }
    var cloudBenchResults by remember { mutableStateOf<List<LlamaVlmEngine.BenchResult>?>(null) }
    var cloudBenchError by remember { mutableStateOf<String?>(null) }

    // Persist all settings. Called after every change so there's no explicit Save.
    fun save() {
        scope.launch {
            preferencesManager.saveSettings(
                PreferencesManager.Settings(
                    provider = provider,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    model = model,
                    coordType = coordType,
                    maxSteps = maxSteps.toIntOrNull() ?: 25,
                    enableNotetaker = enableNotetaker,
                    agentMode = agentMode,
                    language = initial.language,
                    localModelId = localModelId
                )
            )
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.settings_autosave_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Language selection
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val languages = listOf(
                "system" to stringResource(R.string.settings_lang_system),
                "en" to "English",
                "zh-CN" to "中文"
            )
            languages.forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentLang == value,
                        onClick = {
                            com.mobileagent.app.ui.LocaleManager.setLocale(context, value)
                        }
                    )
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Provider selection
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_provider),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val providers = listOf(
                "openai" to "OpenAI Compatible",
                "anthropic" to "Anthropic",
                "local" to stringResource(R.string.settings_provider_local)
            )
            providers.forEach { (value, label) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = provider == value,
                        onClick = { provider = value; save() }
                    )
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (provider == "local") {
                LocalModelsSection(
                    context = context,
                    scope = scope,
                    selectedModelId = localModelId,
                    onModelSelect = { id -> localModelId = id; save() }
                )
            } else {

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it; save() },
                label = { Text(stringResource(R.string.settings_endpoint)) },
                placeholder = { Text(stringResource(R.string.settings_endpoint_hint)) },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; save() },
                label = { Text(stringResource(R.string.settings_api_key)) },
                placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = model,
                onValueChange = { model = it; save() },
                label = { Text(stringResource(R.string.settings_model)) },
                placeholder = { Text(stringResource(R.string.settings_model_hint)) },
                leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Latency benchmark (short/medium/long) over a streaming response.
            Button(
                onClick = {
                    cloudBenchRunning = true; cloudBenchError = null; cloudBenchResults = null
                    scope.launch {
                        try {
                            cloudBenchResults = runCloudBenchmark(provider, endpoint, apiKey, model)
                        } catch (e: Exception) {
                            cloudBenchError = e.message ?: "benchmark failed"
                        } finally {
                            cloudBenchRunning = false
                        }
                    }
                },
                enabled = endpoint.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank() && !cloudBenchRunning,
                colors = ButtonDefaults.buttonColors(containerColor = BtnTest)
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_model_benchmark))
            }
            if (cloudBenchRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_benchmarking), style = MaterialTheme.typography.bodySmall)
                }
            }
            cloudBenchError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${stringResource(R.string.settings_model_benchmark_failed)}: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            cloudBenchResults?.let { results ->
                Spacer(modifier = Modifier.height(8.dp))
                BenchmarkResults(results)
            }
            } // end provider != "local"

            Spacer(modifier = Modifier.height(16.dp))

            // Coordinate type
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_coordinate_type),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            val coordTypes = listOf(
                "absolute" to stringResource(R.string.settings_coord_absolute),
                "normalized" to stringResource(R.string.settings_coord_normalized)
            )
            coordTypes.forEach { (value, label) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = coordType == value,
                        onClick = { coordType = value; save() }
                    )
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = maxSteps,
                onValueChange = { maxSteps = it.filter { c -> c.isDigit() }; save() },
                label = { Text(stringResource(R.string.settings_max_steps)) },
                leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NoteAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_enable_notetaker),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Switch(
                    checked = enableNotetaker,
                    onCheckedChange = { enableNotetaker = it; save() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Agent mode slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_agent_mode),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val modeLabels = listOf(
                stringResource(R.string.settings_mode_fast),
                stringResource(R.string.settings_mode_balanced),
                stringResource(R.string.settings_mode_accurate)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                modeLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Slider(
                value = agentMode.toFloat(),
                onValueChange = { agentMode = it.toInt() },
                onValueChangeFinished = { save() },
                valueRange = 0f..2f,
                steps = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

sealed class TestState {
    data object Idle : TestState()
    data object Testing : TestState()
    data class Success(val detail: TestDetail) : TestState()
    data class Failed(val detail: TestDetail) : TestState()
}

data class TestDetail(
    val requestUrl: String = "",
    val requestBody: String = "",
    val responseCode: Int = 0,
    val responseBody: String = "",
    val reply: String = "",
    val error: String = "",
    val durationMs: Long = 0
)

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 5
        )
    }
}


private fun formatMb(bytes: Long): String = "%.0f MB".format(bytes / 1024.0 / 1024.0)

// ── Multi-model local section ────────────────────────────────────────────────

@Composable
private fun LocalModelsSection(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    selectedModelId: String,
    onModelSelect: (String) -> Unit
) {
    val downloadStates by ModelDownloadManager.states.collectAsState()
    LaunchedEffect(Unit) { ModelDownloadManager.refresh(context) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_model_section),
                style = MaterialTheme.typography.titleSmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_model_select_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        com.mobileagent.app.api.ModelManifest.ALL.forEach { entry ->
            val state = downloadStates[entry.id] ?: ModelDownloadManager.State.NotDownloaded
            LocalModelCard(
                entry = entry,
                state = state,
                isSelected = selectedModelId == entry.id,
                onSelect = { onModelSelect(entry.id) },
                context = context,
                scope = scope
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LocalModelCard(
    entry: com.mobileagent.app.api.ModelManifest.ModelEntry,
    state: ModelDownloadManager.State,
    isSelected: Boolean,
    onSelect: () -> Unit,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope
) {
    var benchRunning by remember { mutableStateOf(false) }
    var benchResults by remember { mutableStateOf<List<LlamaVlmEngine.BenchResult>?>(null) }
    var benchError by remember { mutableStateOf<String?>(null) }
    val isReady = state is ModelDownloadManager.State.Ready

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected && isReady)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: name + badges + size
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (entry.recommended) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiary
                    ) {
                        Text(
                            text = stringResource(R.string.settings_model_recommended),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = entry.sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // State-specific UI
            when (state) {
                is ModelDownloadManager.State.NotDownloaded -> {
                    Button(
                        onClick = { ModelDownloadManager.start(context, entry.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_model_download))
                    }
                }
                is ModelDownloadManager.State.Downloading -> {
                    val pct = (state.progress * 100).toInt()
                    Text(
                        "${stringResource(R.string.settings_model_downloading)}  $pct%  " +
                                "(${formatMb(state.downloadedBytes)} / ${formatMb(state.totalBytes)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { ModelDownloadManager.cancel(entry.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_model_cancel))
                    }
                }
                is ModelDownloadManager.State.Verifying -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_model_verifying))
                    }
                }
                is ModelDownloadManager.State.Ready -> {
                    // "In use" or "Use this model" row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = onSelect)
                        Text(
                            text = if (isSelected)
                                stringResource(R.string.settings_model_in_use)
                            else
                                stringResource(R.string.settings_model_use),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp)
                        )
                        Row {
                            OutlinedButton(
                                onClick = {
                                    benchRunning = true; benchError = null; benchResults = null
                                    scope.launch {
                                        try {
                                            benchResults = runLocalBenchmark(context, entry)
                                        } catch (e: Exception) {
                                            benchError = e.message ?: "benchmark failed"
                                        } finally {
                                            benchRunning = false
                                        }
                                    }
                                },
                                enabled = !benchRunning,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_model_benchmark), style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { ModelDownloadManager.delete(context, entry.id) },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.settings_model_delete), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (benchRunning) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.settings_model_benchmarking), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    benchError?.let {
                        Text(
                            "${stringResource(R.string.settings_model_benchmark_failed)}: $it",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    benchResults?.let { BenchmarkResults(it) }
                }
                is ModelDownloadManager.State.Failed -> {
                    Text(
                        "${stringResource(R.string.settings_model_failed)}: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { ModelDownloadManager.start(context, entry.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_model_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun BenchmarkResults(results: List<LlamaVlmEngine.BenchResult>) {
    val labels = listOf(
        stringResource(R.string.bench_short),
        stringResource(R.string.bench_medium),
        stringResource(R.string.bench_long)
    )
    val ttftLabel = stringResource(R.string.bench_ttft)
    val itlLabel = stringResource(R.string.bench_itl)
    Column {
        Text(stringResource(R.string.bench_header), style = MaterialTheme.typography.labelMedium)
        results.forEachIndexed { i, r ->
            val label = labels.getOrElse(i) { "#$i" }
            Text(
                text = "$label · $ttftLabel ${"%.0f".format(r.ttftMs)} ms · " +
                        "$itlLabel ${"%.1f".format(r.interTokenMs)} ms (${"%.1f".format(r.tokensPerSec)} tok/s)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private const val BENCH_SENTENCE = "The quick brown fox jumps over the lazy dog. "
private val BENCH_REPEATS = listOf(2, 24, 96) // short / medium / long input lengths

private fun benchPrompt(repeat: Int): String =
    BENCH_SENTENCE.repeat(repeat) + "\nSummarize the text above in one sentence."

/** Loads a temporary engine for [entry], benchmarks short/medium/long prompts, then frees it. */
private suspend fun runLocalBenchmark(
    context: android.content.Context,
    entry: com.mobileagent.app.api.ModelManifest.ModelEntry
): List<LlamaVlmEngine.BenchResult> =
    withContext(Dispatchers.Default) {
        val dir = ModelDownloadManager.modelsDir(context)
        val base = java.io.File(dir, entry.base.fileName)
        val mmproj = java.io.File(dir, entry.mmproj.fileName)
        val engine = LlamaVlmEngine()
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        if (!engine.load(base.absolutePath, mmproj.absolutePath, threads, 8192)) {
            error("Failed to load on-device model: ${entry.id}")
        }
        try {
            engine.benchmark(benchPrompt(1), 4) // warm-up (discarded)
            BENCH_REPEATS.map { engine.benchmark(benchPrompt(it), 32) }
        } finally {
            engine.free()
        }
    }

/** Benchmarks short/medium/long prompts against a cloud provider over a streaming response. */
private suspend fun runCloudBenchmark(
    provider: String,
    endpoint: String,
    apiKey: String,
    model: String
): List<LlamaVlmEngine.BenchResult> = withContext(Dispatchers.IO) {
    BENCH_REPEATS.map { rep -> streamOnce(provider, endpoint, apiKey, model, benchPrompt(rep), 32) }
}

/** One streaming request; times first-token (TTFT) and average inter-token latency. */
private fun streamOnce(
    provider: String,
    endpoint: String,
    apiKey: String,
    model: String,
    prompt: String,
    maxTokens: Int
): LlamaVlmEngine.BenchResult {
    val isAnthropic = provider == "anthropic"
    val ep = endpoint.trimEnd('/')
    val url = if (isAnthropic) {
        if (ep.endsWith("/messages")) ep else "$ep/messages"
    } else {
        if (ep.endsWith("/chat/completions")) ep else "$ep/chat/completions"
    }
    val bodyJson = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("stream", true)
        put("messages", buildJsonArray {
            addJsonObject { put("role", "user"); put("content", prompt) }
        })
    }
    val builder = Request.Builder().url(url)
        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
    if (isAnthropic) {
        builder.addHeader("x-api-key", apiKey)
        builder.addHeader("anthropic-version", "2023-06-01")
    } else {
        builder.addHeader("Authorization", "Bearer $apiKey")
    }
    builder.addHeader("Content-Type", "application/json")

    val json = Json { ignoreUnknownKeys = true }
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val start = System.nanoTime()
    var ttftNs = 0L
    var lastNs = 0L
    var interNs = 0L
    var chunks = 0

    client.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) {
            val err = resp.body?.string()?.take(300).orEmpty()
            throw IllegalStateException("HTTP ${resp.code}${if (err.isNotBlank()) ": $err" else ""}")
        }
        val source = resp.body?.source() ?: throw IllegalStateException("empty response")
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue          // skip SSE "event:"/blank lines
            val data = line.substring(5).trim()
            if (data.isEmpty()) continue
            if (data == "[DONE]") break
            val text = extractDeltaText(json, data, isAnthropic)
            if (text.isNullOrEmpty()) continue                // skip role/ping/empty deltas
            val now = System.nanoTime()
            if (chunks == 0) ttftNs = now - start else interNs += now - lastNs
            lastNs = now
            chunks++
        }
    }
    val ttftMs = ttftNs / 1_000_000.0
    val interMs = if (chunks > 1) (interNs / 1_000_000.0) / (chunks - 1) else 0.0
    return LlamaVlmEngine.BenchResult(ttftMs, interMs, chunks)
}

/** Pulls the incremental text from one SSE data line (OpenAI delta.content / Anthropic delta.text). */
private fun extractDeltaText(json: Json, data: String, isAnthropic: Boolean): String? = try {
    val obj = json.parseToJsonElement(data).jsonObject
    if (isAnthropic) {
        obj["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
    } else {
        obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
    }
} catch (e: Exception) {
    null
}

private suspend fun testApiConnection(
    provider: String,
    endpoint: String,
    apiKey: String,
    model: String
): TestState = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()
    var requestUrl = ""
    var requestBodyStr = ""

    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val (url, body, headers) = if (provider == "anthropic") {
            val reqBody = buildJsonObject {
                put("model", model)
                put("max_tokens", 64)
                put("messages", buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put("content", "Say hello in one word.")
                    }
                })
            }
            val ep = endpoint.trimEnd('/')
            val finalUrl = if (ep.endsWith("/messages")) ep else "$ep/messages"
            Triple(
                finalUrl,
                reqBody.toString(),
                mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01", "Content-Type" to "application/json")
            )
        } else {
            val reqBody = buildJsonObject {
                put("model", model)
                put("max_tokens", 64)
                put("messages", buildJsonArray {
                    addJsonObject {
                        put("role", "user")
                        put("content", "Say hello in one word.")
                    }
                })
            }
            val ep = endpoint.trimEnd('/')
            val finalUrl = if (ep.endsWith("/chat/completions")) ep else "$ep/chat/completions"
            Triple(
                finalUrl,
                reqBody.toString(),
                mapOf("Authorization" to "Bearer $apiKey", "Content-Type" to "application/json")
            )
        }

        requestUrl = url
        requestBodyStr = body

        val requestBuilder = Request.Builder().url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""
        val duration = System.currentTimeMillis() - startTime

        if (!response.isSuccessful) {
            return@withContext TestState.Failed(TestDetail(
                requestUrl = requestUrl,
                requestBody = requestBodyStr,
                responseCode = response.code,
                responseBody = responseBody,
                error = "HTTP ${response.code}",
                durationMs = duration
            ))
        }

        if (responseBody.isBlank()) {
            return@withContext TestState.Failed(TestDetail(
                requestUrl = requestUrl,
                requestBody = requestBodyStr,
                responseCode = response.code,
                responseBody = "(empty)",
                error = "Empty response body",
                durationMs = duration
            ))
        }

        val json = Json { ignoreUnknownKeys = true }
        val jsonObj = try {
            json.parseToJsonElement(responseBody).jsonObject
        } catch (e: Exception) {
            return@withContext TestState.Failed(TestDetail(
                requestUrl = requestUrl,
                requestBody = requestBodyStr,
                responseCode = response.code,
                responseBody = responseBody,
                error = "Not valid JSON",
                durationMs = duration
            ))
        }

        val reply = if (provider == "anthropic") {
            jsonObj["content"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: "OK"
        } else {
            jsonObj["choices"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")?.jsonPrimitive?.content ?: "OK"
        }

        TestState.Success(TestDetail(
            requestUrl = requestUrl,
            requestBody = requestBodyStr,
            responseCode = response.code,
            responseBody = responseBody,
            reply = reply,
            durationMs = duration
        ))
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        TestState.Failed(TestDetail(
            requestUrl = requestUrl,
            requestBody = requestBodyStr,
            error = "${e.javaClass.simpleName}: ${e.message}",
            durationMs = duration
        ))
    }
}
