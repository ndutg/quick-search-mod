package com.tk.quicksearch.widgets.countdownWidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.tk.quicksearch.widgets.utils.refreshCountdownWidgets
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val countdownRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Redraws Countdown widgets when the day changes (a midnight alarm, plus the system's clock, time
 * zone and locale broadcasts). The provider's 30-minute update period keeps the progress bar moving.
 */
class CountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_DAY_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED,
            -> {
                val pendingResult = goAsync()
                val appContext = context.applicationContext
                countdownRefreshScope.launch {
                    try {
                        runCatching { refreshCountdownWidgets(appContext) }
                        scheduleNextDayRefresh(appContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleNextDayRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        context.getSystemService(AlarmManager::class.java)?.cancel(dayChangedIntent(context))
    }

    companion object {
        private const val ACTION_DAY_CHANGED = "com.tk.quicksearch.widgets.countdownWidget.DAY_CHANGED"

        /** Delay past midnight so the new date is already current when the widget redraws. */
        private const val AFTER_MIDNIGHT_MILLIS = 60_000L

        /** An inexact alarm shortly after the next local midnight; replaces any earlier one. */
        fun scheduleNextDayRefresh(context: Context) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val zone = ZoneId.systemDefault()
            val nextMidnight =
                LocalDate
                    .now(zone)
                    .plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            runCatching {
                alarmManager.set(AlarmManager.RTC, nextMidnight + AFTER_MIDNIGHT_MILLIS, dayChangedIntent(context))
            }
        }

        private fun dayChangedIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, CountdownWidgetReceiver::class.java).setAction(ACTION_DAY_CHANGED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
