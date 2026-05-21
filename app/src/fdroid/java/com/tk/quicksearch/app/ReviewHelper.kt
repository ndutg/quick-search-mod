package com.tk.quicksearch.app

import android.app.Activity
import com.tk.quicksearch.search.data.UserAppPreferences

/** No-op for F-Droid builds (Google Play in-app review is unavailable). */
object ReviewHelper {
    fun requestReviewIfEligible(
        activity: Activity,
        userPreferences: UserAppPreferences,
    ) = Unit
}
