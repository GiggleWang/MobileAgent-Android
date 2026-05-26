package com.mobileagent.app.agent

class Notetaker : BaseAgent {

    override fun getPrompt(infoPool: InfoPool): String = buildString {
        appendLine("You are a notetaker agent that extracts and records important information from the screen.")
        appendLine("The user's request is: ${infoPool.instruction}")
        appendLine()
        if (infoPool.progressStatus.isNotEmpty()) {
            appendLine("Progress status: ${infoPool.progressStatus}")
            appendLine()
        }
        if (infoPool.importantNotes.isNotEmpty()) {
            appendLine("Existing important notes:")
            appendLine(infoPool.importantNotes)
            appendLine()
        }
        if (infoPool.additionalKnowledgeExecutor.isNotEmpty()) {
            appendLine("Additional guidelines: ${infoPool.additionalKnowledgeExecutor}")
            appendLine()
        }
        appendLine("Based on the current screenshot, extract any important information relevant to the task.")
        appendLine("Only keep significant textual/visual information (codes, numbers, names, credentials, etc.).")
        appendLine("Do not take notes on low-level actions.")
        appendLine("Do not repeat the user request or progress status.")
        appendLine("Do not make up content you are not sure about.")
        appendLine("If nothing new is worth noting, copy the existing notes as-is.")
        appendLine()
        appendLine(PromptConstants.NOTETAKER_OUTPUT_FORMAT)
    }

    override fun parseResponse(response: String, infoPool: InfoPool) {
        val notesMatch = Regex("###\\s*Important Notes\\s*###\\s*\\n([\\s\\S]*?)$").find(response)
        val notes = notesMatch?.groupValues?.get(1)?.trim() ?: ""
        if (notes.isNotEmpty()) {
            infoPool.importantNotes = notes
        }
    }
}
