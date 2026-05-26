package com.mobileagent.app.agent

class ActionReflector : BaseAgent {

    var lastOutcome: String = ""
        private set
    var lastErrorDescription: String = ""
        private set

    override fun getPrompt(infoPool: InfoPool): String = buildString {
        appendLine("You are a reflection agent that evaluates whether the last action was successful.")
        appendLine("The user's request is: ${infoPool.instruction}")
        appendLine()
        if (infoPool.progressStatus.isNotEmpty()) {
            appendLine("Progress status: ${infoPool.progressStatus}")
            appendLine()
        }
        appendLine("The last action performed was: ${infoPool.lastAction}")
        appendLine("The expected behavior was: ${infoPool.lastSummary}")
        appendLine()
        appendLine("You are given two screenshots: the first is BEFORE the action, the second is AFTER the action.")
        appendLine("Compare them carefully to determine the outcome.")
        appendLine()
        appendLine("For swiping/scrolling actions: if the content before and after is identical (same text, same elements), the outcome is C (no changes).")
        appendLine()
        appendLine(PromptConstants.REFLECTOR_OUTPUT_FORMAT)
    }

    override fun parseResponse(response: String, infoPool: InfoPool) {
        val outcomeMatch = Regex("###\\s*Outcome\\s*###\\s*\\n\\s*([ABC])").find(response)
        lastOutcome = outcomeMatch?.groupValues?.get(1) ?: "C"

        val errorMatch = Regex("###\\s*Error Description\\s*###\\s*\\n([\\s\\S]*?)$").find(response)
        lastErrorDescription = errorMatch?.groupValues?.get(1)?.trim() ?: "None"
    }
}
