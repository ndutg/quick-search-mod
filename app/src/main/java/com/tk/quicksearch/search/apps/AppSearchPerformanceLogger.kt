package com.tk.quicksearch.search.apps

import android.util.Log
import com.tk.quicksearch.BuildConfig

/** DEBUG-only traces for identifying delays in app catalog, suggestions, and result delivery. */
internal object AppSearchPerformanceLogger {
    private const val TAG = "AppSearch"

    fun log(message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message())
        }
    }

    fun logTiming(
        event: String,
        elapsedMs: Long,
        slowThresholdMs: Long,
        details: () -> String = { "" },
    ) {
        if (!BuildConfig.DEBUG) return

        val message = "$event elapsedMs=$elapsedMs slow=${elapsedMs >= slowThresholdMs} ${details()}"
        if (elapsedMs >= slowThresholdMs) {
            Log.w(TAG, message)
        } else {
            Log.d(TAG, message)
        }
    }
}
