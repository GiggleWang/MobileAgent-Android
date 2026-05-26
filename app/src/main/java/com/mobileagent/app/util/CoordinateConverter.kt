package com.mobileagent.app.util

object CoordinateConverter {

    fun normalizedToAbsolute(
        x: Int, y: Int,
        screenWidth: Int, screenHeight: Int
    ): Pair<Int, Int> {
        val absX = (x * screenWidth) / 1000
        val absY = (y * screenHeight) / 1000
        return Pair(absX, absY)
    }

    fun convertAction(
        action: com.mobileagent.app.agent.AgentAction,
        screenWidth: Int,
        screenHeight: Int
    ): com.mobileagent.app.agent.AgentAction {
        return when (action) {
            is com.mobileagent.app.agent.AgentAction.Click -> {
                val (ax, ay) = normalizedToAbsolute(action.x, action.y, screenWidth, screenHeight)
                com.mobileagent.app.agent.AgentAction.Click(ax, ay)
            }
            is com.mobileagent.app.agent.AgentAction.LongPress -> {
                val (ax, ay) = normalizedToAbsolute(action.x, action.y, screenWidth, screenHeight)
                com.mobileagent.app.agent.AgentAction.LongPress(ax, ay)
            }
            is com.mobileagent.app.agent.AgentAction.Swipe -> {
                val (ax1, ay1) = normalizedToAbsolute(action.x1, action.y1, screenWidth, screenHeight)
                val (ax2, ay2) = normalizedToAbsolute(action.x2, action.y2, screenWidth, screenHeight)
                com.mobileagent.app.agent.AgentAction.Swipe(ax1, ay1, ax2, ay2)
            }
            else -> action
        }
    }
}
