package com.mobileagent.app.service

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

class FloatingWindowManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isShowing = false
    private var isExpanded = false

    private var stepText: TextView? = null
    private var phaseText: TextView? = null
    private var messageText: TextView? = null
    private var bubbleView: View? = null
    private var detailPanel: LinearLayout? = null
    private var pulseAnimator: AnimatorSet? = null
    private var bubbleBackground: GradientDrawable? = null
    private var isCompleted = false
    var onDismissRequest: (() -> Unit)? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 200
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isShowing) return
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Bubble (always visible)
        bubbleBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            setColor(0xE6333333.toInt())
        }
        val bubble = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = bubbleBackground
            gravity = Gravity.CENTER_VERTICAL
        }

        stepText = TextView(context).apply {
            text = "⏳"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        bubble.addView(stepText)

        phaseText = TextView(context).apply {
            text = " Starting…"
            setTextColor(0xFFAABBCC.toInt())
            textSize = 12f
            setPadding(dp(6), 0, 0, 0)
        }
        bubble.addView(phaseText)

        bubbleView = bubble
        container.addView(bubble)

        // Detail panel (expandable)
        detailPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
                setColor(0xE6222222.toInt())
            }
            visibility = View.GONE
        }

        messageText = TextView(context).apply {
            text = ""
            setTextColor(0xFFDDDDDD.toInt())
            textSize = 11f
            maxLines = 6
        }
        detailPanel!!.addView(messageText)

        container.addView(detailPanel)

        // Touch handling: drag + tap to expand/collapse
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        if (isCompleted) {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            launchIntent?.let { context.startActivity(it) }
                            dismiss()
                            onDismissRequest?.invoke()
                        } else {
                            isExpanded = !isExpanded
                            detailPanel?.visibility = if (isExpanded) View.VISIBLE else View.GONE
                            windowManager?.updateViewLayout(floatingView, params)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        floatingView = container
        windowManager?.addView(container, params)
        isShowing = true
        startPulseAnimation(bubble)
    }

    private fun startPulseAnimation(view: View) {
        pulseAnimator?.cancel()
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.85f, 1f).apply {
            repeatCount = ValueAnimator.INFINITE
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        bubbleView?.scaleX = 1f
        bubbleView?.scaleY = 1f
        bubbleView?.alpha = 1f
    }

    private fun playSuccessAnimation() {
        stopPulseAnimation()
        val view = bubbleView ?: return
        val bounce = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 0.95f, 1.05f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 0.95f, 1.05f, 1f)
            )
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
        }
        bounce.start()
    }

    fun update(step: Int, phase: String, message: String) {
        if (!isShowing) return

        val emoji = when (phase) {
            "screenshot" -> "📷"
            "manager" -> "🧠"
            "executor" -> "👆"
            "execute" -> "▶️"
            "reflector" -> "👁"
            "notetaker" -> "📝"
            "finished", "done" -> "✅"
            "answer" -> "💬"
            "error" -> "❌"
            else -> "⏳"
        }

        val stepLabel = if (step >= 0) "Step $step" else ""
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 20f,
            context.resources.displayMetrics
        )

        stepText?.post {
            stepText?.text = emoji
            phaseText?.text = " $stepLabel ${phase.uppercase()}"
            messageText?.text = message

            if (phase == "finished" || phase == "done" || phase == "answer") {
                isCompleted = true
                bubbleBackground?.setColor(0xE61B5E3B.toInt())
                bubbleView?.background = bubbleBackground
                playSuccessAnimation()
            } else if (phase == "error") {
                isCompleted = true
                stopPulseAnimation()
                bubbleBackground?.setColor(0xE6660000.toInt())
                bubbleView?.background = bubbleBackground
            }
        }
    }

    fun dismiss() {
        if (!isShowing) return
        stopPulseAnimation()
        try {
            windowManager?.removeView(floatingView)
        } catch (_: Exception) { }
        floatingView = null
        bubbleView = null
        bubbleBackground = null
        isShowing = false
        isExpanded = false
    }

    fun isVisible(): Boolean = isShowing
}
