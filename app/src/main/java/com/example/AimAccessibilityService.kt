package com.example

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Trusted Accessibility Service for Chakib Aiming Wheel Biliardo:
 * Uses WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY.
 * Because TYPE_ACCESSIBILITY_OVERLAY is officially recognized by Android's InputDispatcher
 * as a trusted window, Android 12+ and 8 Ball Pool NEVER block untrusted touches through it!
 * The cue stick moves smoothly and the game NEVER freezes!
 */
class AimAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var linesOverlayView: LinesOverlayView? = null
    private var linesParams: WindowManager.LayoutParams? = null

    private var ballTargetView: BallTargetView? = null
    private var ballParams: WindowManager.LayoutParams? = null

    private var floatingHudView: FloatingHudView? = null
    private var hudParams: WindowManager.LayoutParams? = null

    private var config: AimOverlayConfig = AimOverlayConfig()
    private var table: CalibratedTable = CalibratedTable()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AimOverlayConfig.ACTION_CONFIG_CHANGED -> {
                    context?.let {
                        config = AimOverlayConfig.load(it)
                        table = AimOverlayConfig.loadCalibration(it)
                        linesOverlayView?.updateConfig(config)
                        linesOverlayView?.updateTable(table)
                        ballTargetView?.updateConfig(config)
                    }
                }
                AimOverlayConfig.ACTION_STOP_OVERLAY -> {
                    disableSelf()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used - only used for trusted TYPE_ACCESSIBILITY_OVERLAY window injection
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this

        config = AimOverlayConfig.load(this)
        table = AimOverlayConfig.loadCalibration(this)

        val filter = IntentFilter().apply {
            addAction(AimOverlayConfig.ACTION_CONFIG_CHANGED)
            addAction(AimOverlayConfig.ACTION_STOP_OVERLAY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        initOverlayWindows()
    }

    private fun initOverlayWindows() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density

        val (savedX, savedY, savedAngle) = AimOverlayConfig.loadBallState(
            this,
            resources.displayMetrics.widthPixels * 0.5f,
            resources.displayMetrics.heightPixels * 0.55f
        )

        // 1. TRUSTED FULL-SCREEN GUIDELINES OVERLAY
        linesOverlayView = LinesOverlayView(this, config, table).apply {
            setBallPosition(savedX, savedY)
            setAimAngle(savedAngle)
        }

        val lParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // Trusted Window Type!
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        linesParams = lParams

        try {
            wm.addView(linesOverlayView, lParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. BALL TARGET CIRCLE
        val ballSizePx = ((config.ballRadiusDp * 2f + 16f) * density).toInt()
        val ballLeft = (savedX - ballSizePx / 2f).toInt()
        val ballTop = (savedY - ballSizePx / 2f).toInt()

        val bParams = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ballLeft
            y = ballTop
        }
        ballParams = bParams

        ballTargetView = BallTargetView(
            context = this,
            config = config,
            onPositionChanged = { cx, cy ->
                linesOverlayView?.setBallPosition(cx, cy)
                floatingHudView?.currentAngle?.let { angle ->
                    AimOverlayConfig.saveBallState(this, cx, cy, angle)
                }
            },
            onAngleChanged = { angle ->
                linesOverlayView?.setAimAngle(angle)
                floatingHudView?.setAngle(angle)
            }
        ).apply {
            windowManagerRef = wm
            layoutParamsRef = bParams
        }

        try {
            wm.addView(ballTargetView, bParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. FLOATING HUD TOOLBAR
        val hudWidthPx = (300f * density).toInt()
        val hudHeightPx = (46f * density).toInt()
        val hudTop = (16f * density).toInt()
        val hudLeft = ((resources.displayMetrics.widthPixels - hudWidthPx) / 2).coerceAtLeast(0)

        val hParams = WindowManager.LayoutParams(
            hudWidthPx,
            hudHeightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = hudLeft
            y = hudTop
        }
        hudParams = hParams

        floatingHudView = FloatingHudView(
            context = this,
            config = config,
            table = table,
            onGameModeToggled = { isGameMode ->
                applyGameMode(isGameMode)
            },
            onCalibrationChanged = { newTable ->
                table = newTable
                AimOverlayConfig.saveCalibration(this, newTable)
                linesOverlayView?.updateTable(newTable)
            },
            onAngleAdjusted = { newAngle ->
                linesOverlayView?.setAimAngle(newAngle)
                ballTargetView?.currentX?.let { bx ->
                    AimOverlayConfig.saveBallState(this, bx, ballTargetView?.currentY ?: 0f, newAngle)
                }
            },
            onReboundToggled = { rebounds ->
                val newConfig = config.copy(reboundCount = rebounds, showRebound = rebounds > 0)
                config = newConfig
                AimOverlayConfig.save(this, newConfig)
                linesOverlayView?.updateConfig(newConfig)
            },
            onVisibilityToggled = { visible ->
                linesOverlayView?.showLines = visible
                linesOverlayView?.invalidate()
            },
            onCloseRequested = {
                stopOverlay()
            }
        ).apply {
            windowManagerRef = wm
            layoutParamsRef = hParams
            setAngle(savedAngle)
        }

        try {
            wm.addView(floatingHudView, hParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyGameMode(isGameMode: Boolean) {
        val wm = windowManager ?: return
        val targetView = ballTargetView ?: return
        val params = ballParams ?: return

        targetView.isGameMode = isGameMode

        if (isGameMode) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }

        try {
            wm.updateViewLayout(targetView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopOverlay() {
        val wm = windowManager
        if (wm != null) {
            floatingHudView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
            ballTargetView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
            linesOverlayView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        }
        floatingHudView = null
        ballTargetView = null
        linesOverlayView = null
        isRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null

        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {}

        stopOverlay()
    }

    companion object {
        var isRunning: Boolean = false
            private set

        var instance: AimAccessibilityService? = null
            private set
    }
}
