package com.tk.quicksearch.shared.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.tk.quicksearch.search.core.BackgroundSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Utility for determining whether the current background image is light or dark.
 * Used by the UI layer to pick readable text/icon colors over wallpaper backgrounds.
 */
object WallpaperContrastUtils {

    @Volatile
    private var cachedWallpaperIsLight: Boolean? = null
    @Volatile
    private var cachedCustomImageIsLightUri: String? = null
    @Volatile
    private var cachedCustomImageIsLight: Boolean? = null

    private val mutex = Mutex()

    /**
     * Returns true if the current background is light (use dark text),
     * false if dark (use light text), or null if not applicable.
     */
    suspend fun isBackgroundLight(
        context: Context,
        backgroundSource: BackgroundSource,
        customImageUri: String?,
    ): Boolean? {
        return when (backgroundSource) {
            BackgroundSource.SYSTEM_WALLPAPER -> getWallpaperIsLight(context)
            BackgroundSource.CUSTOM_IMAGE -> {
                val normalized = customImageUri?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                getCustomImageIsLight(context, normalized)
            }
            BackgroundSource.THEME -> null
        }
    }

    /**
     * Call this when wallpaper changes to force re-evaluation.
     */
    fun invalidateWallpaperLightnessCache() {
        cachedWallpaperIsLight = null
    }

    /**
     * Call this when custom image changes to force re-evaluation.
     */
    fun invalidateCustomImageLightnessCache() {
        cachedCustomImageIsLightUri = null
        cachedCustomImageIsLight = null
    }

    /**
     * Clears all lightness caches.
     */
    fun clearAll() {
        invalidateWallpaperLightnessCache()
        invalidateCustomImageLightnessCache()
    }

    private suspend fun getWallpaperIsLight(context: Context): Boolean? {
        cachedWallpaperIsLight?.let { return it }

        val bitmap = WallpaperUtils.getWallpaperBitmap(context) ?: return null
        return mutex.withLock {
            // Double-check after acquiring lock
            cachedWallpaperIsLight?.let { return it }
            withContext(Dispatchers.Default) {
                computeIsLight(bitmap)
            }.also { result ->
                cachedWallpaperIsLight = result
            }
        }
    }

    private suspend fun getCustomImageIsLight(
        context: Context,
        normalizedUri: String,
    ): Boolean? {
        if (cachedCustomImageIsLightUri == normalizedUri && cachedCustomImageIsLight != null) {
            return cachedCustomImageIsLight
        }

        // Use the wallpaper utils to get the custom image bitmap via appearance path
        val bitmap = WallpaperUtils.getCachedWallpaperBitmap()
        // Fallback: try loading wallpaper bitmap if custom not directly accessible
        val targetBitmap = bitmap ?: WallpaperUtils.getWallpaperBitmap(context) ?: return null

        return mutex.withLock {
            if (cachedCustomImageIsLightUri == normalizedUri && cachedCustomImageIsLight != null) {
                return cachedCustomImageIsLight
            }
            withContext(Dispatchers.Default) {
                computeIsLight(targetBitmap)
            }.also { result ->
                cachedCustomImageIsLightUri = normalizedUri
                cachedCustomImageIsLight = result
            }
        }
    }

    /**
     * Downscales the bitmap to 50x50 and computes average perceived luminance.
     * Returns true if the image is considered light (avg luminance > 128).
     */
    private fun computeIsLight(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return false

        val sw = bitmap.width.coerceAtMost(50).coerceAtLeast(1)
        val sh = bitmap.height.coerceAtMost(50).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)

        var totalLuminance = 0.0
        val w = scaled.width
        val h = scaled.height
        val pixelCount = w * h

        for (x in 0 until w) {
            for (y in 0 until h) {
                val pixel = scaled.getPixel(x, y)
                totalLuminance += 0.299 * Color.red(pixel) +
                                  0.587 * Color.green(pixel) +
                                  0.114 * Color.blue(pixel)
            }
        }

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        return (totalLuminance / pixelCount) > 128.0
    }
}
