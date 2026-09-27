package com.tk.quicksearch.widgetsPanel

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Invisible activity that opens a hosted widget's configure screen through
 * [AppWidgetHost.startAppWidgetConfigureActivityForResult] and relays its result.
 *
 * Starting a provider's configure activity directly only works when it is exported; many
 * (Smartspacer, for one) are not and expect the launcher to go through the system, which starts
 * them on the host's behalf.
 */
class WidgetConfigureTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val appWidgetId =
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finishWith(RESULT_CANCELED)
            return
        }
        val launched =
            runCatching {
                AppWidgetHost(applicationContext, QUICK_SEARCH_WIDGET_HOST_ID)
                    .startAppWidgetConfigureActivityForResult(
                        this,
                        appWidgetId,
                        0,
                        REQUEST_CONFIGURE,
                        null,
                    )
            }.onFailure { error ->
                if (error !is ActivityNotFoundException && error !is SecurityException) throw error
            }.isSuccess
        if (!launched) finishWith(RESULT_LAUNCH_FAILED)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) finishWith(resultCode)
    }

    private fun finishWith(resultCode: Int) {
        setResult(resultCode)
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val REQUEST_CONFIGURE = 1

        /** Result code when the provider's configure activity could not be started at all. */
        const val RESULT_LAUNCH_FAILED = RESULT_FIRST_USER + 41

        fun intent(context: Context, appWidgetId: Int): Intent =
            Intent(context, WidgetConfigureTrampolineActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }
}
