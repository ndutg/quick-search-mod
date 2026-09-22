package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.preferences.BatteryPreferences
import com.tk.quicksearch.search.data.preferences.BatteryPreferences.Companion.LOW_BATTERY_THRESHOLD_PERCENT
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import java.text.NumberFormat

/** The low battery warning shown in the home At a Glance card. */
internal class LowBatteryGlance(
    val percent: Int,
    val isPowerSaveOn: Boolean,
    val open: () -> Unit,
)

private data class BatteryStatus(
    val percent: Int,
    val isCharging: Boolean,
) {
    val isLow: Boolean get() = !isCharging && percent <= LOW_BATTERY_THRESHOLD_PERCENT

    companion object {
        fun from(intent: Intent?): BatteryStatus? {
            intent ?: return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            return BatteryStatus(
                percent = level * 100 / scale,
                isCharging =
                    plugged != 0 ||
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
    }
}

/** Listens to the battery broadcast while [enabled] and the toggle is on. */
@Composable
internal fun rememberLowBatteryGlance(enabled: Boolean): LowBatteryGlance? {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember(appContext) { BatteryPreferences(appContext) }
    val powerManager = remember(appContext) { appContext.getSystemService(PowerManager::class.java) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var battery by remember { mutableStateOf<BatteryStatus?>(null) }
    var isPowerSaveOn by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(appContext, enabled, refreshKey) {
        if (!enabled || !preferences.isShowLowBatteryEnabled()) {
            battery = null
            return@DisposableEffect onDispose {}
        }
        fun updateBattery(intent: Intent?) {
            battery = BatteryStatus.from(intent) ?: return
        }
        fun updatePowerSave() {
            isPowerSaveOn = runCatching { powerManager?.isPowerSaveMode }.getOrNull() == true
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        Intent.ACTION_BATTERY_CHANGED -> updateBattery(intent)
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> updatePowerSave()
                    }
                }
            }
        val filter =
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
        // Battery changes are sticky, so registering hands back the current level immediately.
        val sticky =
            runCatching {
                ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }.getOrNull()
        updateBattery(sticky)
        updatePowerSave()
        onDispose { runCatching { appContext.unregisterReceiver(receiver) } }
    }

    val current = battery?.takeIf { it.isLow } ?: return null
    return LowBatteryGlance(
        percent = current.percent,
        isPowerSaveOn = isPowerSaveOn,
        open = { openBatterySettings(context) },
    )
}

private fun openBatterySettings(context: Context) {
    val intents =
        listOf(
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent(Settings.ACTION_SETTINGS),
        )
    intents.firstOrNull { intent ->
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

@Composable
internal fun LowBatteryRow(glance: LowBatteryGlance) {
    val percentLabel =
        remember(glance.percent) { NumberFormat.getPercentInstance().format(glance.percent / 100.0) }
    val subtitle =
        stringResource(
            if (glance.isPowerSaveOn) R.string.home_low_battery_saver_on else R.string.home_low_battery_saver_off,
        )

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(
            start = 7.dp,
            end = 7.dp,
            top = DesignTokens.SpacingMedium,
            bottom = DesignTokens.SpacingMedium,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = glance.open),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BatteryLevelIcon(percent = glance.percent, modifier = Modifier.size(24.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_at_a_glance_low_battery_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = percentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** A sideways battery outline whose red fill tracks [percent]. */
@Composable
private fun BatteryLevelIcon(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val nubWidth = 2.dp.toPx()
        val bodyWidth = size.width - nubWidth - 1.dp.toPx()
        val bodyHeight = size.height * 0.5f
        val top = (size.height - bodyHeight) / 2f

        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(stroke / 2f, top),
            size = Size(bodyWidth - stroke, bodyHeight),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(bodyWidth, top + bodyHeight * 0.3f),
            size = Size(nubWidth, bodyHeight * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )

        val inset = stroke + 1.5.dp.toPx()
        // Keep a sliver visible at 0-1% so the icon never reads as an empty outline.
        val fillWidth = (bodyWidth - inset * 2) * (percent / 100f).coerceIn(0.08f, 1f)
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(inset, top + inset),
            size = Size(fillWidth, bodyHeight - inset * 2),
            cornerRadius = CornerRadius(minOf(1.5.dp.toPx(), fillWidth / 2f)),
        )
    }
}
