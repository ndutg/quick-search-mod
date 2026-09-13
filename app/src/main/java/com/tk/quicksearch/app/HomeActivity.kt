package com.tk.quicksearch.app

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import com.tk.quicksearch.shared.util.isDefaultHomeApp

/**
 * Default-launcher entry point for the home gesture.
 *
 * This is a thin subclass of [MainActivity] that lets the manifest bind a
 * Home-specific theme to the `CATEGORY_HOME` intent filter. That theme and
 * [WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER] let Android composite the
 * system wallpaper behind transparent app content.
 *
 * [MainActivity] itself keeps its translucent theme for non-launcher launches
 * (share intents, web search, ASSIST, app-icon taps from another launcher,
 * etc.) where seeing through to the previous app is desirable.
 *
 * The app only exposes the transparent backdrop when this activity is the
 * active default Home app and System wallpaper is the selected source.
 */
class HomeActivity : MainActivity() {
    var canShowSystemWallpaperBackdrop: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        canShowSystemWallpaperBackdrop = isDefaultHomeApp()
        if (canShowSystemWallpaperBackdrop) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setFormat(PixelFormat.TRANSPARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.decorView.setBackgroundColor(Color.TRANSPARENT)
        }
        super.onCreate(savedInstanceState)
    }

    /** Back at the launcher root must not reveal the previously used app. */
    override fun handleSearchBackPressed() = Unit
}
