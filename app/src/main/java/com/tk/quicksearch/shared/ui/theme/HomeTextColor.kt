package com.tk.quicksearch.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import com.tk.quicksearch.search.core.HomeTextColor

val LocalHomeTextColorOverride = staticCompositionLocalOf<HomeTextColor?> { null }

/**
 * Whether the wallpaper or image behind Home and search content reads as dark, with the user's
 * Home text colour taking precedence over the detected value. Null without an image background.
 */
@Composable
fun homeBackgroundIsDark(): Boolean? {
    val imageBackgroundIsDark = LocalImageBackgroundIsDark.current ?: return null
    return when (LocalHomeTextColorOverride.current) {
        HomeTextColor.WHITE -> true
        HomeTextColor.BLACK -> false
        null -> imageBackgroundIsDark
    }
}

/** Uses the wallpaper-derived foreground unless the user chose a Home text colour. */
@Composable
fun homeTextColor(): Color =
        when (LocalHomeTextColorOverride.current) {
            HomeTextColor.WHITE -> Color.White
            HomeTextColor.BLACK -> Color.Black
            null ->
                when (LocalImageBackgroundIsDark.current) {
                    true -> Color.White
                    false -> Color.Black
                    null -> MaterialTheme.colorScheme.onSurface
                }
        }
