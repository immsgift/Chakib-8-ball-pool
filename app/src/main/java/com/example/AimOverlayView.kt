package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Custom overlay view that matches the 8 Ball Pool Aiming tool in the reference screenshot:
 * - Only ONE single circle placed directly over the target/object ball.
 * - Real-time laser guidelines radiating from that single circle to ALL 6 POCKETS of the table.
 * - Primary shot line projecting through the cue ball.
 * - Top floating HUD bar with 5 icons: Move (4-way arrow), Eye, Rebound/Bounce, Settings, and Close.
 * - Cushion bounce reflections off table rails.
 */
@SuppressLint("ViewConstructor")
class AimOverlayView(
    context: Context,
    var config: AimOverlayConfig,
    private val onCloseRequested: () -> Unit,
    private val onTogglePassThrough: ((Boolean) -> Unit)? = null
) : View(context), View.OnTouchListener {

    private val density = resources.displayMetrics.density

    // SINGLE TARGET CIRCLE position
    private var ballX = resources.displayMetrics.widthPixels * 0.5f
    private var ballY = resources.displayMetrics.heightPixels * 0.55f

    // Aim Shot Angle (in degrees)
    private var aimAngleDegrees = 270f // Default pointing straight up

    // Draggable HUD position (White pill matching screenshot)
    private var hudX = (resources.displayMetrics.widthPixels - 250f * density) / 2f
    private var hudY = 16f * density
    private val hudWidth = 250f * density
    private val hudHeight = 44f * density

    // UI States
    private var isLocked = false
    private var showGuidelines = true
    private var bounceMode = config.reboundCount // 0 = off, 1 = 1-bounce, 2 = 2-bounces

    private enum class DragTarget {
        NONE,
        HUD_BAR,
        TARGET_CIRCLE,
        ROTATION_HANDLE
    }

    private var dragTarget = DragTarget.NONE
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f

    // HUD Button Rects (5 icons matching screenshot)
    private val hudRect = RectF()
    private val btnMoveRect = RectF()
    private val btnEyeRect = RectF()
    private val btnBounceRect = RectF()
    private val btnSettingsRect = RectF()
    private val btnCloseRect = RectF()

    // Paints
    private val ballStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
    }

    private val ballInnerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.argb(160, 0, 229, 255) // Cyan inner ring
    }

    private val ballFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(35, 255, 255, 255)
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // Laser Lines radiating to the 6 pockets (Translucent cyan/white beams)
    private val pocketLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.argb(180, 255, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }

    private val pocketLineGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
        color = Color.argb(60, 0, 229, 255)
        strokeCap = Paint.Cap.ROUND
    }

    // Primary shot line (solid white beam with arrow)
    private val primaryShotLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }

    private val reboundLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        color = Color.argb(230, 255, 214, 0) // Amber gold for rebounds
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }

    // HUD Paints (Clean White Background with Dark Icons like Screenshot)
    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(245, 255, 255, 255) // Clean white
    }

    private val hudBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(100, 200, 200, 200)
    }

    private val hudIconBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(25, 0, 0, 0)
    }

    private val hudIconActiveBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(50, 0, 200, 100)
    }

    private val textDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E293B.toInt()
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val textRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFDC2626.toInt()
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    data class Vec2(val x: Float, val y: Float)

    init {
        setOnTouchListener(this)
    }

    fun updateConfig(newConfig: AimOverlayConfig) {
        config = newConfig
        bounceMode = if (newConfig.showRebound) newConfig.reboundCount else 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val ballRadius = config.ballRadiusDp * density

        // Load 6 Pocket Coordinates
        val cal = AimOverlayConfig.loadCalibration(context)
        val tl = Vec2(w * cal.tlX, h * cal.tlY)
        val tr = Vec2(w * cal.trX, h * cal.trY)
        val br = Vec2(w * cal.brX, h * cal.brY)
        val bl = Vec2(w * cal.blX, h * cal.blY)

        val pockets = listOf(
            tl, // Top-Left
            tr, // Top-Right
            Vec2((tr.x + br.x) * 0.5f, (tr.y + br.y) * 0.5f), // Middle-Right
            br, // Bottom-Right
            bl, // Bottom-Left
            Vec2((tl.x + bl.x) * 0.5f, (tl.y + bl.y) * 0.5f)  // Middle-Left
        )

        val railSegments = listOf(
            Pair(tl, tr),
            Pair(tr, br),
            Pair(br, bl),
            Pair(bl, tl)
        )

        if (showGuidelines) {
            // 1. PROJECT LINES FROM SINGLE CIRCLE TO ALL 6 POCKETS SIMULTANEOUSLY!
            pockets.forEach { pocket ->
                canvas.drawLine(ballX, ballY, pocket.x, pocket.y, pocketLineGlowPaint)
                canvas.drawLine(ballX, ballY, pocket.x, pocket.y, pocketLinePaint)

                // Pocket highlight target
                canvas.drawCircle(pocket.x, pocket.y, 14f * density, pocketLineGlowPaint)
                canvas.drawCircle(pocket.x, pocket.y, 4f * density, centerDotPaint)
            }

            // 2. Primary Shot Line & Cushion Rebounds
            val rad = Math.toRadians(aimAngleDegrees.toDouble())
            val dirX = cos(rad).toFloat()
            val dirY = sin(rad).toFloat()

            val startShotX = ballX + ballRadius * dirX
            val startShotY = ballY + ballRadius * dirY

            val hit = calculateRayCushionHit(Vec2(startShotX, startShotY), Vec2(dirX, dirY), railSegments)
            if (hit != null) {
                canvas.drawLine(startShotX, startShotY, hit.point.x, hit.point.y, pocketLineGlowPaint)
                canvas.drawLine(startShotX, startShotY, hit.point.x, hit.point.y, primaryShotLinePaint)
                canvas.drawCircle(hit.point.x, hit.point.y, 4.5f * density, centerDotPaint)

                if (bounceMode > 0) {
                    val nextHit = calculateRayCushionHit(hit.point, hit.reflectedDir, railSegments)
                    if (nextHit != null) {
                        canvas.drawLine(hit.point.x, hit.point.y, nextHit.point.x, nextHit.point.y, reboundLinePaint)
                        canvas.drawCircle(nextHit.point.x, nextHit.point.y, 4f * density, reboundLinePaint)
                    }
                }
            } else {
                canvas.drawLine(startShotX, startShotY, startShotX + dirX * 1000f, startShotY + dirY * 1000f, primaryShotLinePaint)
            }

            // 3. Ghost Ball along the shot line
            val ghostDist = 65f * density
            val ghostX = ballX - dirX * ghostDist
            val ghostY = ballY - dirY * ghostDist
            canvas.drawCircle(ghostX, ghostY, ballRadius, ballInnerGlowPaint)
            canvas.drawCircle(ghostX, ghostY, ballRadius, ballStrokePaint)
            canvas.drawLine(ghostX, ghostY, ballX - dirX * ballRadius, ballY - dirY * ballRadius, primaryShotLinePaint)
        }

        // 4. DRAW THE SINGLE TARGET CIRCLE
        drawSingleTargetCircle(canvas, ballRadius)

        // 5. DRAW ROTATABLE AIM HANDLE
        val rad = Math.toRadians(aimAngleDegrees.toDouble())
        val handleDist = ballRadius + 32f * density
        val hx = ballX + handleDist * cos(rad).toFloat()
        val hy = ballY + handleDist * sin(rad).toFloat()
        canvas.drawCircle(hx, hy, 12f * density, hudBgPaint)
        canvas.drawCircle(hx, hy, 12f * density, ballStrokePaint)
        canvas.drawCircle(hx, hy, 3f * density, centerDotPaint)

        // 6. DRAW TOP FLOATING HUD BAR (Matching Screenshot)
        drawTopHudBar(canvas)
    }

    private fun drawSingleTargetCircle(canvas: Canvas, ballRadius: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(90, 0, 0, 0)
        }
        canvas.drawCircle(ballX + 2f * density, ballY + 2.5f * density, ballRadius, shadowPaint)
        canvas.drawCircle(ballX, ballY, ballRadius, ballFillPaint)
        canvas.drawCircle(ballX, ballY, ballRadius, ballStrokePaint)
        canvas.drawCircle(ballX, ballY, ballRadius * 0.55f, ballInnerGlowPaint)
        canvas.drawCircle(ballX, ballY, 3f * density, centerDotPaint)

        val tick = 6f * density
        canvas.drawLine(ballX - ballRadius, ballY, ballX - ballRadius + tick, ballY, ballStrokePaint)
        canvas.drawLine(ballX + ballRadius - tick, ballY, ballX + ballRadius, ballY, ballStrokePaint)
        canvas.drawLine(ballX, ballY - ballRadius, ballX, ballY - ballRadius + tick, ballStrokePaint)
        canvas.drawLine(ballX, ballY + ballRadius - tick, ballX, ballY + ballRadius, ballStrokePaint)
    }

    private fun drawTopHudBar(canvas: Canvas) {
        hudRect.set(hudX, hudY, hudX + hudWidth, hudY + hudHeight)
        canvas.drawRoundRect(hudRect, 10f * density, 10f * density, hudBgPaint)
        canvas.drawRoundRect(hudRect, 10f * density, 10f * density, hudBorderPaint)

        val btnSize = 36f * density
        val btnY = hudY + (hudHeight - btnSize) / 2f
        val gap = 10f * density
        var curX = hudX + 10f * density

        // 1. Move Icon (✥)
        btnMoveRect.set(curX, btnY, curX + btnSize, btnY + btnSize)
        val moveBg = if (!isLocked) hudIconActiveBgPaint else hudIconBgPaint
        canvas.drawRoundRect(btnMoveRect, 8f * density, 8f * density, moveBg)
        canvas.drawText("✥", btnMoveRect.centerX(), btnMoveRect.centerY() + 5f * density, textDarkPaint)

        // 2. Eye Icon
        curX += btnSize + gap
        btnEyeRect.set(curX, btnY, curX + btnSize, btnY + btnSize)
        val eyeBg = if (showGuidelines) hudIconActiveBgPaint else hudIconBgPaint
        canvas.drawRoundRect(btnEyeRect, 8f * density, 8f * density, eyeBg)
        val eyeSymbol = if (showGuidelines) "👁" else "⊘"
        canvas.drawText(eyeSymbol, btnEyeRect.centerX(), btnEyeRect.centerY() + 5f * density, textDarkPaint)

        // 3. Bounce Icon (🔀)
        curX += btnSize + gap
        btnBounceRect.set(curX, btnY, curX + btnSize, btnY + btnSize)
        val bounceBg = if (bounceMode > 0) hudIconActiveBgPaint else hudIconBgPaint
        canvas.drawRoundRect(btnBounceRect, 8f * density, 8f * density, bounceBg)
        canvas.drawText("🔀", btnBounceRect.centerX(), btnBounceRect.centerY() + 5f * density, textDarkPaint)

        // 4. Settings Icon (🎚)
        curX += btnSize + gap
        btnSettingsRect.set(curX, btnY, curX + btnSize, btnY + btnSize)
        canvas.drawRoundRect(btnSettingsRect, 8f * density, 8f * density, hudIconBgPaint)
        canvas.drawText("🎚", btnSettingsRect.centerX(), btnSettingsRect.centerY() + 5f * density, textDarkPaint)

        // 5. Close Icon (✕)
        curX += btnSize + gap
        btnCloseRect.set(curX, btnY, curX + btnSize, btnY + btnSize)
        canvas.drawRoundRect(btnCloseRect, 8f * density, 8f * density, hudIconBgPaint)
        canvas.drawText("✕", btnCloseRect.centerX(), btnCloseRect.centerY() + 5f * density, textRedPaint)
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (btnMoveRect.contains(x, y)) {
                    isLocked = !isLocked
                    onTogglePassThrough?.invoke(isLocked)
                    invalidate()
                    return true
                }
                if (btnEyeRect.contains(x, y)) {
                    showGuidelines = !showGuidelines
                    invalidate()
                    return true
                }
                if (btnBounceRect.contains(x, y)) {
                    bounceMode = (bounceMode + 1) % 3
                    invalidate()
                    return true
                }
                if (btnSettingsRect.contains(x, y)) {
                    aimAngleDegrees = (aimAngleDegrees + 45f) % 360f
                    invalidate()
                    return true
                }
                if (btnCloseRect.contains(x, y)) {
                    onCloseRequested()
                    return true
                }

                if (hudRect.contains(x, y)) {
                    dragTarget = DragTarget.HUD_BAR
                    touchOffsetX = x - hudX
                    touchOffsetY = y - hudY
                    return true
                }

                if (isLocked) return false

                val ballRadius = config.ballRadiusDp * density
                val rad = Math.toRadians(aimAngleDegrees.toDouble())
                val handleDist = ballRadius + 32f * density
                val hx = ballX + handleDist * cos(rad).toFloat()
                val hy = ballY + handleDist * sin(rad).toFloat()
                val distHandleSq = (x - hx) * (x - hx) + (y - hy) * (y - hy)
                if (distHandleSq <= 28f * density * 28f * density) {
                    dragTarget = DragTarget.ROTATION_HANDLE
                    return true
                }

                val distCircleSq = (x - ballX) * (x - ballX) + (y - ballY) * (y - ballY)
                val circleTouchRadius = ballRadius * 2.8f
                if (distCircleSq <= circleTouchRadius * circleTouchRadius) {
                    dragTarget = DragTarget.TARGET_CIRCLE
                    touchOffsetX = x - ballX
                    touchOffsetY = y - ballY
                    return true
                }

                val distFromBall = sqrt(distCircleSq.toDouble()).toFloat()
                if (distFromBall in (ballRadius * 1.5f)..(ballRadius + 140f * density)) {
                    dragTarget = DragTarget.ROTATION_HANDLE
                    var deg = Math.toDegrees(atan2((y - ballY).toDouble(), (x - ballX).toDouble())).toFloat()
                    if (deg < 0) deg += 360f
                    aimAngleDegrees = deg
                    invalidate()
                    return true
                }

                return false
            }

            MotionEvent.ACTION_MOVE -> {
                when (dragTarget) {
                    DragTarget.HUD_BAR -> {
                        hudX = (x - touchOffsetX).coerceIn(10f * density, width - hudWidth - 10f * density)
                        hudY = (y - touchOffsetY).coerceIn(10f * density, height - hudHeight - 10f * density)
                        invalidate()
                        return true
                    }
                    DragTarget.TARGET_CIRCLE -> {
                        ballX = (x - touchOffsetX).coerceIn(20f * density, width - 20f * density)
                        ballY = (y - touchOffsetY).coerceIn(20f * density, height - 20f * density)
                        invalidate()
                        return true
                    }
                    DragTarget.ROTATION_HANDLE -> {
                        var deg = Math.toDegrees(atan2((y - ballY).toDouble(), (x - ballX).toDouble())).toFloat()
                        if (deg < 0) deg += 360f
                        aimAngleDegrees = deg
                        invalidate()
                        return true
                    }
                    DragTarget.NONE -> return false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragTarget = DragTarget.NONE
                invalidate()
                return true
            }
        }
        return false
    }

    private data class RayHit(val point: Vec2, val reflectedDir: Vec2)

    private fun calculateRayCushionHit(
        start: Vec2,
        dir: Vec2,
        railSegments: List<Pair<Vec2, Vec2>>
    ): RayHit? {
        var minT = Float.MAX_VALUE
        var hitPoint: Vec2? = null
        var bestNormal: Vec2? = null

        railSegments.forEach { (a, b) ->
            val segVecX = b.x - a.x
            val segVecY = b.y - a.y
            val cross = dir.x * segVecY - dir.y * segVecX
            if (Math.abs(cross) > 0.0001f) {
                val toStartX = a.x - start.x
                val toStartY = a.y - start.y
                val t = (toStartX * segVecY - toStartY * segVecX) / cross
                val u = (toStartX * dir.y - toStartY * dir.x) / cross

                if (t > 0.001f && t < minT && u in 0f..1f) {
                    minT = t
                    hitPoint = Vec2(start.x + dir.x * t, start.y + dir.y * t)

                    val segLen = sqrt((segVecX * segVecX + segVecY * segVecY).toDouble()).toFloat().coerceAtLeast(1f)
                    bestNormal = Vec2(-segVecY / segLen, segVecX / segLen)
                }
            }
        }

        if (hitPoint != null && bestNormal != null) {
            var nx = bestNormal!!.x
            var ny = bestNormal!!.y
            val dot = dir.x * nx + dir.y * ny
            if (dot > 0f) {
                nx = -nx
                ny = -ny
            }
            val refX = dir.x - nx * (2f * (dir.x * nx + dir.y * ny))
            val refY = dir.y - ny * (2f * (dir.x * nx + dir.y * ny))
            val refLen = sqrt((refX * refX + refY * refY).toDouble()).toFloat().coerceAtLeast(1f)
            return RayHit(hitPoint!!, Vec2(refX / refLen, refY / refLen))
        }

        return null
    }
}
