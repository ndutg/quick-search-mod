package com.tk.quicksearch.widgets.customButtonsWidget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.tk.quicksearch.media.MediaAppSeek
import com.tk.quicksearch.media.MediaCommand
import com.tk.quicksearch.media.MediaControls
import com.tk.quicksearch.search.data.MediaPlaybackRepository

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
        // The Media Controls widget's app seek buttons; if the session stopped offering that
        // action since the widget last drew, fall back to the plain command.
        val customAction = parameters[CUSTOM_ACTION_KEY]
        if (customAction != null) {
            val controller = MediaPlaybackRepository(context).priorityController()
            if (controller != null && MediaAppSeek.send(controller, customAction)) return
        }
        MediaControls.dispatch(context, command)
    }

    companion object {
        val MEDIA_COMMAND_KEY = ActionParameters.Key<String>("media_command")

        /** Optional session custom action (see [MediaAppSeek]) sent instead of [MEDIA_COMMAND_KEY]. */
        val CUSTOM_ACTION_KEY = ActionParameters.Key<String>("media_custom_action")
    }
}
