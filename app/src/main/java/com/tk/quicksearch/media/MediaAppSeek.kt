package com.tk.quicksearch.media

import android.content.Context
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.PlaybackState
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap

/** One of an app's own seek buttons, published as a session custom action. */
data class AppSeekAction(
    val action: String,
    val label: String,
    /** A drawable in the media app's package, not ours. */
    @DrawableRes val iconRes: Int,
)

/** At least one of the two is set; a missing direction falls back to the [MediaSeek] button. */
data class AppSeekActions(
    val back: AppSeekAction?,
    val forward: AppSeekAction?,
)

/**
 * Podcast and audiobook apps often publish their own seek buttons (skip back 10 seconds, forward
 * 30 seconds, ...) as custom actions instead of relying on [PlaybackState.ACTION_SEEK_TO]. The media
 * controls show those buttons in place of the 15 second [MediaSeek] ones, so each app keeps its
 * own step. Custom actions carry no type, so they are recognized by their id and label.
 */
object MediaAppSeek {
    /** Words that contain "back" without meaning a backward seek. */
    private val BACK_FALSE_FRIENDS = listOf("playback", "feedback", "callback", "background")
    private val SEEK_HINTS = listOf("seek", "skip", "jump", "sec")

    /** The session's custom seek buttons, or null when it offers none. */
    fun find(state: PlaybackState?): AppSeekActions? {
        val customActions = runCatching { state?.customActions }.getOrNull().orEmpty()
        if (customActions.isEmpty()) return null
        var back: AppSeekAction? = null
        var forward: AppSeekAction? = null
        for (customAction in customActions) {
            val seekAction =
                AppSeekAction(
                    action = customAction.action ?: continue,
                    label = customAction.name?.toString().orEmpty(),
                    iconRes = customAction.icon,
                )
            // Action ids and labels vary by app (jumpBack, SEEK_15_SECONDS_BACK, "Rewind", ...), so
            // match on both, lowercased with separators dropped.
            var text =
                (seekAction.action + seekAction.label)
                    .lowercase()
                    .filter { it.isLetterOrDigit() }
            BACK_FALSE_FRIENDS.forEach { text = text.replace(it, "") }
            val hasSeekHint = SEEK_HINTS.any { it in text }
            when {
                "rewind" in text || "replay" in text || ("back" in text && hasSeekHint) ->
                    if (back == null) back = seekAction
                "fastforward" in text || "ffwd" in text || ("forward" in text && hasSeekHint) ->
                    if (forward == null) forward = seekAction
            }
        }
        if (back == null && forward == null) return null
        return AppSeekActions(back, forward)
    }

    /**
     * Triggers the custom action with id [action] on [controller], passing along the extras the
     * session published with it. Returns false when the session no longer offers that action.
     */
    fun send(
        controller: MediaController,
        action: String,
    ): Boolean =
        runCatching {
            val customAction =
                controller.playbackState?.customActions?.firstOrNull { it.action == action } ?: return false
            controller.transportControls.sendCustomAction(action, customAction.extras)
            true
        }.getOrDefault(false)

    /** Loads [iconRes] from the media app's resources as a [sizePx] square bitmap, for tinting. */
    fun loadIcon(
        context: Context,
        packageName: String,
        @DrawableRes iconRes: Int,
        sizePx: Int,
    ): Bitmap? =
        runCatching {
            if (iconRes == 0) return null
            val resources = context.packageManager.getResourcesForApplication(packageName)
            ResourcesCompat.getDrawable(resources, iconRes, null)?.toBitmap(sizePx, sizePx)
        }.getOrNull()
}
