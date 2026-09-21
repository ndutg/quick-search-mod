package com.tk.quicksearch.settings.settingsDetailScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.preferences.CalendarPreferences
import com.tk.quicksearch.search.data.preferences.ReminderPreferences
import com.tk.quicksearch.search.data.preferences.UpcomingAlarmPreferences
import com.tk.quicksearch.settings.shared.SettingsCard
import com.tk.quicksearch.settings.shared.SettingsToggleRow
import com.tk.quicksearch.shared.ui.theme.DesignTokens

/** Toggles for the glanceable cards shown on the home screen: today's events, alarm and reminders. */
@Composable
fun AtAGlanceSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val calendarPreferences = remember(context) { CalendarPreferences(context.applicationContext) }
    val alarmPreferences = remember(context) { UpcomingAlarmPreferences(context.applicationContext) }
    val reminderPreferences = remember(context) { ReminderPreferences(context.applicationContext) }
    var showTodayEvents by remember { mutableStateOf(calendarPreferences.getShowTodayEvents()) }
    var showUpcomingAlarm by remember { mutableStateOf(alarmPreferences.isShowUpcomingAlarmEnabled()) }
    var showUpcomingReminders by remember {
        mutableStateOf(reminderPreferences.isShowUpcomingRemindersEnabled())
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
    ) {
        Text(
            text = stringResource(R.string.settings_at_a_glance_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.SpacingXSmall),
        )
        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            SettingsToggleRow(
                title = stringResource(R.string.section_calendar),
                subtitle = stringResource(R.string.settings_at_a_glance_events_desc),
                checked = showTodayEvents,
                onCheckedChange = { enabled ->
                    showTodayEvents = enabled
                    calendarPreferences.setShowTodayEvents(enabled)
                },
                isFirstItem = true,
                isLastItem = false,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_at_a_glance_alarms_title),
                subtitle = stringResource(R.string.settings_upcoming_alarm_desc),
                checked = showUpcomingAlarm,
                onCheckedChange = { enabled ->
                    showUpcomingAlarm = enabled
                    alarmPreferences.setShowUpcomingAlarmEnabled(enabled)
                },
                isFirstItem = false,
                isLastItem = false,
            )
            SettingsToggleRow(
                title = stringResource(R.string.section_reminders),
                subtitle = stringResource(R.string.settings_upcoming_reminders_desc),
                checked = showUpcomingReminders,
                onCheckedChange = { enabled ->
                    showUpcomingReminders = enabled
                    reminderPreferences.setShowUpcomingRemindersEnabled(enabled)
                },
                isFirstItem = false,
                isLastItem = true,
            )
        }
    }
}
