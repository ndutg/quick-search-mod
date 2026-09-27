package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.apps.appLock.AppLockGate
import com.tk.quicksearch.search.apps.notificationDots.NotificationDotsPermission
import com.tk.quicksearch.search.apps.rememberAppIcon
import com.tk.quicksearch.search.data.GlanceNotificationsStore
import com.tk.quicksearch.search.data.MissedCallNotification
import com.tk.quicksearch.search.data.OngoingCallNotification
import com.tk.quicksearch.search.data.ProgressNotification
import com.tk.quicksearch.search.data.TimerNotification
import com.tk.quicksearch.search.data.preferences.GlancePreferences
import com.tk.quicksearch.shared.util.sendFromUserTap
import java.text.NumberFormat
import kotlinx.coroutines.delay

/** At most this many progress notifications show on home, newest first. */
private const val MAX_PROGRESS_ROWS = 3

/** Running clock-app timers, live progress notifications, missed and ongoing calls for the home At a Glance card. */
internal class NotificationGlances(
    val timers: List<TimerNotification>,
    val progress: List<ProgressNotification>,
    /** Newest first; shown as a single summary row. */
    val missedCalls: List<MissedCallNotification>,
    /** Hides the missed calls row until a newer missed call comes in. */
    val dismissMissedCalls: () -> Unit,
    val ongoingCalls: List<OngoingCallNotification>,
    /** Wall clock the timer and call rows count from; ticks every second while one shows. */
    val nowMillis: Long,
)

/**
 * Mirrors [GlanceNotificationsStore] while [enabled], each source behind its own toggle and the
 * notification access shared with notification dots and media.
 */
@Composable
internal fun rememberNotificationGlances(enabled: Boolean): NotificationGlances {
    val context = LocalContext.current
    val preferences = remember(context) { GlancePreferences(context.applicationContext) }
    val refreshKey = rememberResumeRefreshKey()
    val allTimers by GlanceNotificationsStore.timers.collectAsState()
    val allProgress by GlanceNotificationsStore.progress.collectAsState()
    val allMissedCalls by GlanceNotificationsStore.missedCalls.collectAsState()
    val allOngoingCalls by GlanceNotificationsStore.ongoingCalls.collectAsState()
    val hasAccess = remember(refreshKey) { NotificationDotsPermission.hasNotificationListenerAccess(context) }
    val showTimers = remember(refreshKey) { preferences.isShowTimersEnabled() }
    val showProgress = remember(refreshKey) { preferences.isShowProgressNotificationsEnabled() }
    val showMissedCalls = remember(refreshKey) { preferences.isShowMissedCallsEnabled() }
    val showOngoingCalls = remember(refreshKey) { preferences.isShowOngoingCallEnabled() }
    val available = enabled && hasAccess
    val timers = if (available && showTimers) allTimers else emptyList()
    val progress = if (available && showProgress) allProgress.take(MAX_PROGRESS_ROWS) else emptyList()
    var missedCallsDismissedAt by remember { mutableLongStateOf(preferences.getMissedCallsDismissedAt()) }
    val missedCalls =
        if (available && showMissedCalls) {
            allMissedCalls.filter { it.callTime > missedCallsDismissedAt }
        } else {
            emptyList()
        }

    val ongoingCalls = if (available && showOngoingCalls) allOngoingCalls else emptyList()

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ticking = timers.isNotEmpty() || ongoingCalls.any { it.startTime != null }
    LaunchedEffect(ticking) {
        while (ticking) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L - nowMillis % 1_000L)
        }
    }
    return NotificationGlances(
        timers = timers,
        progress = progress,
        missedCalls = missedCalls,
        dismissMissedCalls = {
            missedCalls.maxOfOrNull { it.callTime }?.let { newest ->
                preferences.setMissedCallsDismissedAt(newest)
                missedCallsDismissedAt = newest
            }
        },
        ongoingCalls = ongoingCalls,
        nowMillis = nowMillis,
    )
}

