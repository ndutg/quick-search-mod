package com.tk.quicksearch.search.folders

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import com.tk.quicksearch.shared.ui.theme.LocalAppIsDarkTheme

// A folder's picked color is never shown as is: its hue is kept, while saturation is capped and
// lightness fixed per theme, so any pick reads as a calm tone that keeps icons and text legible.
private const val BackdropDarkSaturation = 0.45f
private const val BackdropDarkLightness = 0.32f
private const val BackdropLightSaturation = 0.6f
private const val BackdropLightLightness = 0.82f
private const val BackdropWallpaperAlpha = 0.8f
private const val PopupDarkSaturation = 0.3f
private const val PopupDarkLightness = 0.16f
private const val PopupLightSaturation = 0.5f
private const val PopupLightLightness = 0.94f

/** The folder tile's backdrop tone for [color] in the current theme. */
@Composable
internal fun folderColorBackdrop(color: Int, showWallpaperBackground: Boolean): Color {
    val isDark = LocalAppIsDarkTheme.current
    val tone =
            if (isDark) {
                folderTone(color, BackdropDarkSaturation, BackdropDarkLightness)
            } else {
                folderTone(color, BackdropLightSaturation, BackdropLightLightness)
            }
    return if (showWallpaperBackground) tone.copy(alpha = BackdropWallpaperAlpha) else tone
}

/** The opened folder popup's background tone for [color] in the current theme. */
@Composable
internal fun folderColorPopupBackground(color: Int): Color =
        if (LocalAppIsDarkTheme.current) {
            folderTone(color, PopupDarkSaturation, PopupDarkLightness)
        } else {
            folderTone(color, PopupLightSaturation, PopupLightLightness)
        }

private fun folderTone(color: Int, maxSaturation: Float, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = hsl[1].coerceAtMost(maxSaturation)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}
