package com.tk.quicksearch.search.data.preferences

import android.content.Context
import android.content.SharedPreferences

/** Appearance and placement of the system-wide floating search button drawn by the accessibility service. */
data class FloatingButtonConfig(
    val enabled: Boolean = false,
    /** Button diameter in dp. */
    val sizeDp: Int = DEFAULT_SIZE_DP,
    /** Button opacity. */
    val opacity: Float = DEFAULT_OPACITY,
    /** Horizontal position as a fraction of the space the button can move across. */
    val positionX: Float = DEFAULT_POSITION_X,
    /** Vertical position as a fraction of the space the button can move across. */
    val positionY: Float = DEFAULT_POSITION_Y,
) {
    companion object {
        const val DEFAULT_SIZE_DP = 40
        const val DEFAULT_OPACITY = 0.3f
        const val DEFAULT_POSITION_X = 1f
        const val DEFAULT_POSITION_Y = 0.6f

        const val MIN_SIZE_DP = 36
        const val MAX_SIZE_DP = 80
        const val MIN_OPACITY = 0.1f
        const val MAX_OPACITY = 1f
    }
}

class FloatingButtonPreferences(
    context: Context,
) : BasePreferences(context) {
    fun getConfig(): FloatingButtonConfig =
        FloatingButtonConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            sizeDp =
                prefs.getInt(KEY_SIZE_DP, FloatingButtonConfig.DEFAULT_SIZE_DP)
                    .coerceIn(FloatingButtonConfig.MIN_SIZE_DP, FloatingButtonConfig.MAX_SIZE_DP),
            opacity =
                prefs.getFloat(KEY_OPACITY, FloatingButtonConfig.DEFAULT_OPACITY)
                    .coerceIn(FloatingButtonConfig.MIN_OPACITY, FloatingButtonConfig.MAX_OPACITY),
            positionX = prefs.getFloat(KEY_POSITION_X, FloatingButtonConfig.DEFAULT_POSITION_X).coerceIn(0f, 1f),
            positionY = prefs.getFloat(KEY_POSITION_Y, FloatingButtonConfig.DEFAULT_POSITION_Y).coerceIn(0f, 1f),
        )

    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()

    fun setSizeDp(sizeDp: Int) = prefs.edit().putInt(KEY_SIZE_DP, sizeDp).apply()

    fun setOpacity(opacity: Float) = prefs.edit().putFloat(KEY_OPACITY, opacity).apply()

    fun setPosition(
        positionX: Float,
        positionY: Float,
    ) = prefs.edit().putFloat(KEY_POSITION_X, positionX).putFloat(KEY_POSITION_Y, positionY).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val KEY_ENABLED = "floating_button_enabled"
        private const val KEY_SIZE_DP = "floating_button_size_dp"
        private const val KEY_OPACITY = "floating_button_opacity"
        private const val KEY_POSITION_X = "floating_button_position_x"
        private const val KEY_POSITION_Y = "floating_button_position_y"
        fun isFloatingButtonKey(key: String?): Boolean = key?.startsWith("floating_button_") == true
    }
}
