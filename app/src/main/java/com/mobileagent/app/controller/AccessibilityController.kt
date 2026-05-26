package com.mobileagent.app.controller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.view.WindowManager
import com.mobileagent.app.service.AgentAccessibilityService
import kotlinx.coroutines.delay

class AccessibilityController(
    private val context: Context,
    private val screenCaptureManager: ScreenCaptureManager
) : DeviceController {

    private val service: AgentAccessibilityService
        get() = AgentAccessibilityService.instance
            ?: throw IllegalStateException("AccessibilityService not running")

    override suspend fun captureScreenshot(): Bitmap? {
        return screenCaptureManager.capture()
    }

    override suspend fun tap(x: Int, y: Int) {
        service.performTap(x.toFloat(), y.toFloat())
        delay(100)
    }

    override suspend fun longPress(x: Int, y: Int, durationMs: Long) {
        service.performLongPress(x.toFloat(), y.toFloat(), durationMs)
        delay(100)
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        service.performSwipe(
            x1.toFloat(), y1.toFloat(),
            x2.toFloat(), y2.toFloat(),
            durationMs
        )
        delay(100)
    }

    override suspend fun typeText(text: String) {
        service.performTypeText(text)
        delay(200)
    }

    override suspend fun pressBack() {
        service.performBack()
        delay(300)
    }

    override suspend fun pressHome() {
        service.performHome()
        delay(500)
    }

    override suspend fun pressEnter() {
        service.performEnter()
        delay(100)
    }

    override fun getScreenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(size)
        return Pair(size.x, size.y)
    }
}
