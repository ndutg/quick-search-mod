package com.tk.quicksearch.media

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock

/**
 * Relative seeking for long media (podcasts, audiobooks, videos), where skipping 15 seconds is
 * more useful than skipping the whole track. The At a Glance card shows rewind/forward next to
 * previous/next and emphasizes them while [isSeekMode] holds. Custom buttons offer seeking as
 * separate commands, so their previous/next always skip tracks.
 */
object MediaSeek {
    const val STEP_MS = 15_000L
    private const val LONG_MEDIA_THRESHOLD_MS = 5 * 60_000L

    /** How long a commanded target stays the base for the next tap, before the session reports it. */
    private const val PENDING_TARGET_FRESH_MS = 1_500L

    private var pendingToken: MediaSession.Token? = null
    private var pendingTargetMs = 0L
    private var pendingAtElapsedMs = 0L

    /**
     * Whether the media is longer than five minutes and its session accepts seeks. Live streams and
     * some apps report an unknown (zero or negative) duration, which keeps plain previous/next.
     */
    fun isSeekMode(
        metadata: MediaMetadata?,
        state: PlaybackState?,
    ): Boolean {
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: return false
        return duration > LONG_MEDIA_THRESHOLD_MS && supportsSeek(state)
    }

    fun isSeekMode(controller: MediaController): Boolean =
        runCatching { isSeekMode(controller.metadata, controller.playbackState) }.getOrDefault(false)

    /** Seeks [controller] by [deltaMs] from its current position, clamped to the media's bounds. */
    @Synchronized
    fun seekBy(
        controller: MediaController,
        deltaMs: Long,
    ) {
        runCatching {
            val state = controller.playbackState ?: return
            if (!supportsSeek(state)) return
            val isPlaying = state.state == PlaybackState.STATE_PLAYING
            val now = SystemClock.elapsedRealtime()
            // Rapid taps must stack: the session usually has not reported the previous seek yet,
            // so its position would still read as if that tap never happened.
            val base =
                if (pendingToken == controller.sessionToken && now - pendingAtElapsedMs < PENDING_TARGET_FRESH_MS) {
                    pendingTargetMs + if (isPlaying) now - pendingAtElapsedMs else 0L
                } else {
                    state.currentPositionMs(now)
                }
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val upper = if (duration > 0L) duration else Long.MAX_VALUE
            val target = (base + deltaMs).coerceIn(0L, upper)
            controller.transportControls.seekTo(target)
            pendingToken = controller.sessionToken
            pendingTargetMs = target
            pendingAtElapsedMs = now
        }
    }

    fun supportsSeek(state: PlaybackState?): Boolean =
        state != null && state.actions and PlaybackState.ACTION_SEEK_TO != 0L

    /** [PlaybackState.getPosition] is a snapshot; extrapolate it to [nowElapsedMs] while playing. */
    private fun PlaybackState.currentPositionMs(nowElapsedMs: Long): Long {
        if (state != PlaybackState.STATE_PLAYING || lastPositionUpdateTime <= 0L) return position
        return position + ((nowElapsedMs - lastPositionUpdateTime) * playbackSpeed).toLong()
    }
}
