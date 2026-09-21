package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import android.text.format.DateFormat
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tk.quicksearch.R
import com.tk.quicksearch.reminders.ReminderEditorRequests
import com.tk.quicksearch.search.calendar.calendarRelativeTimeLabel
import com.tk.quicksearch.search.data.ReminderRepository
import com.tk.quicksearch.search.models.ReminderInfo
import com.tk.quicksearch.search.reminders.reminderOverdueColor
import com.tk.quicksearch.search.reminders.reminderScheduleLabel
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val UpcomingReminderDismissSize = 28.dp
private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
private const val REMINDER_NOW_WINDOW_MILLIS = 60L * 1000L

/**
 * Reminders due within 30 minutes or already overdue, shown in the home At a Glance card. A
 * reminder stays until it is marked done or dismissed for the current day; dismissing it does not
 * cancel the notification.
 */
internal class UpcomingRemindersGlance(
    val reminders: List<ReminderInfo>,
    val nowMillis: Long,
    val markDone: (ReminderInfo) -> Unit,
    val dismiss: (ReminderInfo) -> Unit,
    val delete: (ReminderInfo) -> Unit,
)

/** Polls the home-card reminders while [enabled]; refreshes on resume, on edits and every 10 seconds. */
@Composable
internal fun rememberUpcomingRemindersGlance(enabled: Boolean): UpcomingRemindersGlance {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { ReminderRepository(context) }
    val changeCount by ReminderRepository.changes.collectAsState()
    var refreshKey by remember { mutableIntStateOf(0) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var reminders by remember { mutableStateOf(emptyList<ReminderInfo>()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(repository, refreshKey, changeCount, enabled) {
        if (!enabled) {
            reminders = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            nowMillis = System.currentTimeMillis()
            reminders = withContext(Dispatchers.IO) { repository.getHomeCardReminders(nowMillis) }
            delay(10_000)
        }
    }

    fun removeLocally(reminder: ReminderInfo) {
        reminders = reminders.filterNot { it.reminderId == reminder.reminderId }
    }
    return UpcomingRemindersGlance(
        reminders = reminders,
        nowMillis = nowMillis,
        markDone = { reminder ->
            removeLocally(reminder)
            scope.launch(Dispatchers.IO) { repository.setDone(reminder.reminderId, true) }
        },
        dismiss = { reminder ->
            removeLocally(reminder)
            scope.launch(Dispatchers.IO) { repository.dismissFromHome(reminder.reminderId) }
        },
        delete = { reminder ->
            removeLocally(reminder)
            scope.launch(Dispatchers.IO) { repository.deleteReminder(reminder.reminderId) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun UpcomingReminderRow(
    reminder: ReminderInfo,
    nowMillis: Long,
    onClick: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    // Keep a timed reminder at its due time in a brief "Now" state before it becomes overdue.
    // This also avoids showing "1 minute ago" during the first minute after it is due.
    val elapsedSinceDueMillis = nowMillis - reminder.dueMillis
    val isNow =
        !reminder.isDone &&
            reminder.hasTime &&
            elapsedSinceDueMillis in 0 until REMINDER_NOW_WINDOW_MILLIS
    // Overdue reminders show their time (or date once a day late) with a red "Overdue" in place of
    // the relative label; upcoming ones read best relative to now.
    val isOverdue = reminder.isOverdue(nowMillis) && !isNow
    val time = remember(reminder.dueMillis, context) {
        DateFormat.getTimeFormat(context).format(Date(reminder.dueMillis))
    }
    val scheduleText =
        when {
            !isOverdue && !isNow -> "$time • ${calendarRelativeTimeLabel(reminder.dueMillis, nowMillis)}"
            nowMillis - reminder.dueMillis < DAY_MILLIS -> time
            else -> reminderScheduleLabel(reminder)
        }
    val statusText =
        when {
            isNow -> stringResource(R.string.calendar_relative_now)
            isOverdue -> stringResource(R.string.reminder_status_overdue)
            else -> null
        }
    val statusColor = if (isNow) AppColors.Accent else reminderOverdueColor()
    val scheduleLabel =
        buildAnnotatedString {
            append(scheduleText)
            if (statusText != null) {
                append(" • ")
                withStyle(SpanStyle(color = statusColor)) { append(statusText) }
            }
        }

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
                onClick = onClick,
                onLongClick = { showMenu = true },
            ),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reminder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scheduleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                    text = { Text(text = stringResource(R.string.action_mark_as_done)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onDone()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.reminders_home_card_dismiss_for_now)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Close, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onDismiss()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.dialog_delete)) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
        IconButton(
            onClick = onDone,
            modifier = Modifier.size(UpcomingReminderDismissSize),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.action_mark_as_done),
                tint = AppColors.Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
