package com.mobileagent.app.util

import com.mobileagent.app.agent.AgentAction
import kotlinx.serialization.json.*

object JsonActionParser {

    fun parseAction(response: String): AgentAction {
        val actionJson = extractJsonFromResponse(response) ?: return AgentAction.Invalid

        return try {
            val json = Json.parseToJsonElement(actionJson).jsonObject
            val actionType = json["action"]?.jsonPrimitive?.content ?: return AgentAction.Invalid

            when (actionType) {
                AgentAction.ACTION_CLICK -> {
                    val coord = parseCoordinate(json["coordinate"]) ?: return AgentAction.Invalid
                    AgentAction.Click(coord.first, coord.second)
                }
                AgentAction.ACTION_LONG_PRESS -> {
                    val coord = parseCoordinate(json["coordinate"]) ?: return AgentAction.Invalid
                    AgentAction.LongPress(coord.first, coord.second)
                }
                AgentAction.ACTION_SWIPE, AgentAction.ACTION_SCROLL -> {
                    val coord1 = parseCoordinate(json["coordinate"]) ?: return AgentAction.Invalid
                    val coord2 = parseCoordinate(json["coordinate2"]) ?: return AgentAction.Invalid
                    AgentAction.Swipe(coord1.first, coord1.second, coord2.first, coord2.second)
                }
                AgentAction.ACTION_TYPE -> {
                    val text = json["text"]?.jsonPrimitive?.content ?: return AgentAction.Invalid
                    AgentAction.Type(text)
                }
                AgentAction.ACTION_SYSTEM_BUTTON -> {
                    val button = json["button"]?.jsonPrimitive?.content ?: return AgentAction.Invalid
                    val buttonType = when (button.lowercase()) {
                        "back" -> AgentAction.ButtonType.Back
                        "home" -> AgentAction.ButtonType.Home
                        "enter" -> AgentAction.ButtonType.Enter
                        else -> return AgentAction.Invalid
                    }
                    AgentAction.SystemButton(buttonType)
                }
                AgentAction.ACTION_ANSWER -> {
                    val text = json["text"]?.jsonPrimitive?.content ?: ""
                    AgentAction.Answer(text)
                }
                AgentAction.ACTION_WAIT -> AgentAction.Wait
                AgentAction.ACTION_STATUS -> {
                    val status = json["status"]?.jsonPrimitive?.content ?: ""
                    if (status.contains("finish", ignoreCase = true) ||
                        status.contains("success", ignoreCase = true)) {
                        AgentAction.Finished
                    } else {
                        AgentAction.Invalid
                    }
                }
                else -> AgentAction.Invalid
            }
        } catch (e: Exception) {
            AgentAction.Invalid
        }
    }

    fun extractActionMap(response: String): Map<String, Any?>? {
        val actionJson = extractJsonFromResponse(response) ?: return null
        return try {
            val element = Json.parseToJsonElement(actionJson).jsonObject
            jsonObjectToMap(element)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractJsonFromResponse(response: String): String? {
        val actionSection = Regex("###\\s*Action\\s*###\\s*\\n([\\s\\S]*?)(?:###|$)")
            .find(response)?.groupValues?.get(1)?.trim()

        val searchIn = actionSection ?: response

        val jsonMatch = Regex("\\{[^{}]*\\}").find(searchIn)
        return jsonMatch?.value
    }

    private fun parseCoordinate(element: JsonElement?): Pair<Int, Int>? {
        if (element == null) return null
        return try {
            val array = element.jsonArray
            val x = array[0].jsonPrimitive.int
            val y = array[1].jsonPrimitive.int
            Pair(x, y)
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
        return obj.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> when {
                    value.isString -> value.content
                    value.booleanOrNull != null -> value.boolean
                    value.intOrNull != null -> value.int
                    value.floatOrNull != null -> value.float
                    else -> value.content
                }
                is JsonArray -> value.map { el ->
                    when (el) {
                        is JsonPrimitive -> when {
                            el.intOrNull != null -> el.int
                            el.floatOrNull != null -> el.float
                            else -> el.content
                        }
                        else -> el.toString()
                    }
                }
                is JsonObject -> jsonObjectToMap(value)
                is JsonNull -> null
            }
        }
    }
}
