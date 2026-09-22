package com.tk.quicksearch.widgets.mediaControlsWidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class MediaControlsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediaControlsWidget()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)

        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED ||
            intent.action == Intent.ACTION_WALLPAPER_CHANGED
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, MediaControlsWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val updateIntent =
                Intent(context, MediaControlsWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
            context.sendBroadcast(updateIntent)
        }
    }
}
