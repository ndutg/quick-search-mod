package com.tk.quicksearch.search.data.preferences

import com.tk.quicksearch.search.core.AccentColorMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentColorModeResolverTest {
    @Test
    fun savedModeTakesPrecedenceOverLegacyBoolean() {
        assertEquals(
            AccentColorMode.CUSTOM,
            UiPreferences.resolveAccentColorMode(
                savedModeName = AccentColorMode.CUSTOM.name,
                wallpaperAccentEnabled = true,
            ),
        )
    }

    @Test
    fun missingModeMigratesEnabledWallpaperAccent() {
        assertEquals(
            AccentColorMode.FROM_WALLPAPER,
            UiPreferences.resolveAccentColorMode(
                savedModeName = null,
                wallpaperAccentEnabled = true,
            ),
        )
    }

    @Test
    fun missingModeMigratesDisabledWallpaperAccent() {
        assertEquals(
            AccentColorMode.NONE,
            UiPreferences.resolveAccentColorMode(
                savedModeName = null,
                wallpaperAccentEnabled = false,
            ),
        )
    }

    @Test
    fun invalidSavedModeFallsBackToLegacyBoolean() {
        assertEquals(
            AccentColorMode.NONE,
            UiPreferences.resolveAccentColorMode(
                savedModeName = "not-a-mode",
                wallpaperAccentEnabled = false,
            ),
        )
    }
}
