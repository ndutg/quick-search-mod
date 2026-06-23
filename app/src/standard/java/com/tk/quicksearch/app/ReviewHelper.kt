package com.tk.quicksearch.app

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

object ReviewHelper {
    fun launchInAppReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { requestTask ->
            if (!requestTask.isSuccessful) {
                return@addOnCompleteListener
            }

            manager.launchReviewFlow(activity, requestTask.result)
        }
    }
}
