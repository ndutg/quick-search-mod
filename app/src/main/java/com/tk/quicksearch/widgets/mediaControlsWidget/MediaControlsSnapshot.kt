package com.tk.quicksearch.widgets.mediaControlsWidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import com.tk.quicksearch.media.MediaSeek
import com.tk.quicksearch.search.data.MediaPlaybackRepository
import kotlin.math.max

/**
 * What the Media Controls widget shows: the system-priority session, i.e. the same one its media
 * key buttons reach. Unlike the At a Glance card this ignores the "show now playing" toggle, so
 * turning that card off does not blank a widget the user placed on purpose.
 */
internal data class MediaControlsSnapshot(
    /** Without notification access nothing can be read, but the buttons still send media keys. */
    val hasAccess: Boolean,
    /** Null when nothing is playing. */
    val title: String?,
    val albumArt: Bitmap?,
    val isPlaying: Boolean,
    val canSeek: Boolean,
    val isSeekMode: Boolean,
) {
    val hasSession: Boolean
        get() = title != null
}

internal fun MediaPlaybackRepository.mediaControlsHasAccess(): Boolean = hasAccess()

internal fun readMediaControlsSnapshot(context: Context): MediaControlsSnapshot {
    val repository = MediaPlaybackRepository(context)
    val hasAccess = repository.mediaControlsHasAccess()
    if (!hasAccess) {
        // Nothing can be read without access; the buttons still send media keys.
        return MediaControlsSnapshot(
            hasAccess = false,
            title = null,
            albumArt = null,
            isPlaying = false,
            canSeek = true,
            isSeekMode = false,
        )
    }
    val controller =
        repository.priorityController()
            ?: return MediaControlsSnapshot(
                hasAccess = hasAccess,
                title = null,
                albumArt = null,
                isPlaying = false,
                // With no session to ask, keep the full row: seeks fall back to rewind/fast-forward keys.
                canSeek = true,
                isSeekMode = false,
            )
    val metadata = runCatching { controller.metadata }.getOrNull()
    val playbackState = runCatching { controller.playbackState }.getOrNull()
    val title =
        metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: runCatching {
                val packageManager = context.packageManager
                packageManager.getApplicationInfo(controller.packageName, 0).loadLabel(packageManager).toString()
            }.getOrNull()
            ?: controller.packageName
    return MediaControlsSnapshot(
        hasAccess = hasAccess,
        title = title,
        albumArt =
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
        isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
        canSeek = MediaSeek.supportsSeek(playbackState),
        isSeekMode = MediaSeek.isSeekMode(metadata, playbackState),
    )
}

/**
 * Center-crops [source] into a [sizePx] square with rounded corners. Session artwork is often
 * 1000px or more, which would blow the widget host's bitmap budget, and Glance can only round
 * corners itself on Android 12+.
 */
internal fun roundedAlbumArt(
    source: Bitmap,
    sizePx: Int,
    cornerRadiusPx: Float,
): Bitmap? =
    runCatching {
        val software =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            }
        val scale = max(sizePx / software.width.toFloat(), sizePx / software.height.toFloat())
        val shader =
            BitmapShader(software, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            (sizePx - software.width * scale) / 2f,
                            (sizePx - software.height * scale) / 2f,
                        )
                    },
                )
            }
        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        Canvas(output).drawRoundRect(
            RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()),
            cornerRadiusPx,
            cornerRadiusPx,
            paint,
        )
        output
    }.getOrNull()
