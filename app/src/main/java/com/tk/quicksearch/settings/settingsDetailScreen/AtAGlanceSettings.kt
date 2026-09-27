package com.tk.quicksearch.settings.settingsDetailScreen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tk.quicksearch.R
import com.tk.quicksearch.search.apps.notificationDots.NotificationDotsPermission
import com.tk.quicksearch.search.apps.notificationDots.rememberNotificationDotsCheckedChange
import com.tk.quicksearch.search.data.preferences.BatteryPreferences
import com.tk.quicksearch.search.data.preferences.CalendarPreferences
import com.tk.quicksearch.search.data.preferences.GlancePreferences
import com.tk.quicksearch.search.data.preferences.MediaPreferences
import com.tk.quicksearch.search.data.preferences.ReminderPreferences
import com.tk.quicksearch.search.data.preferences.UpcomingAlarmPreferences
import com.tk.quicksearch.settings.shared.SettingsCard
import com.tk.quicksearch.settings.shared.SettingsToggleRow
import com.tk.quicksearch.shared.permissions.PermissionHelper
import com.tk.quicksearch.shared.ui.components.AppAlertDialog
import com.tk.quicksearch.shared.ui.theme.DesignTokens

/** One At a Glance toggle; [searchText] is what the page's search bar matches against. */
private class GlanceToggle(
    val searchText: String,
    val row: @Composable (isFirstItem: Boolean, isLastItem: Boolean) -> Unit,
)

/** A runtime permission behind a toggle: whether it is granted, and a request that turns the toggle on once it is. */
private class PermissionGate(
    val hasAccess: Boolean,
    val request: () -> Unit,
)

/**
 * Tracks [permission] across resumes. While a request is in flight (the dialog or a trip to app
 * settings), [onGranted] runs once the user comes back with the permission granted.
 */
