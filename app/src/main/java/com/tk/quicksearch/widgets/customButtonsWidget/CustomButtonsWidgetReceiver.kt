package com.tk.quicksearch.widgets.customButtonsWidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.tk.quicksearch.widgets.utils.WidgetVariant
import com.tk.quicksearch.widgets.searchWidget.SearchWidget

class CustomButtonsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SearchWidget(WidgetVariant.CUSTOM_BUTTONS_ONLY)

    // ACTION_WALLPAPER_CHANGED is deprecated but still the broadcast that signals a new wallpaper.
    @Suppress("DEPRECATION")
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)

        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED ||
            intent.action == Intent.ACTION_WALLPAPER_CHANGED
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CustomButtonsWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            val updateIntent =
                Intent(context, CustomButtonsWidgetReceiver::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                }
            context.sendBroadcast(updateIntent)
        }
    }
}
