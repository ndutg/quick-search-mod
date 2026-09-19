package com.tk.quicksearch.search.data.preferences

import android.content.Context
import android.content.SharedPreferences

enum class EdgeGestureSide {
    LEFT,
    RIGHT,
}

/** Placement of the system-wide edge swipe handle drawn by the accessibility service. */
data class EdgeGestureConfig(
    val enabled: Boolean = false,
    val side: EdgeGestureSide = EdgeGestureSide.RIGHT,
    /** Vertical center of the handle as a fraction of the screen height. */
    val position: Float = DEFAULT_POSITION,
    /** Handle length as a fraction of the screen height. */
    val size: Float = DEFAULT_SIZE,
    /** Handle thickness in dp. */
    val widthDp: Int = DEFAULT_WIDTH_DP,
    /** Handle opacity; 0 keeps it invisible. */
    val opacity: Float = DEFAULT_OPACITY,
    /** Gap between the screen edge and the handle, in dp. */
    val offsetDp: Int = DEFAULT_OFFSET_DP,
) {
    companion object {
        const val DEFAULT_POSITION = 0.4f
        const val DEFAULT_SIZE = 0.25f
        const val DEFAULT_WIDTH_DP = 16
        const val DEFAULT_OPACITY = 0f

        /**
         * The system owns a strip along both side edges for the back gesture, and on some OEMs
         * (One UI) that strip swallows touches before any app window sees them. The handle sits
         * clear of it by default.
         */
        const val DEFAULT_OFFSET_DP = 32
        const val MIN_OFFSET_DP = 0
        const val MAX_OFFSET_DP = 96
        const val MIN_SIZE = 0.1f
        const val MAX_SIZE = 1f
        const val MIN_WIDTH_DP = 8
        const val MAX_WIDTH_DP = 48
    }
}

class EdgeGesturePreferences(
    context: Context,
) : BasePreferences(context) {
    fun getConfig(): EdgeGestureConfig =
        EdgeGestureConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            side =
                prefs.getString(KEY_SIDE, null)
                    ?.let { name -> EdgeGestureSide.entries.firstOrNull { it.name == name } }
                    ?: EdgeGestureSide.RIGHT,
            position = prefs.getFloat(KEY_POSITION, EdgeGestureConfig.DEFAULT_POSITION).coerceIn(0f, 1f),
            size =
                prefs.getFloat(KEY_SIZE, EdgeGestureConfig.DEFAULT_SIZE)
                    .coerceIn(EdgeGestureConfig.MIN_SIZE, EdgeGestureConfig.MAX_SIZE),
            widthDp =
                prefs.getInt(KEY_WIDTH_DP, EdgeGestureConfig.DEFAULT_WIDTH_DP)
                    .coerceIn(EdgeGestureConfig.MIN_WIDTH_DP, EdgeGestureConfig.MAX_WIDTH_DP),
            opacity = prefs.getFloat(KEY_OPACITY, EdgeGestureConfig.DEFAULT_OPACITY).coerceIn(0f, 1f),
            offsetDp =
                prefs.getInt(KEY_OFFSET_DP, EdgeGestureConfig.DEFAULT_OFFSET_DP)
                    .coerceIn(EdgeGestureConfig.MIN_OFFSET_DP, EdgeGestureConfig.MAX_OFFSET_DP),
        )

    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun setSide(side: EdgeGestureSide) = prefs.edit().putString(KEY_SIDE, side.name).apply()

    fun setPosition(position: Float) = prefs.edit().putFloat(KEY_POSITION, position).apply()

    fun setSize(size: Float) = prefs.edit().putFloat(KEY_SIZE, size).apply()

    fun setWidthDp(widthDp: Int) = prefs.edit().putInt(KEY_WIDTH_DP, widthDp).apply()

    fun setOpacity(opacity: Float) = prefs.edit().putFloat(KEY_OPACITY, opacity).apply()

    fun setOffsetDp(offsetDp: Int) = prefs.edit().putInt(KEY_OFFSET_DP, offsetDp).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val KEY_ENABLED = "edge_gesture_enabled"
        private const val KEY_SIDE = "edge_gesture_side"
        private const val KEY_POSITION = "edge_gesture_position"
        private const val KEY_SIZE = "edge_gesture_size"
        private const val KEY_WIDTH_DP = "edge_gesture_width_dp"
        private const val KEY_OPACITY = "edge_gesture_opacity"
        private const val KEY_OFFSET_DP = "edge_gesture_offset_dp"

        fun isEdgeGestureKey(key: String?): Boolean = key?.startsWith("edge_gesture_") == true
    }
}
