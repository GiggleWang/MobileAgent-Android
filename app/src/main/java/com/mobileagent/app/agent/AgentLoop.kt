package com.mobileagent.app.agent

import android.graphics.Bitmap
import android.util.Log
import com.mobileagent.app.api.ApiConfig
import com.mobileagent.app.api.VlmApiClient
import com.mobileagent.app.controller.DeviceController
import com.mobileagent.app.util.CoordinateConverter
import com.mobileagent.app.util.JsonActionParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class StepResult(
    val step: Int,
    val phase: String,
    val message: String,
    val promptSnippet: String = "",
    val response: String = "",
    val durationMs: Long = 0,
    val imageCount: Int = 0
)

class AgentLoop(
    private val controller: DeviceController,
    private val apiClient: VlmApiClient,
    private val config: ApiConfig
) {
    private val manager = Manager()
    private val executor = Executor()
    private val reflector = ActionReflector()
    private val notetaker = Notetaker()

    private val _stepResults = MutableSharedFlow<StepResult>(extraBufferCapacity = 100)
    val stepResults: SharedFlow<StepResult> = _stepResults

    private var job: Job? = null

    fun start(instruction: String, scope: CoroutineScope) {
        job = scope.launch {
            runLoop(instruction)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    val isRunning: Boolean get() = job?.isActive == true

    private suspend fun callApi(
        step: Int,
        phase: String,
        prompt: String,
        images: List<Bitmap>
    ): String? {
        emit(StepResult(
            step = step,
            phase = phase,
            message = "Calling API…",
            promptSnippet = prompt.takeLast(300),
            imageCount = images.size
        ))

        val startTime = System.currentTimeMillis()
        val result = apiClient.predictWithImages(null, prompt, images)
        val duration = System.currentTimeMillis() - startTime

        if (result.isFailure) {
            emit(StepResult(
                step = step,
                phase = phase,
                message = "API FAILED: ${result.exceptionOrNull()?.message}",
                promptSnippet = prompt.takeLast(200),
                durationMs = duration,
                imageCount = images.size
            ))
            return null
        }

        val response = result.getOrThrow()
        emit(StepResult(
            step = step,
            phase = phase,
            message = "Got response",
            promptSnippet = prompt.takeLast(200),
            response = response,
            durationMs = duration,
            imageCount = images.size
        ))
        return response
    }

    private suspend fun runLoop(instruction: String) {
        val infoPool = InfoPool(instruction = instruction)
        val maxSteps = config.maxSteps
        val agentMode = config.agentMode // 0=fast, 1=balanced, 2=accurate
        val useNormalized = config.coordType == "normalized"
        val (screenWidth, screenHeight) = controller.getScreenSize()

        for (step in 0 until maxSteps) {
            if (!currentCoroutineContext().isActive) return

            emit(StepResult(step, "screenshot", "Capturing screenshot…"))

            val screenshotBefore = captureWithRetry()
            if (screenshotBefore == null) {
                emit(StepResult(step, "error", "Failed to capture screenshot"))
                return
            }
            emit(StepResult(step, "screenshot", "Screenshot captured (${screenshotBefore.width}x${screenshotBefore.height})"))

            // Detect UI elements and annotate screenshot
            val uiElements = com.mobileagent.app.controller.UiElementDetector.detectElements()
            val annotatedScreenshot = if (uiElements.isNotEmpty()) {
                com.mobileagent.app.controller.UiElementDetector.annotateScreenshot(screenshotBefore, uiElements)
            } else {
                screenshotBefore
            }
            val elementListText = com.mobileagent.app.controller.UiElementDetector.buildElementListText(uiElements)
            emit(StepResult(step, "screenshot", "Detected ${uiElements.size} UI elements"))

            checkErrorThreshold(infoPool)

            // Manager phase: frequency depends on agentMode
            val needsManager = when (agentMode) {
                0 -> step == 0 || infoPool.plan.isEmpty() || infoPool.errorFlagPlan
                1 -> step == 0 || infoPool.plan.isEmpty() || infoPool.errorFlagPlan ||
                        (infoPool.actionOutcomes.isNotEmpty() && infoPool.actionOutcomes.last() != "A")
                else -> true
            }
            if (needsManager) {
                val managerPrompt = manager.getPrompt(infoPool) +
                    if (elementListText.isNotBlank()) "\n\n$elementListText" else ""
                val managerResponse = callApi(step, "manager", managerPrompt, listOf(annotatedScreenshot))
                if (managerResponse == null) return

                manager.parseResponse(managerResponse, infoPool)

                val planTrimmed = infoPool.plan.trim()
                if (planTrimmed.startsWith("Finished", ignoreCase = true) && planTrimmed.length < 15) {
                    emit(StepResult(step, "finished", "Task completed: ${infoPool.finishThought}"))
                    return
                }
                infoPool.errorFlagPlan = false
            }

            // Executor phase
            val executorPrompt = executor.getPrompt(infoPool) +
                if (elementListText.isNotBlank()) "\n\n$elementListText\n\nIMPORTANT: The coordinates in the element list above are the ACTUAL screen coordinates. Use the center(x, y) values directly in your action. These are more accurate than estimating from the image. For example, if element [3] shows center(540, 1200), use {\"action\": \"click\", \"coordinate\": [540, 1200]}." else ""
            val executorResponse = callApi(step, "executor", executorPrompt, listOf(annotatedScreenshot))
            if (executorResponse == null) return

            executor.parseResponse(executorResponse, infoPool)

            var action = JsonActionParser.parseAction(executorResponse)
            val actionMap = JsonActionParser.extractActionMap(executorResponse)

            if (action is AgentAction.Finished) {
                emit(StepResult(step, "finished", "Task completed by executor"))
                return
            }
            if (action is AgentAction.Answer) {
                emit(StepResult(step, "answer", "Answer: ${(action as AgentAction.Answer).text}"))
                return
            }

            if (useNormalized && action !is AgentAction.Invalid) {
                action = CoordinateConverter.convertAction(action, screenWidth, screenHeight)
            } else if (!useNormalized && action !is AgentAction.Invalid) {
                // Check if coordinates match a known UI element (already screen coords)
                val matchesElement = isFromElementList(action, uiElements)
                if (!matchesElement) {
                    // Coordinates are from image space, scale to screen space
                    val (scaleX, scaleY) = com.mobileagent.app.api.ImageEncoder.getScaleFactors(screenshotBefore)
                    action = scaleAction(action, scaleX, scaleY)
                }
            }

            infoPool.lastAction = actionMap ?: action.toMap()
            infoPool.actionHistory.add(infoPool.lastAction!!)
            infoPool.summaryHistory.add(infoPool.lastSummary)

            // Execute action
            emit(StepResult(step, "execute", "Executing: ${infoPool.lastSummary} | Action: ${infoPool.lastAction}"))
            executeAction(action)

            val waitMs = if (step == 0) 8000L else 2000L
            delay(waitMs)

            // Skip reflector: fast mode skips for system buttons + simple clicks; balanced skips for system buttons only
            val skipReflector = when (agentMode) {
                0 -> action is AgentAction.SystemButton || action is AgentAction.Type
                1 -> action is AgentAction.SystemButton
                else -> false
            }
            if (skipReflector) {
                if (annotatedScreenshot !== screenshotBefore) {
                    annotatedScreenshot.recycle()
                }
                infoPool.actionOutcomes.add("A")
                infoPool.errorDescriptions.add("None")
                updateProgress(infoPool)
                screenshotBefore.recycle()
                continue
            }

            // Capture after screenshot
            val screenshotAfter = captureWithRetry()
            if (screenshotAfter == null) {
                emit(StepResult(step, "error", "Failed to capture screenshot after action"))
                return
            }

            // Reflector phase (use original screenshots for comparison, not annotated)
            val reflectorPrompt = reflector.getPrompt(infoPool)
            val reflectorResponse = callApi(step, "reflector", reflectorPrompt, listOf(screenshotBefore, screenshotAfter))
            if (annotatedScreenshot !== screenshotBefore) {
                annotatedScreenshot.recycle()
            }
            if (reflectorResponse == null) {
                infoPool.actionOutcomes.add("C")
                infoPool.errorDescriptions.add("API call failed")
                screenshotBefore.recycle()
                screenshotAfter.recycle()
                continue
            }
            reflector.parseResponse(reflectorResponse, infoPool)
            infoPool.actionOutcomes.add(reflector.lastOutcome)
            infoPool.errorDescriptions.add(reflector.lastErrorDescription)

            // Update progress
            if (reflector.lastOutcome == "A") {
                updateProgress(infoPool)
            }

            // Notetaker phase
            if (config.enableNotetaker && reflector.lastOutcome == "A") {
                val notetakerPrompt = notetaker.getPrompt(infoPool)
                callApi(step, "notetaker", notetakerPrompt, listOf(screenshotAfter))?.let {
                    notetaker.parseResponse(it, infoPool)
                }
            }

            screenshotBefore.recycle()
            screenshotAfter.recycle()
        }

        emit(StepResult(-1, "done", "Agent loop finished ($maxSteps steps reached)"))
    }

    private fun isFromElementList(
        action: AgentAction,
        elements: List<com.mobileagent.app.controller.UiElement>
    ): Boolean {
        val (x, y) = when (action) {
            is AgentAction.Click -> action.x to action.y
            is AgentAction.LongPress -> action.x to action.y
            else -> return false
        }
        // Check if the coordinate is close to any element's center or within its bounds
        return elements.any { el ->
            val cx = (el.bounds.left + el.bounds.right) / 2
            val cy = (el.bounds.top + el.bounds.bottom) / 2
            val tolerance = 30
            (Math.abs(x - cx) < tolerance && Math.abs(y - cy) < tolerance) ||
                    el.bounds.contains(x, y)
        }
    }

    private fun scaleAction(action: AgentAction, scaleX: Float, scaleY: Float): AgentAction {
        if (scaleX == 1f && scaleY == 1f) return action
        return when (action) {
            is AgentAction.Click -> AgentAction.Click(
                (action.x * scaleX).toInt(), (action.y * scaleY).toInt()
            )
            is AgentAction.LongPress -> AgentAction.LongPress(
                (action.x * scaleX).toInt(), (action.y * scaleY).toInt()
            )
            is AgentAction.Swipe -> AgentAction.Swipe(
                (action.x1 * scaleX).toInt(), (action.y1 * scaleY).toInt(),
                (action.x2 * scaleX).toInt(), (action.y2 * scaleY).toInt()
            )
            else -> action
        }
    }

    private suspend fun executeAction(action: AgentAction) {
        when (action) {
            is AgentAction.Click -> controller.tap(action.x, action.y)
            is AgentAction.LongPress -> controller.longPress(action.x, action.y)
            is AgentAction.Swipe -> controller.swipe(action.x1, action.y1, action.x2, action.y2)
            is AgentAction.Type -> controller.typeText(action.text)
            is AgentAction.SystemButton -> when (action.button) {
                AgentAction.ButtonType.Back -> controller.pressBack()
                AgentAction.ButtonType.Home -> controller.pressHome()
                AgentAction.ButtonType.Enter -> controller.pressEnter()
            }
            is AgentAction.Wait -> delay(2000)
            else -> Log.w(TAG, "Unhandled action: $action")
        }
    }

    private suspend fun captureWithRetry(maxRetries: Int = 5): Bitmap? {
        for (i in 0 until maxRetries) {
            val bitmap = controller.captureScreenshot()
            if (bitmap != null) return bitmap
            delay(500)
        }
        return null
    }

    private fun checkErrorThreshold(infoPool: InfoPool) {
        val outcomes = infoPool.actionOutcomes
        if (outcomes.size >= infoPool.errToManagerThresh) {
            val recent = outcomes.takeLast(infoPool.errToManagerThresh)
            if (recent.all { it == "B" || it == "C" }) {
                infoPool.errorFlagPlan = true
            }
        }
    }

    private fun updateProgress(infoPool: InfoPool) {
        val plan = infoPool.plan
        val lines = plan.lines().filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            val completed = lines.first()
            infoPool.completedPlan += "\n$completed"
            val remaining = lines.drop(1).joinToString("\n")
            infoPool.plan = remaining
            if (remaining.isNotBlank()) {
                val nextLines = remaining.lines().filter { it.isNotBlank() }
                if (nextLines.isNotEmpty()) {
                    infoPool.currentSubgoal = nextLines.first().replace(Regex("^\\d+\\.?\\s*"), "")
                }
            }
            infoPool.progressStatus = "Completed: ${completed.trim()}"
        }
    }

    private suspend fun emit(result: StepResult) {
        _stepResults.emit(result)
    }

    companion object {
        private const val TAG = "AgentLoop"
    }
}
