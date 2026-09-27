package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.preferences.GlancePreferences

/** Airplane mode while it is on; apps can't turn it off, so a tap opens its settings. */
internal class AirplaneModeGlance(
    val onClick: () -> Unit,
)

/** The Wi-Fi hotspot while it is on; apps can't turn it off, so a tap opens its settings. */
internal class HotspotGlance(
    val onClick: () -> Unit,
)

/** Follows airplane mode while [enabled] and the toggle is on. Reading it needs no permission. */
@Composable
internal fun rememberAirplaneModeGlance(enabled: Boolean): AirplaneModeGlance? {
    val context = LocalContext.current
    val preferences = remember(context) { GlancePreferences(context.applicationContext) }
    val refreshKey = rememberResumeRefreshKey()
    val show = remember(enabled, refreshKey) { enabled && preferences.isShowAirplaneModeEnabled() }
    var isOn by remember { mutableStateOf(false) }

    DisposableEffect(show, refreshKey) {
        val readState = { isOn = show && isAirplaneModeOn(context) }
        readState()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = readState()
            }
        val registered = show && registerSystemReceiver(context, receiver, Intent.ACTION_AIRPLANE_MODE_CHANGED)
        onDispose {
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    if (!isOn) return null
    return AirplaneModeGlance(
        onClick = {
            openFirstAvailable(
                context,
                Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
        },
    )
}

private fun isAirplaneModeOn(context: Context): Boolean =
    runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }.getOrDefault(false)

/**
 * Follows the Wi-Fi hotspot while [enabled] and the toggle is on. There is no public API for its
 * state, so this listens to the system's hotspot state broadcast and reads the current state from
 * the hidden [WifiManager] getter, as the broadcast is not sticky on every device (Samsung). Both
 * need ACCESS_WIFI_STATE, an install-time permission.
 */
@Composable
internal fun rememberHotspotGlance(enabled: Boolean): HotspotGlance? {
    val context = LocalContext.current
    val preferences = remember(context) { GlancePreferences(context.applicationContext) }
    val refreshKey = rememberResumeRefreshKey()
    val show = remember(enabled, refreshKey) { enabled && preferences.isShowHotspotEnabled() }
    var isOn by remember { mutableStateOf(false) }

    DisposableEffect(show, refreshKey) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    isOn = show && isHotspotOn(context, intent)
                }
            }
        var registered = false
        if (show) {
            val sticky =
                runCatching {
                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        IntentFilter(ACTION_WIFI_AP_STATE_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    ).also { registered = true }
                }.getOrNull()
            isOn = isHotspotOn(context, sticky)
        } else {
            isOn = false
        }
        onDispose {
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    if (!isOn) return null
    return HotspotGlance(
        onClick = {
            openFirstAvailable(
                context,
                Intent(ACTION_TETHER_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
            )
        },
    )
}

private fun isHotspotOn(
    context: Context,
    stateIntent: Intent?,
): Boolean {
    val state = stateIntent?.getIntExtra(EXTRA_WIFI_AP_STATE, -1) ?: -1
    if (state != -1) return state == WIFI_AP_STATE_ENABLED
    return runCatching {
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifiManager) as Boolean
    }.getOrDefault(false)
}

private fun registerSystemReceiver(
    context: Context,
    receiver: BroadcastReceiver,
    action: String,
): Boolean =
    runCatching {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
    }.isSuccess

/** The hotspot state broadcast, its state extra and enabled value; hidden [WifiManager] constants. */
private const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
private const val EXTRA_WIFI_AP_STATE = "wifi_state"
private const val WIFI_AP_STATE_ENABLED = 13

/** The system Hotspot & tethering page; not a public Settings constant. */
private const val ACTION_TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"

@Composable
internal fun AirplaneModeRow(glance: AirplaneModeGlance) {
    GlanceStatusRow(
        icon = {
            Icon(
                imageVector = Icons.Rounded.AirplanemodeActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = stringResource(R.string.home_airplane_mode_on),
        pillText = stringResource(R.string.home_turn_off),
        onClick = glance.onClick,
    )
}

@Composable
internal fun HotspotRow(glance: HotspotGlance) {
    GlanceStatusRow(
        icon = {
            Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = stringResource(R.string.home_hotspot_on),
        pillText = stringResource(R.string.home_turn_off),
        onClick = glance.onClick,
    )
}
