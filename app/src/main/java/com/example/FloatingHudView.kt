package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Compact floating HUD toolbar for Chakib Aiming Wheel Biliardo:
 * - Always accessible floating controls.
 * - Draggable anywhere on the screen by touch.
 * - Controls:
 *   1. 🎮 / 🎯 Game Mode (Pass-Through Touches for shooting) vs Move Mode.
 *   2. 📐 Table Calibration ("القيس") adjuster.
 *   3. 🔄 360° Angle Wheel fine-tuner.
 *   4. 🔀 Cushion Rebound toggle.
 *   5. 👁️ Guidelines Visibility.
 *   6. ✕ Close tool.
 */
@SuppressLint("ViewConstructor")
class FloatingHudView(
    context: Context,
    var config: AimOverlayConfig,
    var table: CalibratedTable,
    private val onGameModeToggled: (Boolean) -> Unit,
    private val onCalibrationChanged: (CalibratedTable) -> Unit,
    private val onAngleAdjusted: (Float) -> Unit,
    private val onReboundToggled: (Int) -> Unit,
    private val onVisibilityToggled: (Boolean) -> Unit,
    private val onCloseRequested: () -> Unit
) : View(context), View.OnTouchListener {

    private val density = resources.displayMetrics.density

    var isGameMode: Boolean = false
        private set
    var showGuidelines: Boolean = true
        private set
    var reboundMode: Int = config.reboundCount
        private set
    var currentAngle: Float = 270f
        private set

    var windowManagerRef: WindowManager? = null
    var layoutParamsRef: WindowManager.LayoutParams? = null

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialParamX = 0
    private var initialParamY = 0
    private var isDraggingBar = false

    // Rects for touch detection
    private val hudBgRect = RectF()
    private val btnModeRect = RectF()
    private val btnCalibRect = RectF()
    private val btnAngleMinusRect = RectF()
    private val btnAnglePlusRect = RectF()
    private val btnReboundRect = RectF()
    private val btnEyeRect = RectF()
    private val btnCloseRect = RectF()

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 13, 27, 20) // Deep billiard dark emerald
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(140, 0, 255, 102) // Neon emerald border
    }

    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(90, 255, 255, 255)
    }

    private val btnActiveGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 0, 200, 83)
    }

    private val btnActiveCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 0, 229, 255)
    }

    private val textWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val textDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 10, 20, 15)
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val textRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 255, 82, 82)
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setOnTouchListener(this)
    }

    fun setAngle(angle: Float) {
        currentAngle = angle
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        hudBgRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(hudBgRect, 14f * density, 14f * density, bgPaint)
        canvas.drawRoundRect(hudBgRect, 14f * density, 14f * density, borderPaint)

        // Draw drag grip dots on the left
        val gripX = 10f * density
        val cy = h / 2f
        canvas.drawLine(gripX, cy - 8f * density, gripX, cy + 8f * density, gripPaint)
        canvas.drawLine(gripX + 3.5f * density, cy - 8f * density, gripX + 3.5f * density, cy + 8f * density, gripPaint)

        val btnH = h - 12f * density
        val btnY = 6f * density
        var curX = 20f * density

        // 1. GAME MODE / MOVE TOGGLE BUTTON
        val modeW = 58f * density
        btnModeRect.set(curX, btnY, curX + modeW, btnY + btnH)
        val modeBg = if (isGameMode) btnActiveCyanPaint else btnActiveGreenPaint
        canvas.drawRoundRect(btnModeRect, 8f * density, 8f * density, modeBg)
        val modeText = if (isGameMode) "🎮 SHOOT" else "🎯 MOVE"
        canvas.drawText(modeText, btnModeRect.centerX(), btnModeRect.centerY() + 4f * density, textDarkPaint)

        // 2. CALIBRATION ("القيس")
        curX += modeW + 6f * density
        val calibW = 48f * density
        btnCalibRect.set(curX, btnY, curX + calibW, btnY + btnH)
        canvas.drawRoundRect(btnCalibRect, 8f * density, 8f * density, btnBgPaint)
        canvas.drawText("📐 القيس", btnCalibRect.centerX(), btnCalibRect.centerY() + 4f * density, textWhitePaint)

        // 3. ANGLE MINUS (-5°)
        curX += calibW + 6f * density
        val angleStepW = 32f * density
        btnAngleMinusRect.set(curX, btnY, curX + angleStepW, btnY + btnH)
        canvas.drawRoundRect(btnAngleMinusRect, 8f * density, 8f * density, btnBgPaint)
        canvas.drawText("-5°", btnAngleMinusRect.centerX(), btnAngleMinusRect.centerY() + 4f * density, textWhitePaint)

        // 4. ANGLE PLUS (+5°)
        curX += angleStepW + 4f * density
        btnAnglePlusRect.set(curX, btnY, curX + angleStepW, btnY + btnH)
        canvas.drawRoundRect(btnAnglePlusRect, 8f * density, 8f * density, btnBgPaint)
        canvas.drawText("+5°", btnAnglePlusRect.centerX(), btnAnglePlusRect.centerY() + 4f * density, textWhitePaint)

        // 5. REBOUND (🔀)
        curX += angleStepW + 6f * density
        val smallBtnW = 30f * density
        btnReboundRect.set(curX, btnY, curX + smallBtnW, btnY + btnH)
        val reboundBg = if (reboundMode > 0) btnActiveGreenPaint else btnBgPaint
        canvas.drawRoundRect(btnReboundRect, 8f * density, 8f * density, reboundBg)
        val reboundTxt = if (reboundMode > 0) "🔀${reboundMode}" else "🔀"
        val reboundTextColor = if (reboundMode > 0) textDarkPaint else textWhitePaint
        canvas.drawText(reboundTxt, btnReboundRect.centerX(), btnReboundRect.centerY() + 4f * density, reboundTextColor)

        // 6. EYE (👁)
        curX += smallBtnW + 5f * density
        btnEyeRect.set(curX, btnY, curX + smallBtnW, btnY + btnH)
        val eyeBg = if (showGuidelines) btnActiveCyanPaint else btnBgPaint
        canvas.drawRoundRect(btnEyeRect, 8f * density, 8f * density, eyeBg)
        val eyeText = if (showGuidelines) "👁" else "⊘"
        val eyeTextColor = if (showGuidelines) textDarkPaint else textWhitePaint
        canvas.drawText(eyeText, btnEyeRect.centerX(), btnEyeRect.centerY() + 4f * density, eyeTextColor)

        // 7. CLOSE (✕)
        curX += smallBtnW + 5f * density
        btnCloseRect.set(curX, btnY, curX + smallBtnW, btnY + btnH)
        canvas.drawRoundRect(btnCloseRect, 8f * density, 8f * density, btnBgPaint)
        canvas.drawText("✕", btnCloseRect.centerX(), btnCloseRect.centerY() + 4f * density, textRedPaint)
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check button taps
                if (btnModeRect.contains(x, y)) {
                    isGameMode = !isGameMode
                    onGameModeToggled(isGameMode)
                    invalidate()
                    return true
                }
                if (btnCalibRect.contains(x, y)) {
                    // Cycle calibration presets: Default Landscape -> Wide Landscape -> Compact
                    val nextTable = when {
                        table.leftRatio > 0.07f -> table.copy(leftRatio = 0.05f, rightRatio = 0.95f, topRatio = 0.12f, bottomRatio = 0.88f)
                        table.leftRatio < 0.06f -> table.copy(leftRatio = 0.10f, rightRatio = 0.90f, topRatio = 0.16f, bottomRatio = 0.84f)
                        else -> table.copy(leftRatio = 0.08f, rightRatio = 0.92f, topRatio = 0.14f, bottomRatio = 0.86f)
                    }
                    table = nextTable
                    onCalibrationChanged(table)
                    invalidate()
                    return true
                }
                if (btnAngleMinusRect.contains(x, y)) {
                    currentAngle = (currentAngle - 5f + 360f) % 360f
                    onAngleAdjusted(currentAngle)
                    invalidate()
                    return true
                }
                if (btnAnglePlusRect.contains(x, y)) {
                    currentAngle = (currentAngle + 5f) % 360f
                    onAngleAdjusted(currentAngle)
                    invalidate()
                    return true
                }
                if (btnReboundRect.contains(x, y)) {
                    reboundMode = (reboundMode + 1) % 3
                    onReboundToggled(reboundMode)
                    invalidate()
                    return true
                }
                if (btnEyeRect.contains(x, y)) {
                    showGuidelines = !showGuidelines
                    onVisibilityToggled(showGuidelines)
                    invalidate()
                    return true
                }
                if (btnCloseRect.contains(x, y)) {
                    onCloseRequested()
                    return true
                }

                // If touched outside buttons, drag the toolbar window
                val wm = windowManagerRef ?: return true
                val params = layoutParamsRef ?: return true
                isDraggingBar = true
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialParamX = params.x
                initialParamY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingBar) {
                    val wm = windowManagerRef ?: return true
                    val params = layoutParamsRef ?: return true
                    params.x = initialParamX + (event.rawX - initialTouchX).toInt()
                    params.y = initialParamY + (event.rawY - initialTouchY).toInt()
                    try {
                        wm.updateViewLayout(this, params)
                    } catch (_: Exception) {}
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingBar = false
                return true
            }
        }
        return false
    }
}