/** Opens the notification's own target, behind the app lock, falling back to launching the app. */
internal fun openNotificationTarget(
    context: Context,
    packageName: String,
    contentIntent: PendingIntent?,
) {
    AppLockGate.runAfterUnlock(context, packageName) {
        launchNotificationTarget(context, packageName, contentIntent)
    }
}

/** [openNotificationTarget] for callers already past the app lock. */
internal fun launchNotificationTarget(
    context: Context,
    packageName: String,
    contentIntent: PendingIntent?,
) {
    if (contentIntent?.sendFromUserTap() == true) return
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    runCatching { context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
internal fun rememberAppLabel(packageName: String): String {
    val context = LocalContext.current
    return remember(packageName) {
        val packageManager = context.packageManager
        runCatching {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        }.getOrDefault(packageName)
    }
}

@Composable
internal fun TimerRow(
    timer: TimerNotification,
    nowMillis: Long,
) {
    val context = LocalContext.current
    val elapsedMillis =
        timer.pausedMillis ?: if (timer.isCountDown) {
            (timer.chronometerBase - nowMillis).coerceAtLeast(0L)
        } else {
            (nowMillis - timer.chronometerBase).coerceAtLeast(0L)
        }
    // Round a countdown up so it reads 0:01 until the last second has fully passed, like the clock app.
    val seconds = if (timer.isCountDown) (elapsedMillis + 999L) / 1_000L else elapsedMillis / 1_000L
    GlanceStatusRow(
        icon = {
            Icon(
                imageVector = if (timer.isCountDown) Icons.Rounded.Timer else Icons.Rounded.AvTimer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = stringResource(if (timer.isCountDown) R.string.home_timer else R.string.home_stopwatch),
        subtitle = rememberAppLabel(timer.packageName),
        pillText = DateUtils.formatElapsedTime(seconds),
        onClick = { openNotificationTarget(context, timer.packageName, timer.contentIntent) },
    )
}

/**
 * A connected call with the caller (or the calling app when it doesn't say), its duration, and a
 * hang up button when the notification offers one.
 */
@Composable
internal fun OngoingCallRow(
    call: OngoingCallNotification,
    nowMillis: Long,
) {
    val context = LocalContext.current
    val subtitle = call.caller ?: rememberAppLabel(call.packageName)
    GlanceStatusRow(
        icon = {
            Icon(
                imageVector = Icons.Rounded.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = stringResource(R.string.home_ongoing_call),
        subtitle = subtitle,
        pillText =
            call.startTime?.let { start ->
                DateUtils.formatElapsedTime(((nowMillis - start).coerceAtLeast(0L)) / 1_000L)
            },
        onClick = { openNotificationTarget(context, call.packageName, call.contentIntent) },
        trailing =
            call.hangUpIntent?.let { hangUpIntent ->
                {
                    FilledIconButton(
                        onClick = { hangUpIntent.sendFromUserTap() },
                        modifier = Modifier.size(32.dp),
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CallEnd,
                            contentDescription = stringResource(R.string.home_ongoing_call_hang_up),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
    )
}

@Composable
internal fun ProgressNotificationRow(notification: ProgressNotification) {
    val context = LocalContext.current
    val appLabel = rememberAppLabel(notification.packageName)
    val fraction = notification.progress.toFloat() / notification.progressMax
    val percentLabel = remember(fraction) { NumberFormat.getPercentInstance().format(fraction.toDouble()) }
    GlanceStatusRow(
        icon = { NotificationAppIcon(notification.packageName) },
        title = notification.title ?: appLabel,
        subtitle = notification.text ?: appLabel.takeIf { notification.title != null },
        pillText = percentLabel,
        onClick = {
            openNotificationTarget(context, notification.packageName, notification.contentIntent)
        },
        belowText = {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
            )
        },
    )
}

/** The posting app's icon for a notification row, or a generic app icon while it loads. */
@Composable
internal fun NotificationAppIcon(packageName: String) {
    val appIcon = rememberAppIcon(packageName = packageName).bitmap
    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            imageVector = Icons.Rounded.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
