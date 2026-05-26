package com.mobileagent.app.agent

interface BaseAgent {
    fun getPrompt(infoPool: InfoPool): String
    fun parseResponse(response: String, infoPool: InfoPool)
}
