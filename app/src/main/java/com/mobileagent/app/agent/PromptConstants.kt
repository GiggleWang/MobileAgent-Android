package com.mobileagent.app.agent

object PromptConstants {

    const val INPUT_KNOW = """To type text, first tap the input field to activate it. Once the input field is focused (usually indicated by a cursor or blinking line), use the type action to input text. If the keyboard appears, it will not block the agent's operation."""

    val ATOMIC_ACTION_SIGNATURES = """
Available actions:
1. answer: Provide an answer to the user's question. Arguments: text (string).
2. click: Click/tap at the specified coordinate. Arguments: coordinate [x, y].
3. long_press: Long press at the specified coordinate. Arguments: coordinate [x, y].
4. type: Type text into the currently focused input field. First ensure the input field is activated by tapping it. Arguments: text (string).
5. system_button: Press a system button. Arguments: button (one of: back, home, enter).
6. swipe: Swipe from one coordinate to another. Arguments: coordinate [x1, y1], coordinate2 [x2, y2]. Note: swipe upward to scroll down, swipe downward to scroll up.
""".trimIndent()

    val EXECUTOR_OUTPUT_FORMAT = """
Your output should strictly follow the format below:

### Thought ###
[Your detailed reasoning about what action to take and why]

### Action ###
[A single JSON object describing the action, e.g., {"action": "click", "coordinate": [540, 1200]}]

### Description ###
[A brief description of what this action does, not the expected outcome]
""".trimIndent()

    val REFLECTOR_OUTPUT_FORMAT = """
Your output should strictly follow the format below:

### Outcome ###
[One of: A, B, or C]
A: Successful or Partially Successful - the action produced visible changes aligned with the intent.
B: Failed - wrong page or unexpected result, need to go back to previous state.
C: Failed - no visible changes produced (e.g., swipe reached bottom, element not clickable).

### Error Description ###
[Detailed description of what went wrong, or "None" if outcome is A]
""".trimIndent()

    val NOTETAKER_OUTPUT_FORMAT = """
Your output should strictly follow the format below:

### Important Notes ###
[Updated notes combining existing notes with any new important information extracted from the current screen. Only keep significant textual/visual information relevant to the task. Do not take notes on low-level actions. Do not repeat the user request or progress status. Do not make up content you are not sure about.]
""".trimIndent()
}
