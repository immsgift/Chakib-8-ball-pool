package com.example

import android.content.Context
import android.content.SharedPreferences

data class CalibratedTable(
    val leftRatio: Float = 0.08f,
    val topRatio: Float = 0.14f,
    val rightRatio: Float = 0.92f,
    val bottomRatio: Float = 0.86f,
    val isLandscape: Boolean = true,
    val isCalibrated: Boolean = true
) {
    fun getPockets(width: Float, height: Float): List<PocketPoint> {
        val l = leftRatio * width
        val t = topRatio * height
        val r = rightRatio * width
        val b = bottomRatio * height
        val midX = (l + r) / 2f

        return listOf(
            PocketPoint("TL", l, t),
            PocketPoint("TC", midX, t),
            PocketPoint("TR", r, t),
            PocketPoint("BL", l, b),
            PocketPoint("BC", midX, b),
            PocketPoint("BR", r, b)
        )
    }
}

data class PocketPoint(
    val id: String,
    val x: Float,
    val y: Float
)

data class AimOverlayConfig(
    val ballRadiusDp: Float = 26f,
    val lineColor: Int = 0xFF00FF66.toInt(), // Neon Green
    val pocketLineColor: Int = 0xFF00E5FF.toInt(), // Cyber Cyan
    val bestPocketColor: Int = 0xFFFFD700.toInt(), // Gold
    val showRebound: Boolean = true,
    val reboundCount: Int = 1, // 0 = off, 1 = 1-bounce, 2 = 2-bounces
    val showGuidelines: Boolean = true,
    val lineThicknessDp: Float = 2.5f,
    val showCenterCrosshair: Boolean = true,
    val isGameModeTouchThrough: Boolean = false // When true, passes 100% touches through to 8 Ball Pool
) {
    companion object {
        private const val PREFS_NAME = "chakib_aiming_prefs"
        private const val KEY_BALL_RADIUS = "ball_radius_dp"
        private const val KEY_LINE_COLOR = "line_color"
        private const val KEY_POCKET_LINE_COLOR = "pocket_line_color"
        private const val KEY_BEST_POCKET_COLOR = "best_pocket_color"
        private const val KEY_SHOW_REBOUND = "show_rebound"
        private const val KEY_REBOUND_COUNT = "rebound_count"
        private const val KEY_SHOW_GUIDELINES = "show_guidelines"
        private const val KEY_LINE_THICKNESS = "line_thickness_dp"
        private const val KEY_SHOW_CROSSHAIR = "show_crosshair"
        private const val KEY_GAME_MODE = "is_game_mode"

        // Calibration Keys (Table Measurement / "القيس")
        private const val KEY_CAL_LEFT = "cal_left"
        private const val KEY_CAL_TOP = "cal_top"
        private const val KEY_CAL_RIGHT = "cal_right"
        private const val KEY_CAL_BOTTOM = "cal_bottom"
        private const val KEY_CAL_LANDSCAPE = "cal_landscape"
        private const val KEY_CAL_SAVED = "cal_saved"

        // Ball Position Persistence
        private const val KEY_BALL_X = "ball_pos_x"
        private const val KEY_BALL_Y = "ball_pos_y"
        private const val KEY_AIM_ANGLE = "aim_angle"

        const val ACTION_CONFIG_CHANGED = "com.example.ACTION_CONFIG_CHANGED"
        const val ACTION_STOP_OVERLAY = "com.example.ACTION_STOP_OVERLAY"

        fun load(context: Context): AimOverlayConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AimOverlayConfig(
                ballRadiusDp = prefs.getFloat(KEY_BALL_RADIUS, 26f),
                lineColor = prefs.getInt(KEY_LINE_COLOR, 0xFF00FF66.toInt()),
                pocketLineColor = prefs.getInt(KEY_POCKET_LINE_COLOR, 0xFF00E5FF.toInt()),
                bestPocketColor = prefs.getInt(KEY_BEST_POCKET_COLOR, 0xFFFFD700.toInt()),
                showRebound = prefs.getBoolean(KEY_SHOW_REBOUND, true),
                reboundCount = prefs.getInt(KEY_REBOUND_COUNT, 1),
                showGuidelines = prefs.getBoolean(KEY_SHOW_GUIDELINES, true),
                lineThicknessDp = prefs.getFloat(KEY_LINE_THICKNESS, 2.5f),
                showCenterCrosshair = prefs.getBoolean(KEY_SHOW_CROSSHAIR, true),
                isGameModeTouchThrough = prefs.getBoolean(KEY_GAME_MODE, false)
            )
        }

        fun save(context: Context, config: AimOverlayConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_BALL_RADIUS, config.ballRadiusDp)
                .putInt(KEY_LINE_COLOR, config.lineColor)
                .putInt(KEY_POCKET_LINE_COLOR, config.pocketLineColor)
                .putInt(KEY_BEST_POCKET_COLOR, config.bestPocketColor)
                .putBoolean(KEY_SHOW_REBOUND, config.showRebound)
                .putInt(KEY_REBOUND_COUNT, config.reboundCount)
                .putBoolean(KEY_SHOW_GUIDELINES, config.showGuidelines)
                .putFloat(KEY_LINE_THICKNESS, config.lineThicknessDp)
                .putBoolean(KEY_SHOW_CROSSHAIR, config.showCenterCrosshair)
                .putBoolean(KEY_GAME_MODE, config.isGameModeTouchThrough)
                .apply()
        }

        fun loadCalibration(context: Context): CalibratedTable {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isCal = prefs.getBoolean(KEY_CAL_SAVED, true)
            return CalibratedTable(
                leftRatio = prefs.getFloat(KEY_CAL_LEFT, 0.08f),
                topRatio = prefs.getFloat(KEY_CAL_TOP, 0.14f),
                rightRatio = prefs.getFloat(KEY_CAL_RIGHT, 0.92f),
                bottomRatio = prefs.getFloat(KEY_CAL_BOTTOM, 0.86f),
                isLandscape = prefs.getBoolean(KEY_CAL_LANDSCAPE, true),
                isCalibrated = isCal
            )
        }

        fun saveCalibration(context: Context, table: CalibratedTable) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_CAL_LEFT, table.leftRatio)
                .putFloat(KEY_CAL_TOP, table.topRatio)
                .putFloat(KEY_CAL_RIGHT, table.rightRatio)
                .putFloat(KEY_CAL_BOTTOM, table.bottomRatio)
                .putBoolean(KEY_CAL_LANDSCAPE, table.isLandscape)
                .putBoolean(KEY_CAL_SAVED, true)
                .apply()
        }

        fun saveBallState(context: Context, x: Float, y: Float, angle: Float) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_BALL_X, x)
                .putFloat(KEY_BALL_Y, y)
                .putFloat(KEY_AIM_ANGLE, angle)
                .apply()
        }

        fun loadBallState(context: Context, defaultX: Float, defaultY: Float): Triple<Float, Float, Float> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val x = prefs.getFloat(KEY_BALL_X, defaultX)
            val y = prefs.getFloat(KEY_BALL_Y, defaultY)
            val angle = prefs.getFloat(KEY_AIM_ANGLE, 270f)
            return Triple(x, y, angle)
        }
    }
}
