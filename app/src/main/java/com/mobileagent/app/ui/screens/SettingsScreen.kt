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
    val scope = rememberCoroutineScope()
    val settings by preferencesManager.settingsFlow.collectAsState(
        initial = PreferencesManager.Settings()
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.settings_saved)

    var provider by remember(settings) { mutableStateOf(settings.provider) }
    var endpoint by remember(settings) { mutableStateOf(settings.endpoint) }
    var apiKey by remember(settings) { mutableStateOf(settings.apiKey) }
    var model by remember(settings) { mutableStateOf(settings.model) }
    var coordType by remember(settings) { mutableStateOf(settings.coordType) }
    var maxSteps by remember(settings) { mutableStateOf(settings.maxSteps.toString()) }
    var enableNotetaker by remember(settings) { mutableStateOf(settings.enableNotetaker) }

    // Connection test state
    var testState by remember { mutableStateOf<TestState>(TestState.Idle) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
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

            Spacer(modifier = Modifier.height(24.dp))

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
            val providers = listOf("openai" to "OpenAI Compatible", "anthropic" to "Anthropic")
            providers.forEach { (value, label) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = provider == value,
                        onClick = { provider = value }
                    )
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text(stringResource(R.string.settings_endpoint)) },
                placeholder = { Text(stringResource(R.string.settings_endpoint_hint)) },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
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
                onValueChange = { model = it },
                label = { Text(stringResource(R.string.settings_model)) },
                placeholder = { Text(stringResource(R.string.settings_model_hint)) },
                leadingIcon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Test Connection Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        testState = TestState.Testing
                        scope.launch {
                            testState = testApiConnection(provider, endpoint, apiKey, model)
                        }
                    },
                    enabled = endpoint.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
                            && testState !is TestState.Testing,
                    colors = ButtonDefaults.buttonColors(containerColor = BtnTest)
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_test_connection))
                }

                if (testState is TestState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                if (testState is TestState.Success) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Green500, modifier = Modifier.size(28.dp))
                }
                if (testState is TestState.Failed) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = Red500, modifier = Modifier.size(28.dp))
                }
            }

            // Detail card
            val detail = when (val s = testState) {
                is TestState.Success -> s.detail
                is TestState.Failed -> s.detail
                else -> null
            }
            if (detail != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (testState is TestState.Success)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        DetailRow("URL", detail.requestUrl)
                        DetailRow("Request", detail.requestBody.take(200))
                        DetailRow("Status", "${detail.responseCode}")
                        DetailRow("Response", detail.responseBody.take(300))
                        if (detail.reply.isNotBlank()) {
                            DetailRow("Reply", detail.reply)
                        }
                        if (detail.error.isNotBlank()) {
                            DetailRow("Error", detail.error)
                        }
                        DetailRow("Time", "${detail.durationMs}ms")
                    }
                }
            }

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
                        onClick = { coordType = value }
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
                onValueChange = { maxSteps = it.filter { c -> c.isDigit() } },
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
                    onCheckedChange = { enableNotetaker = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    scope.launch {
                        preferencesManager.saveSettings(
                            PreferencesManager.Settings(
                                provider = provider,
                                endpoint = endpoint,
                                apiKey = apiKey,
                                model = model,
                                coordType = coordType,
                                maxSteps = maxSteps.toIntOrNull() ?: 25,
                                enableNotetaker = enableNotetaker
                            )
                        )
                        snackbarHostState.showSnackbar(savedMsg)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BtnSave)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_save))
            }
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
