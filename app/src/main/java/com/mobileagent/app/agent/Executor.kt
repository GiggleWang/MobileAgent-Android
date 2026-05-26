package com.mobileagent.app.agent

class Executor : BaseAgent {

    override fun getPrompt(infoPool: InfoPool): String = buildString {
        appendLine("You are a helpful agent that assists users with their smartphone tasks.")
        appendLine("The user's request is: ${infoPool.instruction}")
        appendLine()
        appendLine("Current plan:")
        appendLine(infoPool.plan)
        appendLine()
        appendLine("Current subgoal: ${infoPool.currentSubgoal}")
        appendLine()
        if (infoPool.progressStatus.isNotEmpty()) {
            appendLine("Progress status: ${infoPool.progressStatus}")
            appendLine()
        }

        val historySize = infoPool.actionHistory.size
        if (historySize > 0) {
            appendLine("Recent action history (last ${minOf(5, historySize)} actions):")
            val start = maxOf(0, historySize - 5)
            for (i in start until historySize) {
                appendLine("  Step ${i + 1}:")
                appendLine("    Action: ${infoPool.actionHistory[i]}")
                if (i < infoPool.summaryHistory.size) {
                    appendLine("    Description: ${infoPool.summaryHistory[i]}")
                }
                if (i < infoPool.actionOutcomes.size) {
                    appendLine("    Outcome: ${infoPool.actionOutcomes[i]}")
                }
            }
            appendLine()
        }

        if (infoPool.importantNotes.isNotEmpty()) {
            appendLine("Important notes:")
            appendLine(infoPool.importantNotes)
            appendLine()
        }

        appendLine(PromptConstants.INPUT_KNOW)
        appendLine()

        if (infoPool.additionalKnowledgeExecutor.isNotEmpty()) {
            appendLine("Additional information: ${infoPool.additionalKnowledgeExecutor}")
            appendLine()
        }

        appendLine(PromptConstants.ATOMIC_ACTION_SIGNATURES)
        appendLine()
        appendLine("Based on the current screenshot and the current subgoal, decide the next action to take.")
        appendLine("Do NOT repeat previously failed actions. Try a different approach if the same action failed before.")
        appendLine()
        appendLine(PromptConstants.EXECUTOR_OUTPUT_FORMAT)
    }

    override fun parseResponse(response: String, infoPool: InfoPool) {
        val thoughtMatch = Regex("###\\s*Thought\\s*###\\s*\\n([\\s\\S]*?)###\\s*Action").find(response)
        infoPool.lastActionThought = thoughtMatch?.groupValues?.get(1)?.trim() ?: ""

        val descMatch = Regex("###\\s*Description\\s*###\\s*\\n([\\s\\S]*?)$").find(response)
        infoPool.lastSummary = descMatch?.groupValues?.get(1)?.trim() ?: ""
    }
}
