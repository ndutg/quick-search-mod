package com.tk.quicksearch.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope

internal const val TARGET_PACKAGE = "com.tk.quicksearch"

internal fun MacrobenchmarkScope.launchFromAppIcon(initialQuery: String? = null) {
    startActivityAndWait(mainIntent(initialQuery = initialQuery))
    device.waitForIdle(IDLE_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.launchAsHome(initialQuery: String? = null) {
    startActivityAndWait(
        mainIntent(
            componentClass = "$TARGET_PACKAGE.app.HomeActivity",
            category = Intent.CATEGORY_HOME,
            initialQuery = initialQuery,
        ),
    )
    device.waitForIdle(IDLE_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.launchWithInitialQuery(query: String) {
    startActivityAndWait(mainIntent(initialQuery = query, category = null))
    device.waitForIdle(IDLE_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.launchSettings() {
    startActivityAndWait(mainIntent(category = null).putExtra("overlay_open_settings", true))
    device.waitForIdle(IDLE_TIMEOUT_MS)
}

internal fun MacrobenchmarkScope.launchOverlay(initialQuery: String? = null) {
    startActivityAndWait(
        Intent().apply {
            component = ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.overlay.OverlayActivity")
            initialQuery?.let { query -> putExtra("overlay_initial_query", query) }
        },
    )
    device.waitForIdle(IDLE_TIMEOUT_MS)
}

private fun mainIntent(
    componentClass: String = "$TARGET_PACKAGE.app.MainActivity",
    category: String? = Intent.CATEGORY_LAUNCHER,
    initialQuery: String? = null,
): Intent =
    Intent(Intent.ACTION_MAIN).apply {
        component = ComponentName(TARGET_PACKAGE, componentClass)
        category?.let(::addCategory)
        putExtra("overlay_force_normal_launch", true)
        putExtra("com.tk.quicksearch.extra.BASELINE_PROFILE_SKIP_ONBOARDING", true)
        initialQuery?.let { query -> putExtra("query", query) }
    }

private const val IDLE_TIMEOUT_MS = 1_000L
