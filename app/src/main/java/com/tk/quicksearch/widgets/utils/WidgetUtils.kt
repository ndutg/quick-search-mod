package com.tk.quicksearch.widgets.utils

import android.annotation.TargetApi
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.tk.quicksearch.R
import com.tk.quicksearch.widgets.customButtonsWidget.CustomButtonsWidgetReceiver
import com.tk.quicksearch.widgets.mediaControlsWidget.MediaControlsWidget
import com.tk.quicksearch.widgets.mediaControlsWidget.MediaControlsWidgetReceiver
import com.tk.quicksearch.widgets.noteWidget.NoteWidget
import com.tk.quicksearch.widgets.noteWidget.NoteWidgetReceiver
import com.tk.quicksearch.widgets.searchWidget.SearchWidget
import com.tk.quicksearch.widgets.searchWidget.SearchWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val MediaPlaybackRevisionKey = longPreferencesKey("media_playback_revision")

private val noteWidgetRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
 * Redraws every placed search, custom-buttons and media controls widget instance, e.g. so a
 * Play/Pause button's icon reflects a playback-state change that just happened outside of this app.
 */
suspend fun refreshAllWidgets(context: Context) {
    refreshWidgets(context, SearchWidgetReceiver::class.java, SearchWidget(WidgetVariant.STANDARD))
    refreshWidgets(context, CustomButtonsWidgetReceiver::class.java, SearchWidget(WidgetVariant.CUSTOM_BUTTONS_ONLY))
    refreshMediaControlsWidgets(context)
}

/** Redraws placed media controls widgets, e.g. when the playing track's title or artwork changes. */
suspend fun refreshMediaControlsWidgets(context: Context) {
    refreshWidgets(context, MediaControlsWidgetReceiver::class.java, MediaControlsWidget())
}

/** Redraws placed Note widgets, e.g. after a note is edited, deleted or restored from a backup. */
suspend fun refreshNoteWidgets(context: Context) {
    refreshWidgets(context, NoteWidgetReceiver::class.java, NoteWidget())
}

/** Fire-and-forget [refreshNoteWidgets] for callers outside a coroutine. */
fun requestNoteWidgetsRefresh(context: Context) {
    val appContext = context.applicationContext
    // Devices without widget support have no AppWidgetManager.
    val hasNoteWidgets =
        runCatching {
            AppWidgetManager
                .getInstance(appContext)
                .getAppWidgetIds(ComponentName(appContext, NoteWidgetReceiver::class.java))
                .isNotEmpty()
        }.getOrDefault(false)
    if (!hasNoteWidgets) return
    noteWidgetRefreshScope.launch { runCatching { refreshNoteWidgets(appContext) } }
}

/** The Glance widget that renders [variant]'s placed instances. */
fun glanceWidgetFor(variant: WidgetVariant): GlanceAppWidget =
    when (variant) {
        WidgetVariant.STANDARD,
        WidgetVariant.CUSTOM_BUTTONS_ONLY,
        -> SearchWidget(variant)
        WidgetVariant.MEDIA_CONTROLS -> MediaControlsWidget()
        WidgetVariant.NOTE -> NoteWidget()
    }

private suspend fun refreshWidgets(
    context: Context,
    receiverClass: Class<*>,
    widget: GlanceAppWidget,
) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val componentName = ComponentName(context, receiverClass)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
    val glanceManager = GlanceAppWidgetManager(context)
    appWidgetIds.forEach { appWidgetId ->
        runCatching { glanceManager.getGlanceIdBy(appWidgetId) }
            .getOrNull()
            ?.let { glanceId ->
                // The widgets observe their Glance preferences. Advancing this otherwise-private
                // revision makes an already running Glance composition re-read live data (playback,
                // notes) instead of coalescing the request as an unchanged update.
                updateAppWidgetState(context, glanceId) { preferences ->
                    preferences[MediaPlaybackRevisionKey] =
                        (preferences[MediaPlaybackRevisionKey] ?: 0L) + 1L
                }
                widget.update(context, glanceId)
            }
    }
}
