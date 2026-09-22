package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tk.quicksearch.R
import com.tk.quicksearch.media.MediaSeek
import com.tk.quicksearch.search.data.MediaPlaybackRepository
import com.tk.quicksearch.shared.ui.theme.DesignTokens

private val NowPlayingAlbumArtSize = 56.dp
private val NowPlayingControlButtonSize = 40.dp
private val NowPlayingControlIconSize = 26.dp
private val NowPlayingPlayPauseCircleSize = 28.dp
private val NowPlayingPlayPauseIconSize = 18.dp
private val NowPlayingDismissButtonSize = 28.dp
private val NowPlayingSecondaryControlIconSize = 18.dp

/** The active now-playing media session shown in the home At a Glance card, with transport controls. */
internal class NowPlayingGlance(
    val title: String,
    val albumArt: android.graphics.Bitmap?,
    val isPlaying: Boolean,
    /** Whether the session accepts seeks; the rewind/forward buttons are hidden otherwise. */
    val canSeek: Boolean,
    /** Long media: rewind/forward are emphasized over previous/next and sit beside play/pause. */
    val isSeekMode: Boolean,
    val playPause: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val seekBack: () -> Unit,
    val seekForward: () -> Unit,
    val dismiss: () -> Unit,
    val open: () -> Unit,
)

/**
 * Tracks the system's active media session while [enabled], refreshing whenever the set of
 * sessions changes and mirroring the selected session's playback state and metadata live (rather
 * than polling, since play/pause must flip instantly).
 */
