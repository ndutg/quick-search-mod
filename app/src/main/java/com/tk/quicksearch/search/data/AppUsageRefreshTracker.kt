package com.tk.quicksearch.search.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import com.tk.quicksearch.search.models.AppInfo
import java.util.concurrent.TimeUnit

/**
 * Keeps app usage metadata fresh between full 30-day usage aggregates, which are expensive for
 * the system to compute. Between full refreshes, only the usage events since the previous refresh
 * are read to update last-used times; launch counts and foreground totals catch up at the next
 * full refresh.
 */
internal class AppUsageRefreshTracker(
    private val usageStatsManager: UsageStatsManager?,
) {
    private var lastFullRefreshElapsedMs = 0L
    private var lastRefreshWallMs = 0L

    @Synchronized
    fun shouldRunFullRefresh(
        nowElapsedMs: Long,
        nowWallMs: Long,
    ): Boolean =
        lastFullRefreshElapsedMs == 0L ||
            nowElapsedMs - lastFullRefreshElapsedMs !in 0 until FULL_REFRESH_INTERVAL_MS ||
            // Usage events are only retained for a few days, and a clock change breaks the window.
            nowWallMs - lastRefreshWallMs !in 0 until MAX_DELTA_WINDOW_MS

    @Synchronized
    fun markFullRefresh(
        nowElapsedMs: Long,
        nowWallMs: Long,
    ) {
        lastFullRefreshElapsedMs = nowElapsedMs
        lastRefreshWallMs = nowWallMs
    }

    /** Latest foreground activity time per package since the previous refresh, or null on failure. */
    @Synchronized
    fun readLastUsedSinceLastRefresh(nowWallMs: Long): Map<String, Long>? {
        val manager = usageStatsManager ?: return null
        val events =
            runCatching { manager.queryEvents(lastRefreshWallMs, nowWallMs) }.getOrNull()
                ?: return null
        val lastUsedByPackage = HashMap<String, Long>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.ACTIVITY_PAUSED,
                -> {
                    val packageName = event.packageName ?: continue
                    val previous = lastUsedByPackage[packageName] ?: 0L
                    if (event.timeStamp > previous) lastUsedByPackage[packageName] = event.timeStamp
                }
            }
        }
        lastRefreshWallMs = nowWallMs
        return lastUsedByPackage
    }

    private companion object {
        val FULL_REFRESH_INTERVAL_MS = TimeUnit.HOURS.toMillis(3)
        val MAX_DELTA_WINDOW_MS = TimeUnit.DAYS.toMillis(1)
    }
}

/** Returns [apps] itself when no app has a newer last-used time, so callers can skip publishing. */
internal fun applyLastUsedTimes(
    apps: List<AppInfo>,
    lastUsedByPackage: Map<String, Long>,
): List<AppInfo> {
    if (lastUsedByPackage.isEmpty()) return apps
    var changed = false
    val updated =
        apps.map { app ->
            val lastUsed = lastUsedByPackage[app.packageName]
            if (lastUsed != null && lastUsed > app.lastUsedTime) {
                changed = true
                app.copy(lastUsedTime = lastUsed)
            } else {
                app
            }
        }
    return if (changed) updated else apps
}
