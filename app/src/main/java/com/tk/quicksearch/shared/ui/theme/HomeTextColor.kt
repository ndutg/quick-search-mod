package com.tk.quicksearch.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import com.tk.quicksearch.search.core.HomeTextColor

val LocalHomeTextColorOverride = staticCompositionLocalOf<HomeTextColor?> { null }

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
