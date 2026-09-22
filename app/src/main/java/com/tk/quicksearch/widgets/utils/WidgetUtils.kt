package com.tk.quicksearch.widgets.utils

import android.annotation.TargetApi
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.tk.quicksearch.R
import com.tk.quicksearch.widgets.customButtonsWidget.CustomButtonsWidgetReceiver
import com.tk.quicksearch.widgets.searchWidget.SearchWidget
import com.tk.quicksearch.widgets.searchWidget.SearchWidgetReceiver

private val MediaPlaybackRevisionKey = longPreferencesKey("media_playback_revision")

fun requestAddQuickSearchWidget(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        requestPinForAndroidOPlus(context)
    } else {
        showUnsupportedVersionToast(context)
    }
}

@TargetApi(Build.VERSION_CODES.O)
private fun requestPinForAndroidOPlus(context: Context) {
    val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
    if (appWidgetManager?.isRequestPinAppWidgetSupported != true) {
        showUnsupportedVersionToast(context)
        return
    }

    val provider = ComponentName(context, SearchWidgetReceiver::class.java)
    try {
        val requested = appWidgetManager.requestPinAppWidget(provider, null, null)
        if (!requested) {
            showErrorToast(context)
        }
    } catch (e: Exception) {
        showErrorToast(context)
    }
}

private fun showUnsupportedVersionToast(context: Context) {
    showToast(context, R.string.home_screen_widget_not_supported, Toast.LENGTH_LONG)
}

private fun showErrorToast(context: Context) {
    showToast(context, R.string.home_screen_widget_error)
}

private fun showToast(
    context: Context,
    messageResId: Int,
    duration: Int = Toast.LENGTH_SHORT,
) {
    Toast.makeText(context, context.getString(messageResId), duration).show()
}

/**
 * Redraws every placed search and custom-buttons widget instance, e.g. so a Play/Pause button's
 * icon reflects a playback-state change that just happened outside of this app.
 */
suspend fun refreshAllSearchWidgets(context: Context) {
    refreshWidgets(context, SearchWidgetReceiver::class.java, WidgetVariant.STANDARD)
    refreshWidgets(context, CustomButtonsWidgetReceiver::class.java, WidgetVariant.CUSTOM_BUTTONS_ONLY)
}

private suspend fun refreshWidgets(
    context: Context,
    receiverClass: Class<*>,
    variant: WidgetVariant,
) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val componentName = ComponentName(context, receiverClass)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
    val glanceManager = GlanceAppWidgetManager(context)
    appWidgetIds.forEach { appWidgetId ->
        runCatching { glanceManager.getGlanceIdBy(appWidgetId) }
            .getOrNull()
            ?.let { glanceId ->
                // SearchWidget observes its Glance preferences. Advancing this otherwise-private
                // revision makes an already running Glance composition re-evaluate the live
                // playback icon instead of coalescing the request as an unchanged update.
                updateAppWidgetState(context, glanceId) { preferences ->
                    preferences[MediaPlaybackRevisionKey] =
                        (preferences[MediaPlaybackRevisionKey] ?: 0L) + 1L
                }
                SearchWidget(variant).update(context, glanceId)
            }
    }
}