@Composable
internal fun rememberNowPlayingGlance(enabled: Boolean): NowPlayingGlance? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { MediaPlaybackRepository(context) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var metadata by remember { mutableStateOf<MediaMetadata?>(null) }
    var playbackState by remember { mutableStateOf<PlaybackState?>(null) }
    var dismissedSessionToken by remember { mutableStateOf<Any?>(null) }
    var dismissedWhilePlaying by remember { mutableStateOf(false) }
    var sawNonPlayingStateAfterDismiss by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(repository, enabled, refreshKey) {
        if (!enabled || !repository.hasAccess()) {
            controller = null
            return@DisposableEffect onDispose {}
        }
        fun refreshController() {
            controller = repository.activeController()
        }
        refreshController()
        val listener = repository.registerSessionsListener(::refreshController)
        onDispose { repository.unregisterSessionsListener(listener) }
    }

    DisposableEffect(controller?.sessionToken) {
        val current = controller
        if (current == null) {
            metadata = null
            playbackState = null
            return@DisposableEffect onDispose {}
        }
        metadata = runCatching { current.metadata }.getOrNull()
        playbackState = runCatching { current.playbackState }.getOrNull()
        val callback =
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    playbackState = state
                }

                override fun onMetadataChanged(newMetadata: MediaMetadata?) {
                    metadata = newMetadata
                }

                override fun onSessionDestroyed() {
                    controller = null
                }
            }
        runCatching { current.registerCallback(callback) }
        onDispose { runCatching { current.unregisterCallback(callback) } }
    }

    val activeController = controller ?: return null
    val activeMetadata = metadata ?: return null
    val title =
        activeMetadata
            .getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: return null
    val albumArt =
        activeMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: activeMetadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
    val isSeekMode = MediaSeek.isSeekMode(activeMetadata, playbackState)
    val dismissedForCurrentSession = dismissedSessionToken == activeController.sessionToken
    val shouldRemainDismissed =
        dismissedForCurrentSession &&
            !(isPlaying && (!dismissedWhilePlaying || sawNonPlayingStateAfterDismiss))

    androidx.compose.runtime.LaunchedEffect(
        dismissedSessionToken,
        activeController.sessionToken,
        isPlaying,
    ) {
        when {
            dismissedSessionToken == null -> Unit
            !dismissedForCurrentSession -> {
                dismissedSessionToken = null
                sawNonPlayingStateAfterDismiss = false
            }
            !isPlaying -> sawNonPlayingStateAfterDismiss = true
            !dismissedWhilePlaying || sawNonPlayingStateAfterDismiss -> {
                dismissedSessionToken = null
                sawNonPlayingStateAfterDismiss = false
            }
        }
    }

    if (shouldRemainDismissed) return null

    return NowPlayingGlance(
        title = title,
        albumArt = albumArt,
        isPlaying = isPlaying,
        canSeek = MediaSeek.supportsSeek(playbackState),
        isSeekMode = isSeekMode,
        playPause = {
            runCatching {
                if (isPlaying) activeController.transportControls.pause() else activeController.transportControls.play()
            }
        },
        previous = { runCatching { activeController.transportControls.skipToPrevious() } },
        next = { runCatching { activeController.transportControls.skipToNext() } },
        seekBack = { MediaSeek.seekBy(activeController, -MediaSeek.STEP_MS) },
        seekForward = { MediaSeek.seekBy(activeController, MediaSeek.STEP_MS) },
        dismiss = {
            dismissedSessionToken = activeController.sessionToken
            dismissedWhilePlaying = isPlaying
            sawNonPlayingStateAfterDismiss = false
        },
        open = {
            val openedSessionActivity =
                runCatching { activeController.sessionActivity?.send() }.isSuccess
            if (!openedSessionActivity) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(activeController.packageName)
                if (launchIntent != null) {
                    runCatching {
                        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        },
    )
}

@Composable
internal fun NowPlayingRow(glance: NowPlayingGlance) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = 7.dp,
                    top = DesignTokens.SpacingMedium,
                    bottom = DesignTokens.SpacingSmall,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(DesignTokens.SpacingSmall))
                    .clickable(onClick = glance.open),
        ) {
            NowPlayingAlbumArt(glance)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Scrolls when the title doesn't fit; stays still when it does.
                Text(
                    text = glance.title,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(onClick = glance.open)
                            .basicMarquee(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                IconButton(onClick = glance.dismiss, modifier = Modifier.size(NowPlayingDismissButtonSize)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // The emphasized pair (see NowPlayingGlance.isSeekMode) sits beside play/pause; the
            // other pair moves to the outer slots, each centered in the space left over there.
            val skipBack = NowPlayingControl(Icons.Rounded.SkipPrevious, R.string.media_command_previous, glance.previous)
            val skipForward = NowPlayingControl(Icons.Rounded.SkipNext, R.string.media_command_next, glance.next)
            val seekBack =
                NowPlayingControl(Icons.Rounded.FastRewind, R.string.media_seek_back, glance.seekBack)
                    .takeIf { glance.canSeek }
            val seekForward =
                NowPlayingControl(Icons.Rounded.FastForward, R.string.media_seek_forward, glance.seekForward)
                    .takeIf { glance.canSeek }
            val (outerBack, innerBack) = if (glance.isSeekMode) skipBack to seekBack else seekBack to skipBack
            val (outerForward, innerForward) =
                if (glance.isSeekMode) skipForward to seekForward else seekForward to skipForward
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    outerBack?.let { NowPlayingSkipButton(it, isProminent = false) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
                ) {
                    innerBack?.let { NowPlayingSkipButton(it, isProminent = true) }
                    IconButton(onClick = glance.playPause, modifier = Modifier.size(NowPlayingControlButtonSize)) {
                        Box(
                            modifier =
                                Modifier
                                    .size(NowPlayingPlayPauseCircleSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (glance.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.media_command_play_pause),
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(NowPlayingPlayPauseIconSize),
                            )
                        }
                    }
                    innerForward?.let { NowPlayingSkipButton(it, isProminent = true) }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    outerForward?.let { NowPlayingSkipButton(it, isProminent = false) }
                }
            }
        }
    }
}

private class NowPlayingControl(
    val icon: ImageVector,
    @StringRes val labelRes: Int,
    val onClick: () -> Unit,
)

/**
 * A skip or seek control. Whichever pair suits the media is [isProminent], drawn larger next to
 * play/pause: seeking for long media (see [MediaSeek.isSeekMode]), track skipping otherwise.
 */
@Composable
private fun NowPlayingSkipButton(
    control: NowPlayingControl,
    isProminent: Boolean,
) {
    IconButton(onClick = control.onClick, modifier = Modifier.size(NowPlayingControlButtonSize)) {
        Icon(
            imageVector = control.icon,
            contentDescription = stringResource(control.labelRes),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(if (isProminent) NowPlayingControlIconSize else NowPlayingSecondaryControlIconSize),
        )
    }
}

@Composable
private fun NowPlayingAlbumArt(glance: NowPlayingGlance) {
    val bitmap = glance.albumArt
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier =
                Modifier
                    .size(NowPlayingAlbumArtSize)
                    .clip(RoundedCornerShape(DesignTokens.SpacingSmall)),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(NowPlayingAlbumArtSize)
                    .clip(RoundedCornerShape(DesignTokens.SpacingSmall))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
