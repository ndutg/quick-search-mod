package com.tk.quicksearch.widgets.atAGlanceWidget

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.tk.quicksearch.R
import com.tk.quicksearch.customInfo.CustomInfoItem
import com.tk.quicksearch.customInfo.CustomInfoRepository
import com.tk.quicksearch.customInfo.CustomInfoScheduler
import com.tk.quicksearch.reminders.ReminderEditorRequests
import com.tk.quicksearch.search.apps.appLock.AppLockGate
import com.tk.quicksearch.search.core.intentHelpers.IntentHelpers
import com.tk.quicksearch.search.data.ContactBirthday
import com.tk.quicksearch.search.data.ContactBirthdaysRepository
import com.tk.quicksearch.search.data.GlanceNotificationsStore
import com.tk.quicksearch.search.data.ReminderRepository
import com.tk.quicksearch.search.data.UpcomingAlarmRepository
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.copySensitiveText
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.isDoNotDisturbOn
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.launchNotificationTarget
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.openAirplaneModeSettings
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.openBatterySettings
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.openBatteryUsage
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.openCallHistoryUnlocked
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.openDoNotDisturbSettings
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.openHotspotSettings
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.openStorageSettings
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.openWifiPanel

/**
 * Invisible trampoline for At a Glance widget rows. Starting another app's activity or a
 * notification's intent needs a foreground app, and locked apps need the unlock prompt, so rows
 * launch this first. It re-reads the row's item, which may have changed since the widget drew it.
 */
class AtAGlanceWidgetActionActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type =
            intent.getStringExtra(EXTRA_TYPE)?.let { name -> AtAGlanceWidgetTapType.entries.find { it.name == name } }
        val key = intent.getStringExtra(EXTRA_KEY)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        val extra = intent.getStringExtra(EXTRA_EXTRA)
        when (type) {
            AtAGlanceWidgetTapType.NOTIFICATION -> {
                packageName ?: return finish()
                // Ask before opening a locked app; finish either way after.
                AppLockGate.runAfterUnlock(this, packageName, onCancelled = ::finish) {
                    launchNotificationTarget(this, packageName, notificationIntent(key))
                    finish()
                }
                return
            }
            AtAGlanceWidgetTapType.OTP -> copyOtp(key)
            AtAGlanceWidgetTapType.MISSED_CALLS -> {
                val call = GlanceNotificationsStore.missedCalls.value.firstOrNull { it.key == key }
                val callPackage = call?.packageName ?: packageName ?: return finish()
                AppLockGate.runAfterUnlock(this, callPackage, onCancelled = ::finish) {
                    if (call != null) openCallHistoryUnlocked(this, call) else launchApp(callPackage)
                    finish()
                }
                return
            }
            AtAGlanceWidgetTapType.ALARM -> {
                val repository = UpcomingAlarmRepository(this)
                val now = System.currentTimeMillis()
                val alarm = repository.nextWithinFortyFiveMinutes(now) ?: repository.tomorrowsFirstAlarm(now)
                if (alarm != null) {
                    AppLockGate.runAfterUnlock(this, alarm.showIntent?.creatorPackage ?: this.packageName, onCancelled = ::finish) {
                        repository.open(alarm)
                        finish()
                    }
                    return
                }
            }
            AtAGlanceWidgetTapType.REMINDER -> {
                ReminderRepository(this).getReminderById(id)?.let { ReminderEditorRequests.openEdit(it) }
                launchApp(this.packageName)
            }
            AtAGlanceWidgetTapType.BIRTHDAY ->
                ContactBirthdaysRepository(this).open(
                    ContactBirthday(contactId = id, lookupKey = extra, name = "", isAnniversary = false),
                )
            AtAGlanceWidgetTapType.CALENDAR_EVENT ->
                IntentHelpers.openCalendarEvent(applicationContext as Application, id, packageName) { messageRes, _ ->
                    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
                }
            AtAGlanceWidgetTapType.CUSTOM_INFO -> {
                val item = CustomInfoRepository(this).all().firstOrNull { it.id.toLong() == id }
                if (item?.status == CustomInfoItem.ERROR) {
                    CustomInfoScheduler.retry(applicationContext, item.id)
                } else {
                    launchApp(this.packageName)
                }
            }
            AtAGlanceWidgetTapType.LOW_BATTERY -> openBatterySettings(this)
            AtAGlanceWidgetTapType.CHARGING -> openBatteryUsage(this)
            AtAGlanceWidgetTapType.LOW_STORAGE -> openStorageSettings(this)
            AtAGlanceWidgetTapType.DO_NOT_DISTURB -> turnOffDoNotDisturb()
            AtAGlanceWidgetTapType.AIRPLANE_MODE -> openAirplaneModeSettings(this)
            AtAGlanceWidgetTapType.HOTSPOT -> openHotspotSettings(this)
            AtAGlanceWidgetTapType.WIFI_SIGN_IN -> openWifiPanel(this)
            AtAGlanceWidgetTapType.FLASHLIGHT -> turnOffFlashlight()
            null -> Unit
        }
        finish()
    }

    /** The notification's own intent, looked up by key across every kind the widget shows. */
    private fun notificationIntent(key: String?) =
        key?.let {
            GlanceNotificationsStore.ongoingCalls.value.firstOrNull { it.key == key }?.contentIntent
                ?: GlanceNotificationsStore.timers.value.firstOrNull { it.key == key }?.contentIntent
                ?: GlanceNotificationsStore.appNotifications.value.firstOrNull { it.key == key }?.contentIntent
                ?: GlanceNotificationsStore.progress.value.firstOrNull { it.key == key }?.contentIntent
                ?: GlanceNotificationsStore.finishedProgress.value.firstOrNull { it.key == key }?.contentIntent
                ?: GlanceNotificationsStore.workouts.value.firstOrNull { it.key == key }?.contentIntent
                ?: GlanceNotificationsStore.weather.value.firstOrNull { it.key == key }?.contentIntent
        }

    /** Copies the code; Android 13+ confirms a copy itself. */
    private fun copyOtp(key: String?) {
        val otp = GlanceNotificationsStore.otps.value.firstOrNull { it.key == key } ?: return
        val copied = copySensitiveText(this, getString(R.string.calculator_copy_result), otp.code)
        if (copied && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.home_copied, Toast.LENGTH_SHORT).show()
        }
    }

    /** Turns Do Not Disturb off where the app may, and otherwise opens its settings. */
    private fun turnOffDoNotDisturb() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val canTurnOff =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                runCatching { notificationManager.isNotificationPolicyAccessGranted }.getOrDefault(false)
        if (canTurnOff) {
            runCatching { notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
        }
        if (isDoNotDisturbOn(notificationManager.currentInterruptionFilter)) openDoNotDisturbSettings(this)
    }

    /** Turns off the lit torches, or every flash when the updater has not seen which are lit. */
    private fun turnOffFlashlight() {
        val cameraManager = runCatching { getSystemService(CameraManager::class.java) }.getOrNull() ?: return
        val cameraIds =
            AtAGlanceWidgetUpdater.litCameraIds.value.ifEmpty {
                runCatching {
                    cameraManager.cameraIdList.filter { id ->
                        cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                }.getOrDefault(emptyList()).toSet()
            }
        cameraIds.forEach { cameraId -> runCatching { cameraManager.setTorchMode(cameraId, false) } }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        runCatching { startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    internal companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_ID = "id"
        private const val EXTRA_EXTRA = "extra"

        fun createIntent(
            context: Context,
            row: AtAGlanceWidgetRow,
        ): Intent {
            val tap = row.tap
            return Intent(context, AtAGlanceWidgetActionActivity::class.java)
                // A distinct URI per row keeps each row's PendingIntent from replacing another's.
                .setData(Uri.parse("quicksearch-glance-widget://row/${Uri.encode(row.key)}"))
                .putExtra(EXTRA_TYPE, tap.type.name)
                .putExtra(EXTRA_KEY, tap.key)
                .putExtra(EXTRA_PACKAGE, tap.packageName)
                .putExtra(EXTRA_ID, tap.id)
                .putExtra(EXTRA_EXTRA, tap.extra)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
    }
}
