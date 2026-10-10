package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null

    // 1. Full screen guidelines overlay (ALWAYS FLAG_NOT_TOUCHABLE so it NEVER blocks touches in 8 Ball Pool)
    private var linesOverlayView: LinesOverlayView? = null
    private var linesParams: WindowManager.LayoutParams? = null

    // 2. Draggable target ball circle (only consumes touch on the small ball itself, and can be toggled to NOT_TOUCHABLE in Game Mode)
    private var ballTargetView: BallTargetView? = null
    private var ballParams: WindowManager.LayoutParams? = null

    // 3. Compact floating HUD toolbar
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
                    stopSelf()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        config = AimOverlayConfig.load(this)
        table = AimOverlayConfig.loadCalibration(this)

        createNotificationChannel()
        startForegroundNotification()

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_notification_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText("دائرة التصويب وخطوط الجيوب نشطة. اللمس ممرر للعبة 8 Ball Pool.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_stop_overlay),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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

        // 1. ADD FULL-SCREEN LINES OVERLAY (WITH FLAG_NOT_TOUCHABLE!)
        linesOverlayView = LinesOverlayView(this, config, table).apply {
            setBallPosition(savedX, savedY)
            setAimAngle(savedAngle)
        }

        val lParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Must be <= 0.8f for Android 12+ to allow touches to pass through without blocking
            alpha = 0.55f
        }
        linesParams = lParams

        try {
            wm.addView(linesOverlayView, lParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            return
        }

        // 2. ADD COMPACT BALL TARGET CIRCLE (Draggable to place over any ball)
        val ballSizePx = ((config.ballRadiusDp * 2f + 16f) * density).toInt()
        val ballLeft = (savedX - ballSizePx / 2f).toInt()
        val ballTop = (savedY - ballSizePx / 2f).toInt()

        val bParams = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

        // 3. ADD FLOATING HUD TOOLBAR
        val hudWidthPx = (300f * density).toInt()
        val hudHeightPx = (46f * density).toInt()
        val hudTop = (16f * density).toInt()
        val hudLeft = ((resources.displayMetrics.widthPixels - hudWidthPx) / 2).coerceAtLeast(0)

        val hParams = WindowManager.LayoutParams(
            hudWidthPx,
            hudHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                stopSelf()
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
            // GAME MODE: Make lines and ball invisible so Android 12+ completely skips occlusion checks, allowing 100% smooth cue shooting!
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            linesOverlayView?.visibility = android.view.View.INVISIBLE
            targetView.visibility = android.view.View.INVISIBLE
        } else {
            // MOVE MODE: Make lines and ball visible so user can position the target circle!
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            linesOverlayView?.visibility = android.view.View.VISIBLE
            targetView.visibility = android.view.View.VISIBLE
        }

        try {
            wm.updateViewLayout(targetView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {}

        val wm = windowManager
        if (wm != null) {
            floatingHudView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
            ballTargetView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
            linesOverlayView?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        }

        floatingHudView = null
        ballTargetView = null
        linesOverlayView = null
        windowManager = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    companion object {
        const val CHANNEL_ID = "chakib_aim_overlay_channel"
        const val NOTIFICATION_ID = 8801
        const val ACTION_STOP_SERVICE = "com.example.ACTION_STOP_SERVICE"

        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
