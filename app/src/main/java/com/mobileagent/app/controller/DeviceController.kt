package com.mobileagent.app.controller

import android.graphics.Bitmap

interface DeviceController {
    suspend fun captureScreenshot(): Bitmap?
    suspend fun tap(x: Int, y: Int)
    suspend fun longPress(x: Int, y: Int, durationMs: Long = 1000L)
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 500L)
    suspend fun typeText(text: String)
    suspend fun pressBack()
    suspend fun pressHome()
    suspend fun pressEnter()
    fun getScreenSize(): Pair<Int, Int>
}
