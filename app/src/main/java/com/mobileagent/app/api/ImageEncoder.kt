package com.mobileagent.app.api

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageEncoder {

    private const val MAX_LONG_SIDE = 1344
    private const val JPEG_QUALITY = 85

    data class EncodeResult(
        val base64: String,
        val originalWidth: Int,
        val originalHeight: Int,
        val encodedWidth: Int,
        val encodedHeight: Int
    ) {
        val scaleX: Float get() = originalWidth.toFloat() / encodedWidth
        val scaleY: Float get() = originalHeight.toFloat() / encodedHeight
    }

    fun encode(bitmap: Bitmap): String {
        return encodeWithInfo(bitmap).base64
    }

    fun encodeWithInfo(bitmap: Bitmap): EncodeResult {
        val resized = resizeIfNeeded(bitmap)
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        val result = EncodeResult(
            base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP),
            originalWidth = bitmap.width,
            originalHeight = bitmap.height,
            encodedWidth = resized.width,
            encodedHeight = resized.height
        )
        if (resized !== bitmap) resized.recycle()
        return result
    }

    fun getScaleFactors(bitmap: Bitmap): Pair<Float, Float> {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= MAX_LONG_SIDE) return Pair(1f, 1f)
        val scale = MAX_LONG_SIDE.toFloat() / longSide
        val encodedW = (bitmap.width * scale).toInt()
        val encodedH = (bitmap.height * scale).toInt()
        return Pair(
            bitmap.width.toFloat() / encodedW,
            bitmap.height.toFloat() / encodedH
        )
    }

    private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longSide = maxOf(w, h)
        if (longSide <= MAX_LONG_SIDE) return bitmap

        val scale = MAX_LONG_SIDE.toFloat() / longSide
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
