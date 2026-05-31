package com.mobileagent.app.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiConfig(
    val provider: String = "openai",
    val endpoint: String = "",
    val apiKey: String = "",
    val model: String = "",
    val coordType: String = "absolute",
    val maxSteps: Int = 25,
    val enableNotetaker: Boolean = true,
    val agentMode: Int = 1,
    val localModelId: String = "qwen3-vl-2b"
)
