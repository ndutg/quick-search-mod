package com.tk.quicksearch.app.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tk.quicksearch.app.ReviewHelper
import com.tk.quicksearch.search.data.userAppPreferences.UserAppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val IN_APP_REVIEW_SETTLE_DELAY_MS = 600L

/**
 * Requests Google's in-app review when an established user comes back to Search from Settings or
 * the Widgets panel: a calm moment where they aren't about to type or launch something.
 * Eligibility (usage and a long cooldown) lives in [UserAppPreferences.shouldRequestInAppReview].
 */
@Composable
internal fun InAppReviewOnReturnEffect(
    destination: RootDestination,
    activity: Activity?,
    userPreferences: UserAppPreferences,
    canRequestNow: () -> Boolean,
) {
    var hasVisitedSearchInThisSession by rememberSaveable {
        mutableStateOf(destination == RootDestination.Search)
    }
    var isReturningToSearch by rememberSaveable { mutableStateOf(false) }
    val currentCanRequestNow by rememberUpdatedState(canRequestNow)

    LaunchedEffect(destination) {
        when (destination) {
            RootDestination.Search -> {
                hasVisitedSearchInThisSession = true
                if (!isReturningToSearch) return@LaunchedEffect
                isReturningToSearch = false

                val targetActivity = activity ?: return@LaunchedEffect
                // Let the return transition finish before the review sheet can appear.
                delay(IN_APP_REVIEW_SETTLE_DELAY_MS)
                if (!currentCanRequestNow()) return@LaunchedEffect
                val isEligible =
                    withContext(Dispatchers.IO) { userPreferences.shouldRequestInAppReview() }
                if (!isEligible) return@LaunchedEffect

                userPreferences.recordInAppReviewRequested()
                ReviewHelper.launchInAppReview(targetActivity)
            }
            RootDestination.Settings,
            RootDestination.WidgetsPanel -> {
                if (hasVisitedSearchInThisSession) {
                    isReturningToSearch = true
                }
            }
        }
    }
}
