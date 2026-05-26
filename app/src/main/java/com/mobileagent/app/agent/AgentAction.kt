package com.mobileagent.app.agent

sealed class AgentAction {
    data class Click(val x: Int, val y: Int) : AgentAction()
    data class LongPress(val x: Int, val y: Int) : AgentAction()
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int) : AgentAction()
    data class Type(val text: String) : AgentAction()
    data class SystemButton(val button: ButtonType) : AgentAction()
    data class Answer(val text: String) : AgentAction()
    data object Wait : AgentAction()
    data object Finished : AgentAction()
    data object Invalid : AgentAction()

    enum class ButtonType { Back, Home, Enter }

    fun toMap(): Map<String, Any?> = when (this) {
        is Click -> mapOf("action" to "click", "coordinate" to listOf(x, y))
        is LongPress -> mapOf("action" to "long_press", "coordinate" to listOf(x, y))
        is Swipe -> mapOf(
            "action" to "swipe",
            "coordinate" to listOf(x1, y1),
            "coordinate2" to listOf(x2, y2)
        )
        is Type -> mapOf("action" to "type", "text" to text)
        is SystemButton -> mapOf("action" to "system_button", "button" to button.name.lowercase())
        is Answer -> mapOf("action" to "answer", "text" to text)
        is Wait -> mapOf("action" to "wait")
        is Finished -> mapOf("action" to "status", "status" to "finished")
        is Invalid -> mapOf("action" to "unknown")
    }

    companion object {
        const val ACTION_CLICK = "click"
        const val ACTION_LONG_PRESS = "long_press"
        const val ACTION_SWIPE = "swipe"
        const val ACTION_TYPE = "type"
        const val ACTION_SYSTEM_BUTTON = "system_button"
        const val ACTION_ANSWER = "answer"
        const val ACTION_WAIT = "wait"
        const val ACTION_STATUS = "status"
        const val ACTION_SCROLL = "scroll"
    }
}
