package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import android.app.AlarmManager
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Close
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tk.quicksearch.R
import com.tk.quicksearch.search.calendar.calendarRelativeTimeLabel
import com.tk.quicksearch.search.data.UpcomingAlarmRepository
import com.tk.quicksearch.search.searchScreen.shared.SearchResultCard
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import java.util.Date
import kotlinx.coroutines.delay

private val UpcomingAlarmDismissSize = 28.dp

@Composable
internal fun UpcomingAlarmSection(showWallpaperBackground: Boolean) {
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
    LaunchedEffect(repository, refreshKey) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            alarm = repository.nextWithinFortyFiveMinutes(nowMillis)
            delay(10_000)
        }
    }

    val nextAlarm = alarm ?: return
    val alarmTime = remember(nextAlarm.triggerTime, context) {
        DateFormat.getTimeFormat(context).format(Date(nextAlarm.triggerTime))
    }
    val scheduleLabel = "$alarmTime · ${calendarRelativeTimeLabel(nextAlarm.triggerTime, nowMillis)}"
    val title = stringResource(R.string.home_upcoming_alarm)
    val failureMessage = stringResource(R.string.common_error_unable_to_open, title)
    val showFailure = { Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show() }

    SearchResultCard(
        modifier = Modifier.fillMaxWidth(),
        showWallpaperBackground = showWallpaperBackground,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = DesignTokens.SpacingLarge,
                vertical = DesignTokens.SpacingMedium,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clickable {
                    if (!repository.open(nextAlarm)) showFailure()
                },
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingXSmall)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = scheduleLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = {
                    repository.dismiss(nextAlarm)
                    alarm = null
                },
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
}
