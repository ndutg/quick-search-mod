package com.tk.quicksearch.search.core

/**
 * User customization of the persistent search bar. Defaults reproduce the stock look:
 * a pill-shaped bar with a 1dp accent outline over the theme's search bar background.
 *
 * @property borderColorArgb custom outline color, or null to follow the accent color.
 * @property backgroundColorArgb custom fill color, or null to follow the theme.
 * @property backgroundTransparency 0 keeps the fill's own alpha, 1 makes it fully clear.
 * @property showMicIcon shows a voice search button in the bar, which moves the settings icon
 *   outside to the bar's right. Off by default.
 */
data class SearchBarStyle(
    val borderWidthDp: Float = DEFAULT_BORDER_WIDTH_DP,
    val borderColorArgb: Int? = null,
    val backgroundColorArgb: Int? = null,
    val backgroundTransparency: Float = DEFAULT_BACKGROUND_TRANSPARENCY,
    val cornerRadiusDp: Float = DEFAULT_CORNER_RADIUS_DP,
    val showMicIcon: Boolean = false,
) {
    val isDefault: Boolean
        get() = this == SearchBarStyle()

    val hasCustomBorder: Boolean
        get() = borderWidthDp != DEFAULT_BORDER_WIDTH_DP || borderColorArgb != null

    companion object {
        const val DEFAULT_BORDER_WIDTH_DP = 1f
        const val MIN_BORDER_WIDTH_DP = 0f
        const val MAX_BORDER_WIDTH_DP = 4f
        const val DEFAULT_BACKGROUND_TRANSPARENCY = 0f
        /** Half the bar's resting height, so the default bar is a full pill. */
        const val DEFAULT_CORNER_RADIUS_DP = 28f
        const val MIN_CORNER_RADIUS_DP = 0f
        const val MAX_CORNER_RADIUS_DP = 28f
    }
}
