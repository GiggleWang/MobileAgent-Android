package com.mobileagent.app.agent

class Manager : BaseAgent {

    override fun getPrompt(infoPool: InfoPool): String = buildString {
        if (infoPool.plan.isEmpty()) {
            appendLine("You are a helpful agent that assists users with their smartphone tasks.")
            appendLine("The user's request is: ${infoPool.instruction}")
            appendLine()
            appendLine("CRITICAL RULES:")
            appendLine("- You are running inside an app called MobileAgent. NEVER interact with MobileAgent's own UI (its Start/Stop buttons, instruction input field, execution logs, bottom navigation, or any element belonging to MobileAgent itself).")
            appendLine("- The FIRST subgoal of every plan MUST be: Press the Home button to go to the home screen, so that you start from a clean state.")
            appendLine("- Only interact with the target apps and system UI needed to complete the user's request.")
            appendLine()
            appendLine("Based on the current screenshot, please analyze the screen and create a plan to complete the user's request.")
            appendLine("Break the task into numbered subgoals. Each subgoal should be a clear, atomic step.")
            appendLine()
            if (infoPool.additionalKnowledgeManager.isNotEmpty()) {
                appendLine("Additional information: ${infoPool.additionalKnowledgeManager}")
                appendLine()
            }
            appendLine("Your output should strictly follow the format below:")
            appendLine()
            appendLine("### Thought ###")
            appendLine("[Your detailed analysis of the current screen and the task]")
            appendLine()
            appendLine("### Plan ###")
            appendLine("[Numbered list of subgoals to achieve the task]")
        } else {
            appendLine("You are a helpful agent that assists users with their smartphone tasks.")
            appendLine("The user's request is: ${infoPool.instruction}")
            appendLine()
            appendLine("CRITICAL: NEVER interact with MobileAgent's own UI (Start/Stop buttons, instruction field, logs, navigation). Only interact with the target apps needed to complete the user's request.")
            appendLine()
            appendLine("Current plan:")
            appendLine(infoPool.plan)
            appendLine()
            if (infoPool.completedPlan.isNotEmpty()) {
                appendLine("Completed subgoals:")
                appendLine(infoPool.completedPlan)
                appendLine()
            }
            if (infoPool.progressStatus.isNotEmpty()) {
                appendLine("Progress status: ${infoPool.progressStatus}")
                appendLine()
            }
            if (infoPool.lastSummary.isNotEmpty()) {
                appendLine("Last action: ${infoPool.lastSummary}")
                appendLine()
            }
            if (infoPool.importantNotes.isNotEmpty()) {
                appendLine("Important notes:")
                appendLine(infoPool.importantNotes)
                appendLine()
            }
            if (infoPool.errorFlagPlan) {
                appendLine("WARNING: The agent appears to be stuck. Recent actions have failed repeatedly.")
                appendLine("Recent action history:")
                val recentCount = minOf(infoPool.errToManagerThresh + 1, infoPool.actionHistory.size)
                for (i in infoPool.actionHistory.size - recentCount until infoPool.actionHistory.size) {
                    appendLine("  Action: ${infoPool.actionHistory[i]}")
                    if (i < infoPool.actionOutcomes.size) {
                        appendLine("  Outcome: ${infoPool.actionOutcomes[i]}")
                    }
                    if (i < infoPool.errorDescriptions.size) {
                        appendLine("  Error: ${infoPool.errorDescriptions[i]}")
                    }
                }
                appendLine()
                appendLine("Please revise the plan to try a different approach.")
                appendLine()
            }
            appendLine("Based on the current screenshot, please update the plan if needed.")
            appendLine("If the task is complete, write 'Finished' as the plan.")
            appendLine()
            appendLine("Your output should strictly follow the format below:")
            appendLine()
            appendLine("### Thought ###")
            appendLine("[Your detailed analysis]")
            appendLine()
            appendLine("### Plan ###")
            appendLine("[Updated numbered list or 'Finished']")
        }
    }

    override fun parseResponse(response: String, infoPool: InfoPool) {
        val planMatch = Regex("###\\s*Plan\\s*###\\s*\\n([\\s\\S]*?)$").find(response)
        val plan = planMatch?.groupValues?.get(1)?.trim() ?: ""

        val thoughtMatch = Regex("###\\s*Thought\\s*###\\s*\\n([\\s\\S]*?)###\\s*Plan").find(response)
        val thought = thoughtMatch?.groupValues?.get(1)?.trim() ?: ""

        if (plan.isNotEmpty()) {
            infoPool.plan = plan
        }
        infoPool.finishThought = thought

        val lines = plan.lines().filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            infoPool.currentSubgoal = lines.first().replace(Regex("^\\d+\\.?\\s*"), "")
        }
    }
}
