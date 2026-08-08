package com.tk.quicksearch.search.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Invalidates the persisted app catalog when the app process is not running.
 *
 * [AppsRepository] registers an in-process receiver to refresh the visible list immediately.
 * This receiver covers package changes that happen while Quick Search is not in memory. The next
 * launch can render its cached catalog immediately, then refresh it in the background.
 */
class AppCatalogChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppCache(context.applicationContext).markCatalogInvalidated()
    }
}
