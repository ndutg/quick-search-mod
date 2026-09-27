package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.reminders.ReminderEditorRequests
import com.tk.quicksearch.search.appSettings.AppSettingsDestination
import com.tk.quicksearch.search.appSettings.LocalOpenAppSettingDestination
import com.tk.quicksearch.search.models.ContactInfo
import com.tk.quicksearch.search.searchScreen.LocalOverlayDividerColor
import com.tk.quicksearch.search.searchScreen.shared.SearchResultCard
import com.tk.quicksearch.shared.ui.theme.homeTextColor
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens

/**
 * One row of the home At a Glance card. Today's calendar events are hosted by the calendar card
 * itself; every other glanceable source (ongoing calls, one-time codes, battery, missed calls, flashlight, Wi-Fi sign-in, Do Not Disturb,
 * airplane mode, hotspot, weather, workouts, timers, progress notifications, alarm, reminders, birthdays, tomorrow's
 * events, storage, and future ones) contributes rows here.
 * Rows sit inside the card's inset and follow CalendarEventRow: 7dp before a 24dp icon, then 12dp
 * to the text.
 */
internal class AtAGlanceItem(
    val key: String,
    val content: @Composable () -> Unit,
)

/**
 * Collects the non-calendar At a Glance rows, top to bottom. In the [reversed] (bottom search bar)
 * layout the source groups swap places but rows within a group keep their order, matching how the
 * calendar card never reverses its own events.
 */
@Composable
internal fun rememberAtAGlanceItems(
    enabled: Boolean,
    reversed: Boolean,
    onShowContactMethods: (ContactInfo) -> Unit,
): List<AtAGlanceItem> {
    val battery = rememberBatteryGlances(enabled)
    val notifications = rememberNotificationGlances(enabled)
    val alarm = rememberUpcomingAlarmGlance(enabled)
    val reminders = rememberUpcomingRemindersGlance(enabled)
    val birthdays = rememberBirthdaysGlance(enabled, onShowContactMethods)
    val lowStorage = rememberLowStorageGlance(enabled)
    val doNotDisturb = rememberDoNotDisturbGlance(enabled)
    val wifiSignIn = rememberWifiSignInGlance(enabled)
    val flashlight = rememberFlashlightGlance(enabled)
    val airplaneMode = rememberAirplaneModeGlance(enabled)
    val hotspot = rememberHotspotGlance(enabled)
    val tomorrowEvents = rememberTomorrowEventsGlance(enabled)
    val groups =
        listOf(
            notifications.ongoingCalls.map { call ->
                AtAGlanceItem(key = "ongoing-call-${call.key}") { OngoingCallRow(call, notifications.nowMillis) }
            },
            listOfNotNull(
                notifications.otp?.let { otp ->
                    AtAGlanceItem(key = "otp-${otp.key}") { OtpCodeRow(otp) { notifications.dismissOtp(otp) } }
                },
            ),
            listOfNotNull(battery.lowBattery?.let { AtAGlanceItem(key = "low-battery") { LowBatteryRow(it) } }),
            listOfNotNull(battery.charging?.let { AtAGlanceItem(key = "charging") { ChargingRow(it) } }),
            listOfNotNull(
                notifications.missedCalls.takeIf { it.isNotEmpty() }?.let { calls ->
                    AtAGlanceItem(key = "missed-calls") { MissedCallsRow(calls, notifications.dismissMissedCalls) }
                },
            ),
            listOfNotNull(flashlight?.let { AtAGlanceItem(key = "flashlight") { FlashlightRow(it) } }),
            listOfNotNull(wifiSignIn?.let { AtAGlanceItem(key = "wifi-sign-in") { WifiSignInRow(it) } }),
            listOfNotNull(doNotDisturb?.let { AtAGlanceItem(key = "do-not-disturb") { DoNotDisturbRow(it) } }),
            listOfNotNull(airplaneMode?.let { AtAGlanceItem(key = "airplane-mode") { AirplaneModeRow(it) } }),
            listOfNotNull(hotspot?.let { AtAGlanceItem(key = "hotspot") { HotspotRow(it) } }),
            notifications.weather.map { weather ->
                AtAGlanceItem(key = "weather-${weather.key}") { WeatherRow(weather) }
            },
            notifications.workouts.map { workout ->
                AtAGlanceItem(key = "workout-${workout.key}") { WorkoutRow(workout) }
            },
            notifications.timers.map { timer ->
                AtAGlanceItem(key = "timer-${timer.key}") { TimerRow(timer, notifications.nowMillis) }
            },
            notifications.progress.map { progress ->
                AtAGlanceItem(key = "progress-${progress.key}") { ProgressNotificationRow(progress) }
            } +
                notifications.finishedProgress.map { finished ->
                    AtAGlanceItem(key = "finished-progress-${finished.key}") {
                        FinishedProgressNotificationRow(finished) { notifications.dismissFinishedProgress(finished) }
                    }
                },
            listOfNotNull(alarm?.let { AtAGlanceItem(key = "alarm") { UpcomingAlarmRow(it) } }),
            reminders.reminders.map { reminder ->
                AtAGlanceItem(key = "reminder-${reminder.reminderId}") {
                    UpcomingReminderRow(
                        reminder = reminder,
                        nowMillis = reminders.nowMillis,
                        onClick = { ReminderEditorRequests.openEdit(reminder) },
                        onDone = { reminders.markDone(reminder) },
                        onDismiss = { reminders.dismiss(reminder) },
                        onDelete = { reminders.delete(reminder) },
                    )
                }
            },
            birthdays.birthdays.map { birthday ->
                AtAGlanceItem(key = "birthday-${birthday.contactId}") {
                    BirthdayRow(
                        birthday = birthday,
                        onClick = { birthdays.open(birthday) },
                        onDismiss = { birthdays.dismiss(birthday) },
                    )
                }
            },
            tomorrowEvents.events.map { event ->
                AtAGlanceItem(key = "tomorrow-event-${event.eventId}") {
                    TomorrowEventRow(
                        event = event,
                        onClick = { tomorrowEvents.open(event) },
                        onDismiss = { tomorrowEvents.dismiss(event) },
                    )
                }
            },
            listOfNotNull(lowStorage?.let { AtAGlanceItem(key = "low-storage") { LowStorageRow(it) } }),
        )
    return (if (reversed) groups.asReversed() else groups).flatten()
}

