package com.tk.quicksearch.app

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.tk.quicksearch.search.data.UserAppPreferences

/**
 * Utility class to handle in-app review flow using Google Play's ReviewManager API.
 */
object ReviewHelper {
    private const val TAG = "ReviewHelper"

    /**
     * Requests an in-app review if the user is eligible based on days and app opens.
     * First prompt: at least 5 opens AND at least 2 days since first open.
     * Second prompt: at least 4 days since first prompt AND at least 5 more opens.
     */
    fun requestReviewIfEligible(
        activity: Activity,
        userPreferences: UserAppPreferences,
    ) {
        if (!userPreferences.shouldShowReviewPrompt()) {
            return
        }

        try {
            val reviewManager = ReviewManagerFactory.create(activity)
            val requestReviewFlow = reviewManager.requestReviewFlow()

            requestReviewFlow.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)

                    flow.addOnCompleteListener { _ ->
                        userPreferences.recordReviewPromptTime()
                        userPreferences.recordAppOpenCountAtPrompt()
                        userPreferences.incrementReviewPromptedCount()
                        Log.d(TAG, "Review flow completed. Prompted count: ${userPreferences.getReviewPromptedCount()}")
                    }
                } else {
                    Log.w(TAG, "Failed to request review flow", task.exception)
                    userPreferences.recordReviewPromptTime()
                    userPreferences.recordAppOpenCountAtPrompt()
                    userPreferences.incrementReviewPromptedCount()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting review", e)
            userPreferences.recordReviewPromptTime()
            userPreferences.recordAppOpenCountAtPrompt()
            userPreferences.incrementReviewPromptedCount()
        }
    }
}
