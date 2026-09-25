package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import com.tk.quicksearch.search.apps.appLock.AppLockGate
import android.app.AlarmManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tk.quicksearch.R
import com.tk.quicksearch.search.calendar.calendarRelativeTimeLabel
import com.tk.quicksearch.search.data.UpcomingAlarmRepository
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import java.util.Date
import kotlinx.coroutines.delay

private val UpcomingAlarmDismissSize = 28.dp

/** The next clock-app alarm shown in the home At a Glance card, with its row actions. */
internal class UpcomingAlarmGlance(
    val alarm: AlarmManager.AlarmClockInfo,
    val nowMillis: Long,
    val open: () -> Boolean,
    val dismiss: () -> Unit,
    /** Label of the app that scheduled the alarm, or null when it cannot be identified. */
    val appLabel: String?,
    val hideAlarmsFromApp: () -> Unit,
)

/** Polls the next alarm within 45 minutes while [enabled]; refreshes on resume and every 10 seconds. */
@Composable
internal fun rememberUpcomingAlarmGlance(enabled: Boolean): UpcomingAlarmGlance? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember(context) { UpcomingAlarmRepository(context) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var alarm by remember { mutableStateOf<AlarmManager.AlarmClockInfo?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(repository, refreshKey, enabled) {
        if (!enabled) {
            alarm = null
            return@LaunchedEffect
        }
        while (true) {
            nowMillis = System.currentTimeMillis()
            alarm = repository.nextWithinFortyFiveMinutes(nowMillis)
            delay(10_000)
        }
    }

    val nextAlarm = alarm ?: return null
    return UpcomingAlarmGlance(
        alarm = nextAlarm,
        nowMillis = nowMillis,
        open = {
            val clockPackage = nextAlarm.showIntent?.creatorPackage
            if (clockPackage != null && AppLockGate.isProtected(context, clockPackage)) {
                AppLockGate.runAfterUnlock(context, clockPackage) { repository.open(nextAlarm) }
                true
            } else {
                repository.open(nextAlarm)
            }
        },
        dismiss = {
            repository.dismiss(nextAlarm)
            alarm = null
        },
        appLabel = remember(nextAlarm) { repository.appLabel(nextAlarm) },
        hideAlarmsFromApp = {
            repository.hideAlarmsFromApp(nextAlarm)
            alarm = null
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UpcomingAlarmRow(glance: UpcomingAlarmGlance) {
    val context = LocalContext.current
    val alarmTime = remember(glance.alarm.triggerTime, context) {
        DateFormat.getTimeFormat(context).format(Date(glance.alarm.triggerTime))
    }
    val scheduleLabel = "$alarmTime • ${calendarRelativeTimeLabel(glance.alarm.triggerTime, glance.nowMillis)}"
    val title = stringResource(R.string.home_upcoming_alarm)
    val failureMessage = stringResource(R.string.common_error_unable_to_open, title)
    val showFailure = { Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show() }
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(
            start = 7.dp,
            top = DesignTokens.SpacingMedium,
            bottom = DesignTokens.SpacingMedium,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).combinedClickable(
                onClick = { if (!glance.open()) showFailure() },
                onLongClick = { if (glance.appLabel != null) showMenu = true },
            ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = scheduleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                shape = RoundedCornerShape(24.dp),
                properties = PopupProperties(focusable = false),
                containerColor = AppColors.DialogBackground,
            ) {
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.home_upcoming_alarm_hide_app, glance.appLabel.orEmpty()))
                    },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.VisibilityOff, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        glance.hideAlarmsFromApp()
                    },
                )
            }
        }
        IconButton(
            onClick = glance.dismiss,
            modifier = Modifier.size(UpcomingAlarmDismissSize),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
