package com.tk.quicksearch.search.data.preferences

import android.content.Context

/** Stores the home At a Glance toggle for the now-playing media card. */
class MediaPreferences(context: Context) : BasePreferences(context) {
    fun isShowNowPlayingEnabled(): Boolean = getBooleanPref(KEY_SHOW_NOW_PLAYING, true)

    fun setShowNowPlayingEnabled(enabled: Boolean) = setBooleanPref(KEY_SHOW_NOW_PLAYING, enabled)

    companion object {
        private const val KEY_SHOW_NOW_PLAYING = "home_show_now_playing_media"
    }
}
