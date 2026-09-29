package com.tk.quicksearch.search.searchScreen.searchRoute

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.tk.quicksearch.app.HomeActivity
import com.tk.quicksearch.search.core.BackgroundSource
import com.tk.quicksearch.search.core.SearchUiState
import com.tk.quicksearch.shared.util.WallpaperUtils

internal data class SearchScreenWallpaperState(
    val imageBitmap: ImageBitmap?,
    val usesWallpaperBackground: Boolean,
    val usesSystemWallpaperBackdrop: Boolean,
    val usesMonoThemeFallback: Boolean,
)

private data class CustomBitmapState(
    val imageBitmap: ImageBitmap?,
    val isLoadFinished: Boolean,
)

private data class WallpaperBitmapState(
    val imageBitmap: ImageBitmap?,
    val loadResult: WallpaperUtils.WallpaperLoadResult?,
)

@Composable
internal fun SearchScreenWallpaperLogic(
    state: SearchUiState,
    onWallpaperLoaded: (() -> Unit)? = null,
    onWallpaperUnavailable: (() -> Unit)? = null,
    onSystemWallpaperChanged: (() -> Unit)? = null,
    isOverlayPresentation: Boolean = false,
): SearchScreenWallpaperState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val canShowSystemWallpaperBackdrop =
        !isOverlayPresentation &&
            (context as? HomeActivity)?.canShowSystemWallpaperBackdrop == true
    var wallpaperChangeVersion by remember { mutableIntStateOf(0) }

    DisposableEffect(context, state.backgroundSource) {
        if (state.backgroundSource != BackgroundSource.SYSTEM_WALLPAPER) {
            onDispose { }
        } else {
            val appContext = context.applicationContext
            @Suppress("DEPRECATION")
            val wallpaperChangedAction = Intent.ACTION_WALLPAPER_CHANGED
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        if (intent?.action != wallpaperChangedAction) return
                        WallpaperUtils.invalidateWallpaperCache()
                        wallpaperChangeVersion++
                        onSystemWallpaperChanged?.invoke()
                    }
                }
            val filter = IntentFilter(wallpaperChangedAction)
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            onDispose {
                appContext.unregisterReceiver(receiver)
            }
        }
    }

    DisposableEffect(lifecycleOwner, state.backgroundSource) {
        if (state.backgroundSource != BackgroundSource.SYSTEM_WALLPAPER) {
            onDispose { }
        } else {
            // The first ON_RESUME is the launch itself (replayed when the observer is added). Counting
            // it as a wallpaper change would drop the startup preview on the first frame and fall back
            // to the theme background until the full wallpaper decodes, so only later resumes count.
            var isInitialResume = true
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        if (isInitialResume) {
                            isInitialResume = false
                        } else {
                            wallpaperChangeVersion++
                        }
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    val shouldUseStartupPreview = wallpaperChangeVersion == 0
    val sourceWallpaperState =
        produceState<WallpaperBitmapState>(
            initialValue =
                if (state.backgroundSource == BackgroundSource.SYSTEM_WALLPAPER) {
                    if (WallpaperUtils.hasWallpaperAccessPermission(context)) {
                        WallpaperUtils.getCachedWallpaperBitmap()?.let {
                            WallpaperBitmapState(
                                imageBitmap = it.asImageBitmap(),
                                loadResult = WallpaperUtils.WallpaperLoadResult.Success(it),
                            )
                        } ?: WallpaperBitmapState(imageBitmap = null, loadResult = null)
                    } else {
                        WallpaperBitmapState(
                            imageBitmap = null,
                            loadResult = WallpaperUtils.WallpaperLoadResult.PermissionRequired,
                        )
                    }
                } else {
                    WallpaperBitmapState(imageBitmap = null, loadResult = null)
                },
            state.backgroundSource,
            state.hasWallpaperPermission,
            state.wallpaperAvailable,
            state.startupBackgroundPreviewPath,
            wallpaperChangeVersion,
        ) {
            if (state.backgroundSource != BackgroundSource.SYSTEM_WALLPAPER) {
                value = WallpaperBitmapState(imageBitmap = null, loadResult = null)
                return@produceState
            }

            if (WallpaperUtils.hasWallpaperAccessPermission(context)) {
                // Render from memory immediately. File decode remains off the composition thread.
                val cachedWallpaper = WallpaperUtils.getCachedWallpaperBitmap()?.asImageBitmap()
                if (cachedWallpaper != null) {
                    value = WallpaperBitmapState(imageBitmap = cachedWallpaper, loadResult = null)
                } else if (shouldUseStartupPreview) {
                    WallpaperUtils.loadStartupBackgroundPreviewBitmap(
                        previewPath = state.startupBackgroundPreviewPath,
                    )?.asImageBitmap()?.let {
                        value = WallpaperBitmapState(imageBitmap = it, loadResult = null)
                    }
                }
            }

            when (val result = WallpaperUtils.getWallpaperBitmapResult(context)) {
                is WallpaperUtils.WallpaperLoadResult.Success -> {
                    value =
                        WallpaperBitmapState(
                            imageBitmap = result.bitmap.asImageBitmap(),
                            loadResult = result,
                        )
                    if (!isOverlayPresentation) {
                        onWallpaperLoaded?.invoke()
                    }
                }

                else -> {
                    value = WallpaperBitmapState(imageBitmap = null, loadResult = result)
                    if (!isOverlayPresentation) {
                        onWallpaperUnavailable?.invoke()
                    }
                }
            }
        }
    val sourceCustomBitmap =
        produceState(
            initialValue = CustomBitmapState(imageBitmap = null, isLoadFinished = false),
            key1 = state.backgroundSource,
            key2 = state.customImageUri,
            key3 = state.startupBackgroundPreviewPath,
        ) {
            if (state.backgroundSource != BackgroundSource.CUSTOM_IMAGE) {
                value = CustomBitmapState(imageBitmap = null, isLoadFinished = true)
                return@produceState
            }

            WallpaperUtils.loadStartupBackgroundPreviewBitmap(
                previewPath = state.startupBackgroundPreviewPath,
            )?.asImageBitmap()?.let { value = value.copy(imageBitmap = it) }

            val customBitmap = WallpaperUtils.getOverlayCustomImageBitmap(context, state.customImageUri)
            value =
                CustomBitmapState(
                    imageBitmap = customBitmap ?: value.imageBitmap,
                    isLoadFinished = true,
                )
            if (customBitmap != null && !isOverlayPresentation) {
                onWallpaperLoaded?.invoke()
            }
        }
    val imageBitmap =
        when (state.backgroundSource) {
            BackgroundSource.SYSTEM_WALLPAPER -> sourceWallpaperState.value.imageBitmap
            BackgroundSource.CUSTOM_IMAGE -> sourceCustomBitmap.value.imageBitmap
            BackgroundSource.THEME -> null
        }
    // The cached startup preview decodes off the main thread and lands a few hundred ms after the
    // first frame. Styling the screen for the theme fallback until then and switching when it
    // arrives rebuilds the keyed result content (restarting the app grid's fade), so while the
    // image background is still loading the screen is styled for it from the first frame.
    val isAwaitingStartupImage =
        shouldUseStartupPreview &&
            imageBitmap == null &&
            !state.startupBackgroundPreviewPath.isNullOrBlank() &&
            when (state.backgroundSource) {
                BackgroundSource.SYSTEM_WALLPAPER -> sourceWallpaperState.value.loadResult == null
                BackgroundSource.CUSTOM_IMAGE -> !sourceCustomBitmap.value.isLoadFinished
                BackgroundSource.THEME -> false
            }
    val usesSystemWallpaperBackdrop =
        state.backgroundSource == BackgroundSource.SYSTEM_WALLPAPER &&
            canShowSystemWallpaperBackdrop &&
            (sourceWallpaperState.value.loadResult ==
                WallpaperUtils.WallpaperLoadResult.PermissionRequired ||
                sourceWallpaperState.value.loadResult == WallpaperUtils.WallpaperLoadResult.SecurityError)
    val useBitmapBackground =
        WallpaperUtils.shouldUseImageBackground(
            backgroundSource = state.backgroundSource,
            hasImageBitmap = imageBitmap != null,
            wallpaperAvailable = state.wallpaperAvailable,
            requireWallpaperAvailableForSystemSource =
                !(shouldUseStartupPreview && sourceWallpaperState.value.imageBitmap != null),
        )
    val usesWallpaperBackground =
        usesSystemWallpaperBackdrop || useBitmapBackground || isAwaitingStartupImage
    val useMonoThemeFallback =
        !isOverlayPresentation &&
            state.backgroundSource != BackgroundSource.THEME &&
            !usesWallpaperBackground

    return SearchScreenWallpaperState(
        imageBitmap = imageBitmap,
        usesWallpaperBackground = usesWallpaperBackground,
        usesSystemWallpaperBackdrop = usesSystemWallpaperBackdrop,
        usesMonoThemeFallback = useMonoThemeFallback,
    )
}
