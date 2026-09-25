package com.tk.quicksearch.search.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import com.tk.quicksearch.search.data.preferences.UpcomingAlarmPreferences

/** Android exposes the next scheduled alarm clock, rather than a list of upcoming alarms. */
class UpcomingAlarmRepository(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = UpcomingAlarmPreferences(context)

    fun nextWithinFortyFiveMinutes(nowMillis: Long = System.currentTimeMillis()): AlarmManager.AlarmClockInfo? {
        if (!preferences.isShowUpcomingAlarmEnabled()) return null
        val alarm = alarmManager?.nextAlarmClock ?: return null
        if (!isFromClockApp(alarm)) return null
        if (alarm.showIntent?.creatorPackage?.let(preferences::isHiddenPackage) == true) return null
        val timeUntilAlarm = alarm.triggerTime - nowMillis
        return alarm.takeIf {
            timeUntilAlarm in 1..FORTY_FIVE_MINUTES_MILLIS &&
                !preferences.isDismissed(it.triggerTime)
        }
    }

    /**
     * Any app can schedule an alarm clock (e.g. reminder or sleep-tracking apps), so only surface
     * alarms created by apps that handle the standard clock-app intents.
     */
    private fun isFromClockApp(alarm: AlarmManager.AlarmClockInfo): Boolean {
        val creatorPackage = alarm.showIntent?.creatorPackage ?: return false
        return CLOCK_APP_ACTIONS.any { action ->
            queryActivityPackages(Intent(action).setPackage(creatorPackage)).isNotEmpty()
        }
    }

    private fun queryActivityPackages(intent: Intent): Set<String> =
        runCatching {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            }
        }.getOrDefault(emptyList())
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

    fun dismiss(alarm: AlarmManager.AlarmClockInfo) {
        preferences.dismiss(alarm.triggerTime)
    }

    /** Hides this and future alarms scheduled by the app that created [alarm]. */
    fun hideAlarmsFromApp(alarm: AlarmManager.AlarmClockInfo) {
        alarm.showIntent?.creatorPackage?.let(preferences::hidePackage)
    }

    fun appLabel(alarm: AlarmManager.AlarmClockInfo): String? {
        val creatorPackage = alarm.showIntent?.creatorPackage ?: return null
        return runCatching {
            val packageManager = context.packageManager
            packageManager.getApplicationInfo(creatorPackage, 0).loadLabel(packageManager).toString()
        }.getOrNull()
    }

    fun open(alarm: AlarmManager.AlarmClockInfo): Boolean {
        val alarmsPage = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            .setPackage(alarm.showIntent.creatorPackage)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(alarmsPage)
            return true
        } catch (_: Exception) {
            // Some clock apps do not handle the standard alarms-page action.
        }
        val launchIntent = alarm.showIntent.creatorPackage?.let(context.packageManager::getLaunchIntentForPackage)
        if (launchIntent != null) {
            try {
                context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
                // Fall back to the alarm-specific show intent.
            }
        }
        return try {
            alarm.showIntent.send()
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        private val CLOCK_APP_ACTIONS =
            listOf(AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM)
        private const val FORTY_FIVE_MINUTES_MILLIS = 45 * 60 * 1000L
    }
}
