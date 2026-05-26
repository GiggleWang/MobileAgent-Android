package com.mobileagent.app.controller

import android.graphics.*
import android.view.accessibility.AccessibilityNodeInfo
import com.mobileagent.app.service.AgentAccessibilityService

data class UiElement(
    val index: Int,
    val text: String,
    val className: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val contentDescription: String
)

object UiElementDetector {

    fun detectElements(): List<UiElement> {
        val service = AgentAccessibilityService.instance ?: return emptyList()
        val root = service.rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<UiElement>()
        var index = 0
        traverseNode(root, elements, index = { index++ })
        root.recycle()
        return elements
    }

    fun annotateScreenshot(bitmap: Bitmap, elements: List<UiElement>): Bitmap {
        val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)

        val boxPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val labelBgPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }

        val labelTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        for (el in elements) {
            canvas.drawRect(el.bounds, boxPaint)

            val label = "[${el.index}]"
            val textWidth = labelTextPaint.measureText(label)
            val labelRect = RectF(
                el.bounds.left.toFloat(),
                el.bounds.top.toFloat() - 32f,
                el.bounds.left + textWidth + 8f,
                el.bounds.top.toFloat()
            )
            if (labelRect.top < 0) {
                labelRect.offset(0f, 32f)
            }
            canvas.drawRect(labelRect, labelBgPaint)
            canvas.drawText(label, labelRect.left + 4f, labelRect.bottom - 6f, labelTextPaint)
        }

        return annotated
    }

    fun buildElementListText(elements: List<UiElement>): String {
        if (elements.isEmpty()) return ""
        return buildString {
            appendLine("Detected UI elements on screen:")
            for (el in elements) {
                val type = el.className.substringAfterLast(".")
                val text = when {
                    el.text.isNotBlank() -> "\"${el.text}\""
                    el.contentDescription.isNotBlank() -> "desc:\"${el.contentDescription}\""
                    else -> "(no text)"
                }
                val center = "${(el.bounds.left + el.bounds.right) / 2}, ${(el.bounds.top + el.bounds.bottom) / 2}"
                val flags = buildList {
                    if (el.isClickable) add("clickable")
                    if (el.isScrollable) add("scrollable")
                }.joinToString(",")
                appendLine("[${el.index}] $type $text at center($center) bounds(${el.bounds}) $flags")
            }
        }
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        elements: MutableList<UiElement>,
        index: () -> Int
    ) {
        val dominated = node.isClickable || node.isScrollable ||
                !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()

        if (dominated && node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 10 && bounds.height() > 10) {
                elements.add(UiElement(
                    index = index(),
                    text = node.text?.toString()?.take(50) ?: "",
                    className = node.className?.toString() ?: "",
                    bounds = bounds,
                    isClickable = node.isClickable,
                    isScrollable = node.isScrollable,
                    contentDescription = node.contentDescription?.toString()?.take(50) ?: ""
                ))
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, elements, index)
            child.recycle()
        }
    }
}