@Composable
internal fun atAGlanceDividerColor(showWallpaperBackground: Boolean) =
    LocalOverlayDividerColor.current
        ?: if (showWallpaperBackground) AppColors.WallpaperDivider else MaterialTheme.colorScheme.outlineVariant

/** Renders [items] separated by dividers, optionally with a divider before or after the group. */
@Composable
internal fun AtAGlanceRows(
    items: List<AtAGlanceItem>,
    showWallpaperBackground: Boolean,
    dividerBefore: Boolean = false,
    dividerAfter: Boolean = false,
) {
    if (items.isEmpty()) return
    val dividerColor = atAGlanceDividerColor(showWallpaperBackground)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (dividerBefore) HorizontalDivider(color = dividerColor)
        items.forEachIndexed { index, item ->
            key(item.key) { item.content() }
            if (index < items.lastIndex) HorizontalDivider(color = dividerColor)
        }
        if (dividerAfter) HorizontalDivider(color = dividerColor)
    }
}

/** Tapping the title opens the At a Glance settings. */
@Composable
internal fun AtAGlanceTitle() {
    val openAppSettingDestination = LocalOpenAppSettingDestination.current
    Text(
        text = stringResource(R.string.settings_at_a_glance_title),
        style = MaterialTheme.typography.titleSmall,
        color = homeTextColor(),
        modifier =
            Modifier
                .padding(horizontal = DesignTokens.SpacingLarge)
                .clickable(enabled = openAppSettingDestination != null, role = Role.Button) {
                    openAppSettingDestination?.invoke(AppSettingsDestination.AT_A_GLANCE)
                },
    )
}

/**
 * Card chrome for At a Glance rows outside the calendar card, with the same inset the calendar card
 * gives its event rows so the rows look the same wherever they are hosted.
 */
@Composable
internal fun AtAGlanceCardShell(
    showWallpaperBackground: Boolean,
    content: @Composable () -> Unit,
) {
    SearchResultCard(
        modifier = Modifier.fillMaxWidth(),
        showWallpaperBackground = showWallpaperBackground,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = DesignTokens.SpacingMedium, vertical = 4.dp),
        ) {
            content()
        }
    }
}

/**
 * The At a Glance card when there are no today's events to host the rows. Like the calendar card,
 * the title and card are emitted into the caller's column so they share its section spacing.
 */
@Composable
internal fun AtAGlanceCard(
    items: List<AtAGlanceItem>,
    showWallpaperBackground: Boolean,
    showTitle: Boolean,
) {
    if (items.isEmpty()) return
    if (showTitle) AtAGlanceTitle()
    AtAGlanceCardShell(showWallpaperBackground = showWallpaperBackground) {
        AtAGlanceRows(items = items, showWallpaperBackground = showWallpaperBackground)
    }
}

/** Media controls sit in their own card under the At a Glance title, apart from the other rows. */
@Composable
internal fun NowPlayingCard(
    glance: NowPlayingGlance,
    showWallpaperBackground: Boolean,
) {
    AtAGlanceCardShell(showWallpaperBackground = showWallpaperBackground) {
        NowPlayingRow(glance)
    }
}
