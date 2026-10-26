package com.tk.quicksearch.shared.util

import android.app.Activity
import androidx.core.view.WindowCompat

/**
 * Switches light/dark system bar icons on an activity that already called enableEdgeToEdge in
 * onCreate. Don't call enableEdgeToEdge again for this: on API 35+ every call adds another
 * full-screen ProtectionLayout to the decor view, and repeated calls from composition pile them up
 * until touch, insets and drawing slow down. Bar colors stay transparent, so only icons change.
 */
fun Activity.setSystemBarsDark(dark: Boolean) {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (controller.isAppearanceLightStatusBars == dark) controller.isAppearanceLightStatusBars = !dark
    if (controller.isAppearanceLightNavigationBars == dark) controller.isAppearanceLightNavigationBars = !dark
}
