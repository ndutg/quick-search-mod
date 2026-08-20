package com.tk.quicksearch.pinnedNotifications

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.tk.quicksearch.widgets.customButtonsWidget.CustomWidgetButtonAction

class PinnedNotificationPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            finish()
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), RequestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RequestCode) {
            CustomWidgetButtonAction.fromJson(intent.getStringExtra(ExtraAction))?.let {
                PinnedNotifications.completePermissionRequest(
                    this,
                    it,
                    intent.getStringExtra(ExtraMode)
                        ?.let(PinnedNotifications.PinMode::valueOf)
                        ?: PinnedNotifications.PinMode.WITH_OTHER_ITEMS,
                )
            }
            finish()
        }
    }

    companion object {
        private const val ExtraAction = "pinned_notification_action"
        private const val ExtraMode = "pinned_notification_mode"
        private const val RequestCode = 7330

        fun createIntent(
            context: Context,
            action: CustomWidgetButtonAction,
            mode: PinnedNotifications.PinMode,
        ): Intent =
            Intent(context, PinnedNotificationPermissionActivity::class.java)
                .putExtra(ExtraAction, action.toJson())
                .putExtra(ExtraMode, mode.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
