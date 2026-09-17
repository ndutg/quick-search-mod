package com.tk.quicksearch.search.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.tk.quicksearch.search.data.preferences.UpcomingAlarmPreferences

/** Android exposes the next scheduled alarm clock, rather than a list of upcoming alarms. */
class UpcomingAlarmRepository(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = UpcomingAlarmPreferences(context)

    fun nextWithinFortyFiveMinutes(nowMillis: Long = System.currentTimeMillis()): AlarmManager.AlarmClockInfo? {
        val alarm = alarmManager?.nextAlarmClock ?: return null
        val timeUntilAlarm = alarm.triggerTime - nowMillis
        return alarm.takeIf {
            timeUntilAlarm in 1..FORTY_FIVE_MINUTES_MILLIS &&
                !preferences.isDismissed(it.triggerTime)
        }
    }

    fun dismiss(alarm: AlarmManager.AlarmClockInfo) {
        preferences.dismiss(alarm.triggerTime)
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
        private const val FORTY_FIVE_MINUTES_MILLIS = 45 * 60 * 1000L
    }
}
