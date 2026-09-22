package com.tk.quicksearch.media

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tk.quicksearch.R

/** Playback commands that custom buttons can send to whichever app currently owns media playback. */
enum class MediaCommand(
    val value: String,
    val keyCode: Int,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    /** Lowercase search terms the custom-button picker matches against, in addition to the label. */
    val searchKeywords: List<String>,
) {
    PLAY_PAUSE(
        value = "play_pause",
        keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        labelRes = R.string.media_command_play_pause,
        iconRes = R.drawable.ic_widget_media_play_pause,
        searchKeywords = listOf("play", "pause", "media", "music", "playback"),
    ),
    PREVIOUS(
        value = "previous",
        keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        labelRes = R.string.media_command_previous,
        iconRes = R.drawable.ic_widget_media_previous,
        searchKeywords = listOf("previous", "prev", "back", "rewind", "media", "music"),
    ),
    NEXT(
        value = "next",
        keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
        labelRes = R.string.media_command_next,
        iconRes = R.drawable.ic_widget_media_next,
        searchKeywords = listOf("next", "skip", "forward", "media", "music"),
    ),
    ;

    companion object {
        fun fromValue(value: String?): MediaCommand? = entries.firstOrNull { it.value == value }
    }
}

object MediaControls {
    /**
     * Sends [command] as a media key, which the system routes to the active media session. Unlike
     * the At a Glance card, which drives the exact session it shows through its MediaController,
     * this needs no notification access, so custom buttons work without that grant.
     */
    fun dispatch(
        context: Context,
        command: MediaCommand,
    ) {
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val time = SystemClock.uptimeMillis()
        runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, command.keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, command.keyCode, 0))
        }
    }
}
