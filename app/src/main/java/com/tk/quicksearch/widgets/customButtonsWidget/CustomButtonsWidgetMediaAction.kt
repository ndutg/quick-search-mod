package com.tk.quicksearch.widgets.customButtonsWidget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.tk.quicksearch.media.MediaCommand
import com.tk.quicksearch.media.MediaControls

/**
 * Runs a media command directly from the widget process, without starting [WidgetActionActivity].
 * Prev/next are tapped repeatedly, so routing them through an activity launch would be both slow
 * and visually jarring; a plain key event is instant and keeps the launcher in the foreground.
 */
class CustomButtonsWidgetMediaAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val command = MediaCommand.fromValue(parameters[MEDIA_COMMAND_KEY]) ?: return
        MediaControls.dispatch(context, command)
    }

    companion object {
        val MEDIA_COMMAND_KEY = ActionParameters.Key<String>("media_command")
    }
}
