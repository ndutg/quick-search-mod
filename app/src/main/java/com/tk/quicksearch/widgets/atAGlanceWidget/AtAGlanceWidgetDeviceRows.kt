package com.tk.quicksearch.widgets.atAGlanceWidget

import android.app.NotificationManager
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.PowerManager
import android.text.format.Formatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WifiPassword
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.preferences.BatteryPreferences
import com.tk.quicksearch.search.data.preferences.BatteryPreferences.Companion.CRITICAL_BATTERY_THRESHOLD_PERCENT
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.ACTION_WIFI_AP_STATE_CHANGED
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.BatteryStatus
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.WifiSignInDismissal
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.formatShortDuration
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.isAirplaneModeOn
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.isDoNotDisturbOn
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.isHotspotOn
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.isLowStorage
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.needsCaptivePortalSignIn
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.readInternalStorage
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.updateLowStorageDismissal
import java.text.NumberFormat

/** The sticky battery broadcast, read without registering a receiver. */
internal fun AtAGlanceWidgetRowLoader.batteryStatus(): BatteryStatus? =
    runCatching {
        BatteryStatus.from(
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
            context.getSystemService(BatteryManager::class.java),
        )
    }.getOrNull()

private fun formatPercent(percent: Int): String = NumberFormat.getPercentInstance().format(percent / 100.0)

private fun AtAGlanceWidgetRowLoader.deviceRow(
    type: AtAGlanceWidgetTapType,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    dismiss: AtAGlanceWidgetDismissType? = null,
) = AtAGlanceWidgetRow(
    key = type.name,
    icon = AtAGlanceWidgetIcon.Vector(icon),
    title = title,
    subtitle = subtitle,
    tap = AtAGlanceWidgetTap(type),
    dismiss = dismiss,
)

internal fun AtAGlanceWidgetRowLoader.lowBatteryRows(): List<AtAGlanceWidgetRow> {
    if (!BatteryPreferences(context).isShowLowBatteryEnabled()) return emptyList()
    val battery = batteryStatus()?.takeIf { it.isLow } ?: return emptyList()
    val isCritical = battery.percent <= CRITICAL_BATTERY_THRESHOLD_PERCENT
    val isPowerSaveOn =
        runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode }.getOrNull() == true
    val detail =
        string(
            when {
                isCritical -> R.string.home_critical_battery_charge_now
                isPowerSaveOn -> R.string.home_low_battery_saver_on
                else -> R.string.home_low_battery_saver_off
            },
        )
    return listOf(
        deviceRow(
            type = AtAGlanceWidgetTapType.LOW_BATTERY,
            icon = Icons.Rounded.BatteryAlert,
            title =
                string(
                    if (isCritical) R.string.home_critical_battery_title else R.string.settings_at_a_glance_low_battery_title,
                ),
            subtitle = "${formatPercent(battery.percent)} • $detail",
        ),
    )
}

internal fun AtAGlanceWidgetRowLoader.wifiSignInRows(): List<AtAGlanceWidgetRow> {
    if (!glancePreferences.isShowWifiSignInEnabled()) return emptyList()
    val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
    val needsSignIn =
        runCatching {
            needsCaptivePortalSignIn(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork))
        }.getOrDefault(false)
    if (!needsSignIn || WifiSignInDismissal.dismissed) return emptyList()
    return listOf(
        deviceRow(
            AtAGlanceWidgetTapType.WIFI_SIGN_IN,
            Icons.Rounded.WifiPassword,
            string(R.string.home_wifi_sign_in),
            dismiss = AtAGlanceWidgetDismissType.WIFI_SIGN_IN,
        ),
    )
}

/** Device states that are on, then charging and low storage, in the home card's order. */
internal fun AtAGlanceWidgetRowLoader.deviceStateRows(): List<AtAGlanceWidgetRow> =
    buildList {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (
            glancePreferences.isShowDoNotDisturbEnabled() &&
            notificationManager != null &&
            isDoNotDisturbOn(notificationManager.currentInterruptionFilter)
        ) {
            add(deviceRow(AtAGlanceWidgetTapType.DO_NOT_DISTURB, Icons.Rounded.DoNotDisturbOn, string(R.string.home_dnd_on)))
        }
        if (glancePreferences.isShowAirplaneModeEnabled() && isAirplaneModeOn(context)) {
            add(
                deviceRow(
                    AtAGlanceWidgetTapType.AIRPLANE_MODE,
                    Icons.Rounded.AirplanemodeActive,
                    string(R.string.home_airplane_mode_on),
                ),
            )
        }
        if (glancePreferences.isShowHotspotEnabled()) {
            val stateIntent = runCatching { context.registerReceiver(null, IntentFilter(ACTION_WIFI_AP_STATE_CHANGED)) }.getOrNull()
            if (isHotspotOn(context, stateIntent)) {
                add(deviceRow(AtAGlanceWidgetTapType.HOTSPOT, Icons.Rounded.WifiTethering, string(R.string.home_hotspot_on)))
            }
        }
        if (glancePreferences.isShowFlashlightEnabled() && AtAGlanceWidgetUpdater.litCameraIds.value.isNotEmpty()) {
            add(deviceRow(AtAGlanceWidgetTapType.FLASHLIGHT, Icons.Rounded.FlashlightOn, string(R.string.home_flashlight_on)))
        }
        addAll(chargingRows())
        addAll(lowStorageRows())
    }

private fun AtAGlanceWidgetRowLoader.chargingRows(): List<AtAGlanceWidgetRow> {
    val preferences = BatteryPreferences(context)
    if (!preferences.isShowChargingEnabled()) return emptyList()
    val battery = batteryStatus()?.takeIf { it.isPlugged } ?: return emptyList()
    // Charging dismissed on home stays hidden until the phone is unplugged, as on home.
    val dismissedPercent = preferences.getChargingDismissedPercent()
    if (dismissedPercent != null && battery.percent >= dismissedPercent) return emptyList()
    val title =
        string(
            when {
                battery.isFull -> R.string.home_charged
                battery.isPaused -> R.string.home_charging_paused
                else -> R.string.settings_at_a_glance_charging_title
            },
        )
    val timeToFull =
        battery.chargeTimeRemainingMillis
            ?.takeIf { !battery.isFull && !battery.isPaused }
            ?.let { string(R.string.home_charging_full_in, formatShortDuration(it)) }
    return listOf(
        deviceRow(
            type = AtAGlanceWidgetTapType.CHARGING,
            icon = if (battery.isFull) Icons.Rounded.BatteryFull else Icons.Rounded.BatteryChargingFull,
            title = title,
            subtitle = listOfNotNull(formatPercent(battery.percent), timeToFull).joinToString(" • "),
            dismiss = AtAGlanceWidgetDismissType.CHARGING,
        ),
    )
}

private fun AtAGlanceWidgetRowLoader.lowStorageRows(): List<AtAGlanceWidgetRow> {
    if (!glancePreferences.isShowLowStorageEnabled()) return emptyList()
    val (free, total) = readInternalStorage() ?: return emptyList()
    if (!isLowStorage(free, total) || updateLowStorageDismissal(glancePreferences, free, total)) return emptyList()
    return listOf(
        deviceRow(
            type = AtAGlanceWidgetTapType.LOW_STORAGE,
            icon = Icons.Rounded.Storage,
            title = string(R.string.home_storage_almost_full),
            subtitle =
                string(
                    R.string.home_storage_free,
                    Formatter.formatShortFileSize(context, free),
                    Formatter.formatShortFileSize(context, total),
                ),
            dismiss = AtAGlanceWidgetDismissType.LOW_STORAGE,
        ),
    )
}
