package com.mobileagent.app.agent

data class InfoPool(
    var instruction: String = "",
    var additionalKnowledgeManager: String = "",
    var additionalKnowledgeExecutor: String = "",
    val summaryHistory: MutableList<String> = mutableListOf(),
    val actionHistory: MutableList<Map<String, Any?>> = mutableListOf(),
    val actionOutcomes: MutableList<String> = mutableListOf(),
    val errorDescriptions: MutableList<String> = mutableListOf(),
    var lastSummary: String = "",
    var lastAction: Map<String, Any?>? = null,
    var lastActionThought: String = "",
    var importantNotes: String = "",
    var errorFlagPlan: Boolean = false,
    var plan: String = "",
    var completedPlan: String = "",
    var progressStatus: String = "",
    val progressStatusHistory: MutableList<String> = mutableListOf(),
    var currentSubgoal: String = "",
    var errToManagerThresh: Int = 2,
    var finishThought: String = ""
)
