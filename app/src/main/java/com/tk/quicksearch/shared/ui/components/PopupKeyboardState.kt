package com.tk.quicksearch.shared.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Keeps the host window's keyboard as it was before a popup opened. Call from the host composition,
 * before the popup's own window is shown; pass [active] for popups that stay composed while hidden,
 * such as a DropdownMenu's `expanded`.
 *
 * The search surfaces are declared stateAlwaysVisible, so a field that keeps focus while its
 * keyboard is closed re-raises the keyboard when a popup window closes and focus returns. If the
 * keyboard was closed when the popup opened, the host's focus is dropped so it stays closed; if it
 * was open, focus is kept and it comes back as before. With a hardware keyboard the field keeps
 * focus so typing still works.
 */
@Composable
fun PreserveHostKeyboardStateEffect(active: Boolean = true) {
    val hostView = LocalView.current
    val focusManager = LocalFocusManager.current
    val hasHardwareKeyboard =
            LocalConfiguration.current.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
    val wasKeyboardVisible =
            remember(active) {
                active &&
                        ViewCompat.getRootWindowInsets(hostView)
                                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
    DisposableEffect(active) {
        if (active && !wasKeyboardVisible && !hasHardwareKeyboard) {
            focusManager.clearFocus(force = true)
        }
        onDispose {}
    }
}
