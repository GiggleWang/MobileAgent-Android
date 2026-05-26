package com.mobileagent.app

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.mobileagent.app.data.PreferencesManager
import com.mobileagent.app.service.AgentForegroundService
import com.mobileagent.app.ui.navigation.AppNavigation
import com.mobileagent.app.ui.theme.MobileAgentTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private var pendingInstruction: String? = null

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            projectionResultCode = result.resultCode
            projectionData = result.data
            pendingInstruction?.let { startAgentService(it) }
        }
        pendingInstruction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferencesManager = PreferencesManager(applicationContext)
        instance = this

        val initialRoute = intent?.getStringExtra("navigate_to")

        setContent {
            MobileAgentTheme {
                AppNavigation(preferencesManager, initialRoute = initialRoute)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingNavRoute = intent.getStringExtra("navigate_to")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    fun requestMediaProjectionAndStart(instruction: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            && projectionResultCode == Activity.RESULT_OK
            && projectionData != null
        ) {
            startAgentService(instruction)
            return
        }

        pendingInstruction = instruction
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    private fun startAgentService(instruction: String) {
        val intent = Intent(this, AgentForegroundService::class.java).apply {
            action = AgentForegroundService.ACTION_START
            putExtra(AgentForegroundService.EXTRA_INSTRUCTION, instruction)
            putExtra(AgentForegroundService.EXTRA_RESULT_CODE, projectionResultCode)
            putExtra(AgentForegroundService.EXTRA_RESULT_DATA, projectionData)
        }
        startForegroundService(intent)
    }

    fun stopAgentService() {
        val intent = Intent(this, AgentForegroundService::class.java).apply {
            action = AgentForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    companion object {
        var instance: MainActivity? = null
            private set
        var projectionResultCode: Int = 0
        var projectionData: Intent? = null
        var pendingNavRoute: String? = null
    }
}
