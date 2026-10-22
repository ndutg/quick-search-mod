package com.tk.quicksearch.search.searchScreen.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.search.core.SearchBarStyle
import com.tk.quicksearch.shared.ui.theme.DesignTokens

/** Resting outline width for [SearchBarStyle], shared by the live bar and the settings preview. */
internal fun SearchBarStyle.borderWidth(): Dp = borderWidthDp.dp

/** Bar corner radius, shared by the standalone and inset bars. */
internal fun SearchBarStyle.cornerRadius(): Dp = cornerRadiusDp.dp

/** Resting outline color before the welcome-animation fade is applied. */
internal fun SearchBarStyle.borderColor(accent: Color): Color =
    borderColorArgb?.let { Color(it) }
        ?: accent.copy(alpha = DesignTokens.SearchFieldAccentOutlineAlpha)

/** Fill color with the custom color and transparency applied over [themeBackground]. */
internal fun SearchBarStyle.backgroundColor(themeBackground: Color): Color {
    val base = backgroundColorArgb?.let { Color(it) } ?: themeBackground
    return base.copy(alpha = base.alpha * (1f - backgroundTransparency.coerceIn(0f, 1f)))
}
