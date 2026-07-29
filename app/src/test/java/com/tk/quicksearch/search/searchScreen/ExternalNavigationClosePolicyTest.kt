package com.tk.quicksearch.search.searchScreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalNavigationClosePolicyTest {
    @Test
    fun `default launcher stays alive after opening an external result`() {
        assertFalse(
            shouldCloseSearchSurfaceAfterExternalNavigation(
                autoCloseEnabled = true,
                isOverlayPresentation = false,
                isDefaultLauncher = true,
            ),
        )
    }

    @Test
    fun `regular app still honors auto close`() {
        assertTrue(
            shouldCloseSearchSurfaceAfterExternalNavigation(
                autoCloseEnabled = true,
                isOverlayPresentation = false,
                isDefaultLauncher = false,
            ),
        )
    }

    @Test
    fun `overlay still honors auto close when app is default launcher`() {
        assertTrue(
            shouldCloseSearchSurfaceAfterExternalNavigation(
                autoCloseEnabled = true,
                isOverlayPresentation = true,
                isDefaultLauncher = true,
            ),
        )
    }

    @Test
    fun `disabled auto close keeps every search surface alive`() {
        assertFalse(
            shouldCloseSearchSurfaceAfterExternalNavigation(
                autoCloseEnabled = false,
                isOverlayPresentation = false,
                isDefaultLauncher = false,
            ),
        )
        assertFalse(
            shouldCloseSearchSurfaceAfterExternalNavigation(
                autoCloseEnabled = false,
                isOverlayPresentation = true,
                isDefaultLauncher = false,
            ),
        )
    }
}
