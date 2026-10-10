package com.example

import android.content.Context
import android.content.SharedPreferences

data class CalibratedTable(
    val tlX: Float = 0.08f, val tlY: Float = 0.10f,
    val trX: Float = 0.92f, val trY: Float = 0.10f,
    val brX: Float = 0.92f, val brY: Float = 0.90f,
    val blX: Float = 0.08f, val blY: Float = 0.90f,
    val isCalibrated: Boolean = false
)

data class AimOverlayConfig(
    val ballRadiusDp: Float = 24f,
    val lineColor: Int = 0xFF00FF66.toInt(), // Neon Green
    val tangentColor: Int = 0xFF00E5FF.toInt(), // Cyber Cyan
    val showRebound: Boolean = true,
    val reboundCount: Int = 1, // 1 or 2 bounces
    val showTangent: Boolean = true,
    val showGuidelines: Boolean = true,
    val lineThicknessDp: Float = 2.5f,
    val showCenterCrosshair: Boolean = true,
    val isLocked: Boolean = false
) {
    companion object {
        private const val PREFS_NAME = "aim_overlay_prefs"
        private const val KEY_BALL_RADIUS = "ball_radius_dp"
        private const val KEY_LINE_COLOR = "line_color"
        private const val KEY_TANGENT_COLOR = "tangent_color"
        private const val KEY_SHOW_REBOUND = "show_rebound"
        private const val KEY_REBOUND_COUNT = "rebound_count"
        private const val KEY_SHOW_TANGENT = "show_tangent"
        private const val KEY_SHOW_GUIDELINES = "show_guidelines"
        private const val KEY_LINE_THICKNESS = "line_thickness_dp"
        private const val KEY_SHOW_CROSSHAIR = "show_crosshair"
        private const val KEY_IS_LOCKED = "is_locked"

        // Calibration Keys (localStorage / SharedPreferences persistence)
        private const val KEY_CAL_TL_X = "cal_tl_x"
        private const val KEY_CAL_TL_Y = "cal_tl_y"
        private const val KEY_CAL_TR_X = "cal_tr_x"
        private const val KEY_CAL_TR_Y = "cal_tr_y"
        private const val KEY_CAL_BR_X = "cal_br_x"
        private const val KEY_CAL_BR_Y = "cal_br_y"
        private const val KEY_CAL_BL_X = "cal_bl_x"
        private const val KEY_CAL_BL_Y = "cal_bl_y"
        private const val KEY_CAL_SAVED = "cal_saved"

        const val ACTION_CONFIG_CHANGED = "com.example.ACTION_CONFIG_CHANGED"
        const val ACTION_STOP_OVERLAY = "com.example.ACTION_STOP_OVERLAY"

        fun load(context: Context): AimOverlayConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AimOverlayConfig(
                ballRadiusDp = prefs.getFloat(KEY_BALL_RADIUS, 24f),
                lineColor = prefs.getInt(KEY_LINE_COLOR, 0xFF00FF66.toInt()),
                tangentColor = prefs.getInt(KEY_TANGENT_COLOR, 0xFF00E5FF.toInt()),
                showRebound = prefs.getBoolean(KEY_SHOW_REBOUND, true),
                reboundCount = prefs.getInt(KEY_REBOUND_COUNT, 1),
                showTangent = prefs.getBoolean(KEY_SHOW_TANGENT, true),
                showGuidelines = prefs.getBoolean(KEY_SHOW_GUIDELINES, true),
                lineThicknessDp = prefs.getFloat(KEY_LINE_THICKNESS, 2.5f),
                showCenterCrosshair = prefs.getBoolean(KEY_SHOW_CROSSHAIR, true),
                isLocked = prefs.getBoolean(KEY_IS_LOCKED, false)
            )
        }

        fun save(context: Context, config: AimOverlayConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_BALL_RADIUS, config.ballRadiusDp)
                .putInt(KEY_LINE_COLOR, config.lineColor)
                .putInt(KEY_TANGENT_COLOR, config.tangentColor)
                .putBoolean(KEY_SHOW_REBOUND, config.showRebound)
                .putInt(KEY_REBOUND_COUNT, config.reboundCount)
                .putBoolean(KEY_SHOW_TANGENT, config.showTangent)
                .putBoolean(KEY_SHOW_GUIDELINES, config.showGuidelines)
                .putFloat(KEY_LINE_THICKNESS, config.lineThicknessDp)
                .putBoolean(KEY_SHOW_CROSSHAIR, config.showCenterCrosshair)
                .putBoolean(KEY_IS_LOCKED, config.isLocked)
                .apply()
        }

        fun loadCalibration(context: Context): CalibratedTable {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isCal = prefs.getBoolean(KEY_CAL_SAVED, false)
            return CalibratedTable(
                tlX = prefs.getFloat(KEY_CAL_TL_X, 0.08f),
                tlY = prefs.getFloat(KEY_CAL_TL_Y, 0.10f),
                trX = prefs.getFloat(KEY_CAL_TR_X, 0.92f),
                trY = prefs.getFloat(KEY_CAL_TR_Y, 0.10f),
                brX = prefs.getFloat(KEY_CAL_BR_X, 0.92f),
                brY = prefs.getFloat(KEY_CAL_BR_Y, 0.90f),
                blX = prefs.getFloat(KEY_CAL_BL_X, 0.08f),
                blY = prefs.getFloat(KEY_CAL_BL_Y, 0.90f),
                isCalibrated = isCal
            )
        }

        fun saveCalibration(context: Context, table: CalibratedTable) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat(KEY_CAL_TL_X, table.tlX)
                .putFloat(KEY_CAL_TL_Y, table.tlY)
                .putFloat(KEY_CAL_TR_X, table.trX)
                .putFloat(KEY_CAL_TR_Y, table.trY)
                .putFloat(KEY_CAL_BR_X, table.brX)
                .putFloat(KEY_CAL_BR_Y, table.brY)
                .putFloat(KEY_CAL_BL_X, table.blX)
                .putFloat(KEY_CAL_BL_Y, table.blY)
                .putBoolean(KEY_CAL_SAVED, true)
                .apply()
        }

        fun resetCalibration(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_CAL_SAVED, false).apply()
        }
    }
}
