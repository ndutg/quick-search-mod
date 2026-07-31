package com.tk.quicksearch.search.searchScreen

internal fun shouldCloseSearchSurfaceAfterExternalNavigation(
    autoCloseEnabled: Boolean,
    isOverlayPresentation: Boolean,
    isDefaultLauncher: Boolean,
): Boolean =
    autoCloseEnabled &&
        (isOverlayPresentation || !isDefaultLauncher)
