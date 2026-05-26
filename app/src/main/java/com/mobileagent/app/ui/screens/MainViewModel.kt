package com.mobileagent.app.ui.screens

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobileagent.app.MainActivity
import com.mobileagent.app.agent.AgentEventBus
import com.mobileagent.app.util.PermissionChecker
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    val instruction = mutableStateOf("")
    val isRunning = mutableStateOf(false)
    val logs = mutableStateListOf<StepLog>()
    val missingPermissions = mutableStateOf<List<PermissionChecker.Permission>>(emptyList())

    fun dismissPermissionDialog() {
        missingPermissions.value = emptyList()
    }

    init {
        viewModelScope.launch {
            AgentEventBus.events.collect { result ->
                logs.add(StepLog(
                    step = result.step,
                    phase = result.phase,
                    content = result.message,
                    promptSnippet = result.promptSnippet,
                    response = result.response,
                    durationMs = result.durationMs,
                    imageCount = result.imageCount
                ))

                if (result.phase in listOf("done", "finished", "answer", "error")) {
                    isRunning.value = false
                }
            }
        }
    }

    fun startTask() {
        if (instruction.value.isBlank() || isRunning.value) return
        val activity = MainActivity.instance ?: return

        val missing = PermissionChecker.getMissingCriticalPermissions(activity)
        if (missing.isNotEmpty()) {
            missingPermissions.value = missing
            return
        }

        isRunning.value = true
        logs.clear()
        activity.requestMediaProjectionAndStart(instruction.value)
    }

    fun stopTask() {
        isRunning.value = false
        val activity = MainActivity.instance ?: return
        activity.stopAgentService()
    }
}
