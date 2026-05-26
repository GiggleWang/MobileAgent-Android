package com.mobileagent.app.api

import android.graphics.Bitmap

interface VlmApiClient {
    suspend fun predictWithImages(
        systemPrompt: String?,
        textPrompt: String,
        images: List<Bitmap>
    ): Result<String>
}