@Composable
private fun rememberPermissionGate(
    permission: String,
    onGranted: () -> Unit,
): PermissionGate {
    val context = LocalContext.current
    val onGrantedState = rememberUpdatedState(onGranted)
    val isGranted = {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    var hasAccess by remember { mutableStateOf(isGranted()) }
    var pendingEnable by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasAccess = isGranted()
                    if (pendingEnable && hasAccess) {
                        pendingEnable = false
                        onGrantedState.value()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            hasAccess = results[permission] == true
            if (hasAccess) {
                pendingEnable = false
                onGrantedState.value()
            } else {
                // Keep waiting only if the denial sent the user to app settings to grant it there.
                pendingEnable =
                    PermissionHelper.handleDeniedRuntimePermission(
                        context = context,
                        permission = permission,
                        wasPreviouslyDenied = true,
                    )
            }
        }
    return PermissionGate(hasAccess = hasAccess) {
        pendingEnable = true
        PermissionHelper.requestRuntimePermissionOrOpenSettings(
            context = context,
            permission = permission,
            wasPreviouslyDenied = false,
            runtimeLauncher = launcher,
        )
    }
}

/**
 * Toggles for the glanceable rows shown on the home screen, filtered by [searchQuery] from the
 * page's bottom search bar. Give [modifier] a bounded height; the toggles card scrolls within it.
 */
@Composable
fun AtAGlanceSettingsSection(
    modifier: Modifier = Modifier,
    searchQuery: String = "",
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val calendarPreferences = remember(context) { CalendarPreferences(appContext) }
    val alarmPreferences = remember(context) { UpcomingAlarmPreferences(appContext) }
    val reminderPreferences = remember(context) { ReminderPreferences(appContext) }
    val mediaPreferences = remember(context) { MediaPreferences(appContext) }
    val batteryPreferences = remember(context) { BatteryPreferences(appContext) }
    val glancePreferences = remember(context) { GlancePreferences(appContext) }
    var showTodayEvents by remember { mutableStateOf(calendarPreferences.getShowTodayEvents()) }
    var showUpcomingAlarm by remember { mutableStateOf(alarmPreferences.isShowUpcomingAlarmEnabled()) }
    var hiddenAlarmPackages by remember { mutableStateOf(alarmPreferences.getHiddenPackages()) }
    var showHiddenAlarmAppsDialog by remember { mutableStateOf(false) }
    var showUpcomingReminders by remember {
        mutableStateOf(reminderPreferences.isShowUpcomingRemindersEnabled())
    }
    var showNowPlaying by remember { mutableStateOf(mediaPreferences.isShowNowPlayingEnabled()) }
    var showLowBattery by remember { mutableStateOf(batteryPreferences.isShowLowBatteryEnabled()) }
    var showCharging by remember { mutableStateOf(batteryPreferences.isShowChargingEnabled()) }
    var showBirthdays by remember { mutableStateOf(glancePreferences.isShowBirthdaysEnabled()) }
    var showTimers by remember { mutableStateOf(glancePreferences.isShowTimersEnabled()) }
    var showProgress by remember { mutableStateOf(glancePreferences.isShowProgressNotificationsEnabled()) }
    var showLowStorage by remember { mutableStateOf(glancePreferences.isShowLowStorageEnabled()) }
    var showMissedCalls by remember { mutableStateOf(glancePreferences.isShowMissedCallsEnabled()) }
    var showDoNotDisturb by remember { mutableStateOf(glancePreferences.isShowDoNotDisturbEnabled()) }
    var showOngoingCall by remember { mutableStateOf(glancePreferences.isShowOngoingCallEnabled()) }
    var showWorkouts by remember { mutableStateOf(glancePreferences.isShowWorkoutsEnabled()) }
    var showOtpCodes by remember { mutableStateOf(glancePreferences.isShowOtpCodesEnabled()) }
    var showWeather by remember { mutableStateOf(glancePreferences.isShowWeatherEnabled()) }
    var showAirplaneMode by remember { mutableStateOf(glancePreferences.isShowAirplaneModeEnabled()) }
    var showHotspot by remember { mutableStateOf(glancePreferences.isShowHotspotEnabled()) }
    var showWifiSignIn by remember { mutableStateOf(glancePreferences.isShowWifiSignInEnabled()) }
    var showFlashlight by remember { mutableStateOf(glancePreferences.isShowFlashlightEnabled()) }
    var hasNotificationAccess by remember {
        mutableStateOf(NotificationDotsPermission.hasNotificationListenerAccess(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasNotificationAccess = NotificationDotsPermission.hasNotificationListenerAccess(context)
                    hiddenAlarmPackages = alarmPreferences.getHiddenPackages()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val calendarGate =
        rememberPermissionGate(Manifest.permission.READ_CALENDAR) {
            showTodayEvents = true
            calendarPreferences.setShowTodayEvents(true)
        }
    val contactsGate =
        rememberPermissionGate(Manifest.permission.READ_CONTACTS) {
            showBirthdays = true
            glancePreferences.setShowBirthdaysEnabled(true)
        }
    val needsPermissionText = stringResource(R.string.settings_overlay_source_needs_permission)

    // These reuse the notification-listener grant already used for notification dots, so the
    // request dialog and permission bookkeeping are not duplicated for each feature.
    val onShowNowPlayingCheckedChange =
        rememberNotificationDotsCheckedChange { enabled ->
            showNowPlaying = enabled
            mediaPreferences.setShowNowPlayingEnabled(enabled)
        }
    val onShowTimersCheckedChange =
        rememberNotificationDotsCheckedChange { enabled ->
            showTimers = enabled
            glancePreferences.setShowTimersEnabled(enabled)
        }
    val onShowProgressCheckedChange =
        rememberNotificationDotsCheckedChange { enabled ->
            showProgress = enabled
            glancePreferences.setShowProgressNotificationsEnabled(enabled)
        }
    val onShowMissedCallsCheckedChange =
        rememberNotificationDotsCheckedChange { enabled ->
            showMissedCalls = enabled
            glancePreferences.setShowMissedCallsEnabled(enabled)
        }
    val onShowOngoingCallCheckedChange =
        rememberNotificationDotsCheckedChange { enabled ->
            showOngoingCall = enabled
            glancePreferences.setShowOngoingCallEnabled(enabled)
        }
    val onShowWorkoutsCheckedChange =
        rememberNotificationDotsCheckedChange { enabled ->
            showWorkouts = enabled
            glancePreferences.setShowWorkoutsEnabled(enabled)
        }
    val onShowOtpCodesCheckedChange =
        rememberNotificationDotsCheckedChange { enabled ->
            showOtpCodes = enabled
            glancePreferences.setShowOtpCodesEnabled(enabled)
        }
    val onShowWeatherCheckedChange =
        rememberNotificationDotsCheckedChange { enabled ->
            showWeather = enabled
            glancePreferences.setShowWeatherEnabled(enabled)
        }

    /** A toggle that needs notification access; its description gives way to the permission hint. */
    fun notificationToggle(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) = GlanceToggle("$title $description") { isFirst, isLast ->
        SettingsToggleRow(
            title = title,
            subtitle = if (hasNotificationAccess) description else needsPermissionText,
            checked = checked && hasNotificationAccess,
            onCheckedChange = onCheckedChange,
            enabled = hasNotificationAccess,
            onDisabledClick = { onCheckedChange(true) },
            isFirstItem = isFirst,
            isLastItem = isLast,
        )
    }

    /** A toggle that needs a runtime permission tracked by [gate]. */
    fun permissionToggle(
        title: String,
        description: String,
        checked: Boolean,
        gate: PermissionGate,
        onCheckedChange: (Boolean) -> Unit,
    ) = GlanceToggle("$title $description") { isFirst, isLast ->
        SettingsToggleRow(
            title = title,
            subtitle = if (gate.hasAccess) description else needsPermissionText,
            checked = checked && gate.hasAccess,
            onCheckedChange = onCheckedChange,
            enabled = gate.hasAccess,
            onDisabledClick = gate.request,
            isFirstItem = isFirst,
            isLastItem = isLast,
        )
    }

    /** A toggle with no permission requirement. */
    fun simpleToggle(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
    ) = GlanceToggle("$title $description") { isFirst, isLast ->
        SettingsToggleRow(
            title = title,
            subtitle = description,
            checked = checked,
            onCheckedChange = onCheckedChange,
            isFirstItem = isFirst,
            isLastItem = isLast,
        )
    }

    val alarmsTitle = stringResource(R.string.settings_at_a_glance_alarms_title)
    val alarmsDescription = stringResource(R.string.settings_upcoming_alarm_desc)
    val hiddenAlarmAppsLabel = stringResource(R.string.settings_hidden_alarm_apps_title)
    val toggles =
        listOf(
            notificationToggle(
                title = stringResource(R.string.section_media),
                description = stringResource(R.string.settings_now_playing_desc),
                checked = showNowPlaying,
                onCheckedChange = onShowNowPlayingCheckedChange,
            ),
            permissionToggle(
                title = stringResource(R.string.section_calendar),
                description = stringResource(R.string.settings_at_a_glance_events_desc),
                checked = showTodayEvents,
                gate = calendarGate,
            ) { enabled ->
                showTodayEvents = enabled
                calendarPreferences.setShowTodayEvents(enabled)
            },
            notificationToggle(
                title = stringResource(R.string.settings_at_a_glance_ongoing_call_title),
                description = stringResource(R.string.settings_at_a_glance_ongoing_call_desc),
                checked = showOngoingCall,
                onCheckedChange = onShowOngoingCallCheckedChange,
            ),
            notificationToggle(
                title = stringResource(R.string.settings_at_a_glance_otp_title),
                description = stringResource(R.string.settings_at_a_glance_otp_desc),
                checked = showOtpCodes,
                onCheckedChange = onShowOtpCodesCheckedChange,
            ),
            notificationToggle(
                title = stringResource(R.string.settings_at_a_glance_missed_calls_title),
                description = stringResource(R.string.settings_at_a_glance_missed_calls_desc),
                checked = showMissedCalls,
                onCheckedChange = onShowMissedCallsCheckedChange,
            ),
            simpleToggle(
                title = stringResource(R.string.settings_shortcut_do_not_disturb),
                description = stringResource(R.string.settings_at_a_glance_dnd_desc),
                checked = showDoNotDisturb,
            ) { enabled ->
                showDoNotDisturb = enabled
                glancePreferences.setShowDoNotDisturbEnabled(enabled)
            },
            simpleToggle(
                title = stringResource(R.string.settings_shortcut_airplane),
                description = stringResource(R.string.settings_at_a_glance_airplane_desc),
                checked = showAirplaneMode,
            ) { enabled ->
                showAirplaneMode = enabled
                glancePreferences.setShowAirplaneModeEnabled(enabled)
            },
            simpleToggle(
                title = stringResource(R.string.settings_shortcut_hotspot),
                description = stringResource(R.string.settings_at_a_glance_hotspot_desc),
                checked = showHotspot,
            ) { enabled ->
                showHotspot = enabled
                glancePreferences.setShowHotspotEnabled(enabled)
            },
            simpleToggle(
                title = stringResource(R.string.settings_at_a_glance_wifi_sign_in_title),
                description = stringResource(R.string.settings_at_a_glance_wifi_sign_in_desc),
                checked = showWifiSignIn,
            ) { enabled ->
                showWifiSignIn = enabled
                glancePreferences.setShowWifiSignInEnabled(enabled)
            },
            simpleToggle(
                title = stringResource(R.string.settings_at_a_glance_flashlight_title),
                description = stringResource(R.string.settings_at_a_glance_flashlight_desc),
                checked = showFlashlight,
            ) { enabled ->
                showFlashlight = enabled
                glancePreferences.setShowFlashlightEnabled(enabled)
            },
            GlanceToggle("$alarmsTitle $alarmsDescription") { isFirst, isLast ->
                SettingsToggleRow(
                    title = alarmsTitle,
                    subtitle = if (hiddenAlarmPackages.isEmpty()) alarmsDescription else null,
                    subtitleContent =
                        if (hiddenAlarmPackages.isEmpty()) {
                            null
                        } else {
                            {
                                Text(
                                    text = hiddenAlarmAppsLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showHiddenAlarmAppsDialog = true },
                                )
                            }
                        },
                    checked = showUpcomingAlarm,
                    onCheckedChange = { enabled ->
                        showUpcomingAlarm = enabled
                        alarmPreferences.setShowUpcomingAlarmEnabled(enabled)
                    },
                    isFirstItem = isFirst,
                    isLastItem = isLast,
                )
            },
            notificationToggle(
                title = stringResource(R.string.weather_toggle_title),
                description = stringResource(R.string.settings_at_a_glance_weather_desc),
                checked = showWeather,
                onCheckedChange = onShowWeatherCheckedChange,
            ),
            notificationToggle(
                title = stringResource(R.string.settings_at_a_glance_workouts_title),
                description = stringResource(R.string.settings_at_a_glance_workouts_desc),
                checked = showWorkouts,
                onCheckedChange = onShowWorkoutsCheckedChange,
            ),
            notificationToggle(
                title = stringResource(R.string.settings_at_a_glance_timers_title),
                description = stringResource(R.string.settings_at_a_glance_timers_desc),
                checked = showTimers,
                onCheckedChange = onShowTimersCheckedChange,
            ),
            simpleToggle(
                title = stringResource(R.string.section_reminders),
                description = stringResource(R.string.settings_upcoming_reminders_desc),
                checked = showUpcomingReminders,
            ) { enabled ->
                showUpcomingReminders = enabled
                reminderPreferences.setShowUpcomingRemindersEnabled(enabled)
            },
            permissionToggle(
                title = stringResource(R.string.settings_at_a_glance_birthdays_title),
                description = stringResource(R.string.settings_at_a_glance_birthdays_desc),
                checked = showBirthdays,
                gate = contactsGate,
            ) { enabled ->
                showBirthdays = enabled
                glancePreferences.setShowBirthdaysEnabled(enabled)
            },
            notificationToggle(
                title = stringResource(R.string.settings_at_a_glance_progress_title),
                description = stringResource(R.string.settings_at_a_glance_progress_desc),
                checked = showProgress,
                onCheckedChange = onShowProgressCheckedChange,
            ),
            simpleToggle(
                title = stringResource(R.string.settings_at_a_glance_low_battery_title),
                description =
                    stringResource(R.string.settings_low_battery_desc, BatteryPreferences.LOW_BATTERY_THRESHOLD_PERCENT),
                checked = showLowBattery,
            ) { enabled ->
                showLowBattery = enabled
                batteryPreferences.setShowLowBatteryEnabled(enabled)
            },
            simpleToggle(
                title = stringResource(R.string.settings_at_a_glance_charging_title),
                description = stringResource(R.string.settings_at_a_glance_charging_desc),
                checked = showCharging,
            ) { enabled ->
                showCharging = enabled
                batteryPreferences.setShowChargingEnabled(enabled)
            },
            simpleToggle(
                title = stringResource(R.string.settings_at_a_glance_storage_title),
                description =
                    stringResource(
                        R.string.settings_at_a_glance_storage_desc,
                        GlancePreferences.LOW_STORAGE_THRESHOLD_PERCENT,
                    ),
                checked = showLowStorage,
            ) { enabled ->
                showLowStorage = enabled
                glancePreferences.setShowLowStorageEnabled(enabled)
            },
        )
    val query = searchQuery.trim()
    val visibleToggles =
        if (query.isEmpty()) toggles else toggles.filter { it.searchText.contains(query, ignoreCase = true) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingLarge),
    ) {
        Text(
            text = stringResource(R.string.settings_at_a_glance_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.SpacingXSmall),
        )
        if (visibleToggles.isEmpty()) {
            Text(
                text = stringResource(R.string.widget_custom_buttons_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.SpacingLarge),
            )
        } else {
            // The page itself doesn't scroll: the card takes the height left above the search bar
            // and scrolls its rows inside, shrinking to fit when they don't fill it.
            SettingsCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    visibleToggles.forEachIndexed { index, toggle ->
                        toggle.row(index == 0, index == visibleToggles.lastIndex)
                    }
                }
            }
        }
    }

    if (showHiddenAlarmAppsDialog) {
        HiddenAlarmAppsDialog(
            packageNames = hiddenAlarmPackages,
            onUnhide = { packageName ->
                hiddenAlarmPackages = alarmPreferences.unhidePackage(packageName)
                if (hiddenAlarmPackages.isEmpty()) showHiddenAlarmAppsDialog = false
            },
            onDismiss = { showHiddenAlarmAppsDialog = false },
        )
    }
}

/** Lists the apps whose alarms were hidden from At a Glance, each with an Unhide action. */
@Composable
private fun HiddenAlarmAppsDialog(
    packageNames: Set<String>,
    onUnhide: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val apps =
        remember(packageNames, context) {
            val packageManager = context.packageManager
            packageNames
                .map { packageName ->
                    val label =
                        runCatching {
                            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
                        }.getOrDefault(packageName)
                    packageName to label
                }.sortedBy { (_, label) -> label.lowercase() }
        }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_hidden_alarm_apps_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                apps.forEach { (packageName, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onUnhide(packageName) }) {
                            Text(text = stringResource(R.string.action_include_generic))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_close))
            }
        },
    )
}
