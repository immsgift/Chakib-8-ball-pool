package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Full-screen drawing canvas for Chakib Aiming Wheel Biliardo:
 * - Always runs with FLAG_NOT_TOUCHABLE so it NEVER blocks any touches to 8 Ball Pool underneath!
 * - Draws single target circle directly over the target ball.
 * - Draws 6 real-time laser guidelines projecting directly from the target ball to ALL 6 POCKETS.
 * - Draws bank/cushion bounce reflections off calibrated rails.
 * - Highlights the optimal / nearest aligned pocket.
 */
@SuppressLint("ViewConstructor")
class LinesOverlayView(
    context: Context,
    var config: AimOverlayConfig,
    var table: CalibratedTable
) : View(context) {

    private val density = resources.displayMetrics.density

    // Ball Position (in pixels)
    var ballX: Float = resources.displayMetrics.widthPixels * 0.5f
        private set
    var ballY: Float = resources.displayMetrics.heightPixels * 0.55f
        private set
    var aimAngleDegrees: Float = 270f
        private set

    // Visibility toggles
    var showLines: Boolean = true
    var isCalibrating: Boolean = false

    // Paints
    private val ballRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
    }

    private val ballInnerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(180, 0, 229, 255)
    }

    private val centerCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.WHITE
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // Laser Lines to the 6 Pockets
    private val pocketLaserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = config.lineThicknessDp * density
        color = config.pocketLineColor
        strokeCap = Paint.Cap.ROUND
    }

    private val pocketLaserGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (config.lineThicknessDp + 3f) * density
        color = Color.argb(65, 0, 229, 255)
        strokeCap = Paint.Cap.ROUND
    }

    private val bestPocketLaserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (config.lineThicknessDp + 1f) * density
        color = config.bestPocketColor // Bright Gold / Neon
        strokeCap = Paint.Cap.ROUND
    }

    private val pocketRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.argb(220, 255, 215, 0)
    }

    private val cueLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        color = Color.WHITE
        pathEffect = DashPathEffect(floatArrayOf(12f * density, 8f * density), 0f)
        strokeCap = Paint.Cap.ROUND
    }

    private val reboundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
        color = Color.argb(220, 255, 170, 0) // Amber Rebound
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
        strokeCap = Paint.Cap.ROUND
    }

    private val cushionFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(70, 0, 255, 102) // Subtle green felt boundary
    }

    private val pocketLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setBallPosition(x: Float, y: Float) {
        ballX = x
        ballY = y
        postInvalidateOnAnimation()
    }

    fun setAimAngle(angle: Float) {
        aimAngleDegrees = (angle % 360f + 360f) % 360f
        postInvalidateOnAnimation()
    }

    fun updateConfig(newConfig: AimOverlayConfig) {
        config = newConfig
        pocketLaserPaint.strokeWidth = config.lineThicknessDp * density
        pocketLaserPaint.color = config.pocketLineColor
        pocketLaserGlowPaint.strokeWidth = (config.lineThicknessDp + 3f) * density
        bestPocketLaserPaint.color = config.bestPocketColor
        postInvalidateOnAnimation()
    }

    fun updateTable(newTable: CalibratedTable) {
        table = newTable
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!showLines) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pockets = table.getPockets(w, h)
        val ballRadius = config.ballRadiusDp * density

        // 1. Draw Calibrated Table Cushions & Pockets
        drawTableCushionsAndPockets(canvas, w, h, pockets)

        // 2. Calculate which pocket is closest in alignment to the current shot angle
        var bestPocketIndex = -1
        var minAngleDiff = 999.0
        val shotRad = Math.toRadians(aimAngleDegrees.toDouble())

        for (i in pockets.indices) {
            val p = pockets[i]
            val angleToPocket = atan2((p.y - ballY).toDouble(), (p.x - ballX).toDouble())
            var diff = abs(angleToPocket - shotRad)
            while (diff > Math.PI) diff = abs(diff - 2 * Math.PI)
            if (diff < minAngleDiff) {
                minAngleDiff = diff
                bestPocketIndex = i
            }
        }

        // 3. Draw All 6 Pocket Laser Guidelines radiating from the single target ball
        for (i in pockets.indices) {
            val p = pockets[i]
            val isBest = (i == bestPocketIndex && minAngleDiff < Math.toRadians(35.0))

            // Draw laser line from ball to pocket
            if (isBest) {
                // Highlighting the best aligned pocket with bright glow
                canvas.drawLine(ballX, ballY, p.x, p.y, pocketLaserGlowPaint)
                canvas.drawLine(ballX, ballY, p.x, p.y, bestPocketLaserPaint)
            } else {
                canvas.drawLine(ballX, ballY, p.x, p.y, pocketLaserGlowPaint)
                canvas.drawLine(ballX, ballY, p.x, p.y, pocketLaserPaint)
            }

            // Draw cushion bank bounce lines if enabled
            if (config.showRebound && config.reboundCount > 0) {
                drawBankReflectionLines(canvas, ballX, ballY, p, w, h)
            }
        }

        // 4. Draw Cue Ball Trajectory Guide (Back projection along aimAngle)
        val cueDist = min(w, h) * 0.45f
        val cueStartX = ballX - cueDist * cos(shotRad).toFloat()
        val cueStartY = ballY - cueDist * sin(shotRad).toFloat()
        canvas.drawLine(cueStartX, cueStartY, ballX, ballY, cueLinePaint)

        // Cue ghost ball at start of guide
        canvas.drawCircle(cueStartX, cueStartY, ballRadius * 0.8f, cueLinePaint)

        // 5. Draw the Single Target Circle with crosshairs
        drawTargetBallCircle(canvas, ballRadius)
    }

    private fun drawTableCushionsAndPockets(canvas: Canvas, w: Float, h: Float, pockets: List<PocketPoint>) {
        val l = table.leftRatio * w
        val t = table.topRatio * h
        val r = table.rightRatio * w
        val b = table.bottomRatio * h

        // Cushion rectangle
        canvas.drawRoundRect(l, t, r, b, 12f * density, 12f * density, cushionFramePaint)

        // 6 Pocket Indicators
        val pocketRadius = 14f * density
        for (p in pockets) {
            canvas.drawCircle(p.x, p.y, pocketRadius, pocketRingPaint)
            canvas.drawCircle(p.x, p.y, 3f * density, centerDotPaint)
            canvas.drawText(p.id, p.x, p.y - pocketRadius - 4f * density, pocketLabelPaint)
        }
    }

    private fun drawTargetBallCircle(canvas: Canvas, radius: Float) {
        // Outer white ring
        canvas.drawCircle(ballX, ballY, radius, ballRingPaint)
        // Inner cyan glow ring
        canvas.drawCircle(ballX, ballY, radius * 0.65f, ballInnerGlowPaint)
        // Center crosshair & dot
        if (config.showCenterCrosshair) {
            val tick = 6f * density
            canvas.drawLine(ballX - radius, ballY, ballX - radius + tick, ballY, centerCrossPaint)
            canvas.drawLine(ballX + radius - tick, ballY, ballX + radius, ballY, centerCrossPaint)
            canvas.drawLine(ballX, ballY - radius, ballX, ballY - radius + tick, centerCrossPaint)
            canvas.drawLine(ballX, ballY + radius - tick, ballX, ballY + radius, centerCrossPaint)
        }
        canvas.drawCircle(ballX, ballY, 3f * density, centerDotPaint)
    }

    private fun drawBankReflectionLines(canvas: Canvas, bx: Float, by: Float, pocket: PocketPoint, w: Float, h: Float) {
        val l = table.leftRatio * w
        val t = table.topRatio * h
        val r = table.rightRatio * w
        val b = table.bottomRatio * h

        // Calculate a 1-rail bounce off the opposite cushion to pocket
        // Example: if pocket is on top (t), bounce off bottom cushion (b)
        val bounceRailY = if (pocket.y < (t + b) / 2f) b else t
        val mirrorY = 2f * bounceRailY - pocket.y
        val denom = mirrorY - by
        if (abs(denom) < 1f) return
        val bounceX = bx + (bounceRailY - by) * (pocket.x - bx) / denom

        if (bounceX in l..r) {
            canvas.drawLine(bx, by, bounceX, bounceRailY, reboundPaint)
            canvas.drawLine(bounceX, bounceRailY, pocket.x, pocket.y, reboundPaint)
            canvas.drawCircle(bounceX, bounceRailY, 4f * density, centerDotPaint)
        }
    }
}
