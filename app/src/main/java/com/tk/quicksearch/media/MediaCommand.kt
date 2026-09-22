package com.tk.quicksearch.media

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.MediaPlaybackRepository

/** Playback commands that custom buttons can send to whichever app currently owns media playback. */
enum class MediaCommand(
    val value: String,
    val keyCode: Int,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    /** Lowercase search terms the custom-button picker matches against, in addition to the label. */
    val searchKeywords: List<String>,
    /** Relative seek applied through the active session; null for plain media-key commands. */
    val seekDeltaMs: Long? = null,
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
        searchKeywords = listOf("previous", "prev", "back", "media", "music"),
    ),
    NEXT(
        value = "next",
        keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
        labelRes = R.string.media_command_next,
        iconRes = R.drawable.ic_widget_media_next,
        searchKeywords = listOf("next", "skip", "media", "music"),
    ),
    SEEK_BACK(
        value = "seek_back",
        keyCode = KeyEvent.KEYCODE_MEDIA_REWIND,
        labelRes = R.string.media_seek_back,
        iconRes = R.drawable.ic_widget_media_rewind,
        searchKeywords = listOf("rewind", "seek", "back", "15", "seconds", "media", "podcast"),
        seekDeltaMs = -MediaSeek.STEP_MS,
    ),
    SEEK_FORWARD(
        value = "seek_forward",
        keyCode = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        labelRes = R.string.media_seek_forward,
        iconRes = R.drawable.ic_widget_media_fast_forward,
        searchKeywords = listOf("forward", "fast forward", "seek", "15", "seconds", "media", "podcast"),
        seekDeltaMs = MediaSeek.STEP_MS,
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
     *
     * Seek commands need an exact 15 second jump, which only a MediaController can do. With
     * notification access they seek the priority session; without it, or when that session refuses
     * seeks, they fall back to the rewind/fast-forward media key and the player decides the step.
     */
    fun dispatch(
        context: Context,
        command: MediaCommand,
    ) {
        val seekDeltaMs = command.seekDeltaMs
        if (seekDeltaMs != null) {
            val controller = MediaPlaybackRepository(context).priorityController()
            if (controller != null && MediaSeek.supportsSeek(runCatching { controller.playbackState }.getOrNull())) {
                MediaSeek.seekBy(controller, seekDeltaMs)
                return
            }
        }
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val time = SystemClock.uptimeMillis()
        runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, command.keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, command.keyCode, 0))
        }
    }
}
