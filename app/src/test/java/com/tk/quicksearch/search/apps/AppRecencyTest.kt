package com.tk.quicksearch.search.apps

import com.tk.quicksearch.search.models.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRecencyTest {
    @Test
    fun recordedLaunchImmediatelyUpdatesRecencyAndCount() {
        val launched = app("com.example.launched", lastUsedTime = 10L, launchCount = 2)
        val other = app("com.example.other", lastUsedTime = 20L, launchCount = 4)

        val updated =
            applyRecordedAppLaunch(
                apps = listOf(launched, other),
                appKey = launched.launchCountKey(),
                launchTime = 100L,
            )

        assertEquals(100L, updated[0].lastUsedTime)
        assertEquals(3, updated[0].launchCount)
        assertEquals(other, updated[1])
    }

    private fun app(
        packageName: String,
        lastUsedTime: Long,
        launchCount: Int,
    ) =
        AppInfo(
            appName = packageName,
            packageName = packageName,
            lastUsedTime = lastUsedTime,
            totalTimeInForeground = 0L,
            launchCount = launchCount,
            firstInstallTime = 0L,
            isSystemApp = false,
        )
}
