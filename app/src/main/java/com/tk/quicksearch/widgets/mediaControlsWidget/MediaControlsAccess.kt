package com.tk.quicksearch.widgets.mediaControlsWidget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tk.quicksearch.search.data.MediaPlaybackRepository

/**
 * Whether the Media Controls widget can read media sessions, re-checked on every resume so it
 * updates when the user returns from the notification access settings screen.
 */
@Composable
fun rememberMediaControlsAccess(): Boolean {
    val context = LocalContext.current
    val repository = remember(context) { MediaPlaybackRepository(context) }
    var hasAccess by remember { mutableStateOf(repository.mediaControlsHasAccess()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, repository) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) hasAccess = repository.mediaControlsHasAccess()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return hasAccess
}
