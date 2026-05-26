package com.mobileagent.app.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object AgentEventBus {
    private val _events = MutableSharedFlow<StepResult>(replay = 50, extraBufferCapacity = 200)
    val events: SharedFlow<StepResult> = _events

    suspend fun post(result: StepResult) {
        _events.emit(result)
    }
}
