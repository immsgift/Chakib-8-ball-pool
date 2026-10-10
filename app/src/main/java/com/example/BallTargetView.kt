package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.atan2

/**
 * Compact floating target ball circle (e.g., 60dp x 60dp):
 * - Positioned directly over the object ball.
 * - Allows dragging to place over any ball in 8 Ball Pool.
 * - Can be toggled to FLAG_NOT_TOUCHABLE in Game Mode so user can shoot smoothly without touch interception.
 */
@SuppressLint("ViewConstructor")
class BallTargetView(
    context: Context,
    var config: AimOverlayConfig,
    private val onPositionChanged: (Float, Float) -> Unit,
    private val onAngleChanged: (Float) -> Unit
) : View(context), View.OnTouchListener {

    private val density = resources.displayMetrics.density

    var currentX: Float = 0f
    var currentY: Float = 0f
    var aimAngle: Float = 270f

    var isGameMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialParamX = 0
    private var initialParamY = 0

    var layoutParamsRef: WindowManager.LayoutParams? = null
    var windowManagerRef: WindowManager? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(45, 0, 229, 255)
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val modeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(190, 0, 0, 0)
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    init {
        setOnTouchListener(this)
    }

    fun updateConfig(newConfig: AimOverlayConfig) {
        config = newConfig
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width / 2f) - 4f * density

        // Draw translucent ball target circle
        canvas.drawCircle(cx, cy, radius, fillPaint)
        ringPaint.color = if (isGameMode) Color.argb(180, 0, 255, 102) else Color.WHITE
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawCircle(cx, cy, 3f * density, centerDotPaint)

        // Subtle status indicator
        if (!isGameMode) {
            canvas.drawRoundRect(cx - 18f * density, cy + radius - 10f * density, cx + 18f * density, cy + radius + 4f * density, 4f * density, 4f * density, modeBadgePaint)
            canvas.drawText("MOVE", cx, cy + radius - 1f * density, badgeTextPaint)
        }
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        if (isGameMode) return false // Pass through in game mode

        val wm = windowManagerRef ?: return false
        val params = layoutParamsRef ?: return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialParamX = params.x
                initialParamY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                params.x = initialParamX + dx
                params.y = initialParamY + dy
                try {
                    wm.updateViewLayout(this, params)
                } catch (_: Exception) {}

                val centerX = params.x + width / 2f
                val centerY = params.y + height / 2f
                currentX = centerX
                currentY = centerY
                onPositionChanged(centerX, centerY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val centerX = params.x + width / 2f
                val centerY = params.y + height / 2f
                currentX = centerX
                currentY = centerY
                onPositionChanged(centerX, centerY)
                return true
            }
        }
        return false
    }
}
