package com.mobileagent.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mobileagent.app.MobileAgentApp
import com.mobileagent.app.R
import com.mobileagent.app.agent.AgentEventBus
import com.mobileagent.app.agent.AgentLoop
import com.mobileagent.app.agent.StepResult
import com.mobileagent.app.api.*
import com.mobileagent.app.controller.AccessibilityController
import com.mobileagent.app.controller.ScreenCaptureManager
import com.mobileagent.app.data.PreferencesManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AgentForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.mobileagent.ACTION_START"
        const val ACTION_STOP = "com.mobileagent.ACTION_STOP"
        const val EXTRA_INSTRUCTION = "instruction"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val TAG = "AgentService"

        var currentLoop: AgentLoop? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var mediaProjection: MediaProjection? = null
    private var floatingWindow: FloatingWindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "ACTION_START received")
                val instruction = intent.getStringExtra(EXTRA_INSTRUCTION) ?: return START_NOT_STICKY
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

                Log.d(TAG, "instruction=$instruction, resultCode=$resultCode, resultData=$resultData")

                if (resultData == null) {
                    Log.e(TAG, "No MediaProjection result data")
                    serviceScope.launch {
                        AgentEventBus.post(StepResult(-1, "error", "MediaProjection not authorized"))
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Log.d(TAG, "startForeground with MEDIA_PROJECTION type")
                        startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    } else {
                        startForeground(1, createNotification())
                    }
                    Log.d(TAG, "startForeground OK")

                    val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
                    Log.d(TAG, "getMediaProjection OK: $mediaProjection")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to init: ${e.message}", e)
                    serviceScope.launch {
                        AgentEventBus.post(StepResult(-1, "error", "Init failed: ${e.message}"))
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Show floating status window
                if (android.provider.Settings.canDrawOverlays(this)) {
                    floatingWindow = FloatingWindowManager(this).apply {
                        onDismissRequest = {
                            floatingWindow = null
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                    floatingWindow?.show()
                }

                serviceScope.launch {
                    // Wait for consent dialog to dismiss
                    delay(3000)
                    startAgentLoop(instruction)
                }
            }
            ACTION_STOP -> {
                currentLoop?.stop()
                currentLoop = null
                screenCaptureManager?.release()
                screenCaptureManager = null
                floatingWindow?.dismiss()
                floatingWindow = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startAgentLoop(instruction: String) {
        try {
            Log.d(TAG, "startAgentLoop: instruction=$instruction")
            val projection = mediaProjection
            if (projection == null) {
                Log.e(TAG, "MediaProjection is null")
                AgentEventBus.post(StepResult(-1, "error", "MediaProjection not available"))
                stopSelf()
                return
            }

            Log.d(TAG, "Initializing ScreenCaptureManager")
            screenCaptureManager = ScreenCaptureManager(this).also {
                it.initialize(projection)
            }
            Log.d(TAG, "ScreenCaptureManager initialized")

            val prefs = PreferencesManager(this)
            val settings = prefs.settingsFlow.first()

            val apiConfig = ApiConfig(
                provider = settings.provider,
                endpoint = settings.endpoint,
                apiKey = settings.apiKey,
                model = settings.model,
                coordType = settings.coordType,
                maxSteps = settings.maxSteps,
                enableNotetaker = settings.enableNotetaker,
                agentMode = settings.agentMode
            )

            if (apiConfig.endpoint.isBlank() || apiConfig.apiKey.isBlank()) {
                AgentEventBus.post(StepResult(-1, "error", "API not configured. Go to Settings."))
                stopSelf()
                return
            }

            val apiClient: VlmApiClient = when (apiConfig.provider) {
                "anthropic" -> AnthropicClient(apiConfig)
                else -> OpenAiCompatibleClient(apiConfig)
            }

            val controller = AccessibilityController(this, screenCaptureManager!!)

            if (!AgentAccessibilityService.isRunning()) {
                AgentEventBus.post(StepResult(-1, "error", "Accessibility Service not enabled. Go to Permissions."))
                stopSelf()
                return
            }

            val loop = AgentLoop(controller, apiClient, apiConfig)
            currentLoop = loop

            serviceScope.launch {
                loop.stepResults.collect { result ->
                    AgentEventBus.post(result)
                    floatingWindow?.update(result.step, result.phase, result.message)
                    if (result.phase == "done" || result.phase == "finished" || result.phase == "answer") {
                        currentLoop = null
                    }
                }
            }

            loop.start(instruction, serviceScope)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start agent loop", e)
            AgentEventBus.post(StepResult(-1, "error", "Error: ${e.message}"))
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, MobileAgentApp.CHANNEL_ID)
            .setContentTitle("MobileAgent")
            .setContentText("Agent is running…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        currentLoop?.stop()
        currentLoop = null
        screenCaptureManager?.release()
        floatingWindow?.dismiss()
        floatingWindow = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
