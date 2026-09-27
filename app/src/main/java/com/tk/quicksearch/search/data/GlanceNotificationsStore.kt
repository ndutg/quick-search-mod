package com.tk.quicksearch.search.data

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.RemoteViews
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A running timer or stopwatch posted by a clock app, counted from [chronometerBase]. */
internal class TimerNotification(
    val key: String,
    val packageName: String,
    val isCountDown: Boolean,
    /** Wall-clock time the timer ends, or the stopwatch started. */
    val chronometerBase: Long,
    val contentIntent: PendingIntent?,
    /** The frozen time left (or elapsed) while paused; null while it runs. */
    val pausedMillis: Long? = null,
)

/** A missed call notification; [caller] is usually the name or number. */
internal class MissedCallNotification(
    val key: String,
    val packageName: String,
    /** Posted by the default phone app rather than a calling app such as WhatsApp. */
    val fromPhoneApp: Boolean,
    val caller: String?,
    /** When the call came in, or when the notification was posted if the app does not say. */
    val callTime: Long,
    val contentIntent: PendingIntent?,
)

/** A call in progress; [caller] is usually the name or number. */
internal class OngoingCallNotification(
    val key: String,
    val packageName: String,
    val caller: String?,
    /** Wall-clock time the call connected, when the notification shows its duration. */
    val startTime: Long?,
    val contentIntent: PendingIntent?,
    /** The call style notification's own hang up action, if it has one. */
    val hangUpIntent: PendingIntent?,
)

/**
 * The At a Glance view of the posted notifications, fed by
 * [com.tk.quicksearch.search.apps.notificationDots.NotificationDotsListenerService] so it shares
 * the notification access already granted for notification dots and media.
 */
internal object GlanceNotificationsStore {
    private val timersState = MutableStateFlow<List<TimerNotification>>(emptyList())
    private val progressState = MutableStateFlow<List<ProgressNotification>>(emptyList())
    private val finishedProgressState = MutableStateFlow<List<FinishedProgressNotification>>(emptyList())
    private val progressTracker = ProgressNotificationTracker()
    private val missedCallsState = MutableStateFlow<List<MissedCallNotification>>(emptyList())
    private val ongoingCallsState = MutableStateFlow<List<OngoingCallNotification>>(emptyList())
    private val workoutsState = MutableStateFlow<List<WorkoutNotification>>(emptyList())
    private val otpsState = MutableStateFlow<List<OtpNotification>>(emptyList())
    private val weatherState = MutableStateFlow<List<WeatherNotification>>(emptyList())
    private var clockPackages: Set<String>? = null

    /** Timers read from custom chronometer views, by notification key, reused until the notification changes. */
    private val remoteTimerCache = mutableMapOf<String, Pair<Long, TimerNotification?>>()

    /** Call start times read from custom chronometer views, by notification key, reused until the notification changes. */
    private val remoteCallStartCache = mutableMapOf<String, Pair<Long, Long?>>()

    val timers: StateFlow<List<TimerNotification>> = timersState.asStateFlow()
    val progress: StateFlow<List<ProgressNotification>> = progressState.asStateFlow()
    val finishedProgress: StateFlow<List<FinishedProgressNotification>> = finishedProgressState.asStateFlow()
    val missedCalls: StateFlow<List<MissedCallNotification>> = missedCallsState.asStateFlow()
    val ongoingCalls: StateFlow<List<OngoingCallNotification>> = ongoingCallsState.asStateFlow()
    val workouts: StateFlow<List<WorkoutNotification>> = workoutsState.asStateFlow()
    val otps: StateFlow<List<OtpNotification>> = otpsState.asStateFlow()
    val weather: StateFlow<List<WeatherNotification>> = weatherState.asStateFlow()

    fun update(
        context: Context,
        notifications: Array<StatusBarNotification>?,
    ) {
        val clocks = clockPackages ?: resolveClockPackages(context).also { clockPackages = it }
        val posted =
            notifications.orEmpty().filter { sbn ->
                sbn.packageName != context.packageName &&
                    sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
        // Clock apps need not mark a running timer ongoing (Google Clock does not), so any of their
        // notifications with a chronometer counts.
        val postedKeys = posted.map { it.key }.toSet()
        remoteTimerCache.keys.retainAll(postedKeys)
        remoteCallStartCache.keys.retainAll(postedKeys)
        val timerNotifications = posted.mapNotNull { it.toTimer(context, clocks) }
        val timerKeys = timerNotifications.map { it.key }.toSet()
        timersState.value = timerNotifications.sortedBy { it.chronometerBase }
        val dialerPackage = defaultDialerPackage(context)
        missedCallsState.value =
            posted.mapNotNull { it.toMissedCall(dialerPackage) }.sortedByDescending { it.callTime }
        ongoingCallsState.value =
            posted.mapNotNull { it.toOngoingCall(context) }.sortedByDescending { it.startTime ?: 0L }
        val workouts = WorkoutNotifications.parse(context, posted)
        workoutsState.value = workouts
        // Workouts can be Live Updates too; they keep their own row.
        val workoutKeys = workouts.map { it.key }.toSet()
        progressTracker.update(posted.filter { it.key !in timerKeys && it.key !in workoutKeys })
        progressState.value = progressTracker.progress
        finishedProgressState.value = progressTracker.finished
        otpsState.value = OtpNotifications.parse(posted)
        weatherState.value = WeatherNotifications.parse(context, posted)
    }

    /** Drops cached state when notification access is lost; clock apps are resolved again on reconnect. */
    fun clear() {
        clockPackages = null
        remoteTimerCache.clear()
        remoteCallStartCache.clear()
        WorkoutNotifications.clear()
        OtpNotifications.clear()
        WeatherNotifications.clear()
        timersState.value = emptyList()
        progressTracker.clear()
        progressState.value = emptyList()
        finishedProgressState.value = emptyList()
        missedCallsState.value = emptyList()
        ongoingCallsState.value = emptyList()
        workoutsState.value = emptyList()
        otpsState.value = emptyList()
        weatherState.value = emptyList()
    }

    /** Hides a one-time code and any older ones from At a Glance, leaving their notifications posted. */
    fun dismissOtp(otp: OtpNotification) {
        OtpNotifications.dismiss(otp)
        otpsState.value = otpsState.value.filter { it.postTime > otp.postTime }
    }

    /** Hides a finished progress notification from At a Glance, leaving it posted. */
    fun dismissFinishedProgress(key: String) {
        progressTracker.dismiss(key)
        finishedProgressState.value = progressTracker.finished
    }

    private fun defaultDialerPackage(context: Context): String? =
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()

    /**
     * Phone apps mark missed calls with the missed call category (Android 10+), and older ones at
     * least post them to a channel named for it, so the default dialer's channel id is checked too.
     */
    private fun StatusBarNotification.toMissedCall(dialerPackage: String?): MissedCallNotification? {
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return null
        val isMissedCall =
            notification.category == CATEGORY_MISSED_CALL ||
                (
                    packageName == dialerPackage &&
                        notification.channelIdOrNull()?.contains("missed", ignoreCase = true) == true
                )
        if (!isMissedCall) return null
        return MissedCallNotification(
            key = key,
            packageName = packageName,
            fromPhoneApp = packageName == dialerPackage,
            caller = missedCallCaller(),
            callTime = notification.`when`.takeIf { it > 0L } ?: postTime,
            contentIntent = notification.contentIntent,
        )
    }

    /**
     * The caller's name or number. Phone apps put it in the title (Google) or the text (Samsung),
     * with a generic "Missed call" label in the other. The lock screen version leaves the caller
     * out, so a field that also appears there is the label, not the caller.
     */
    private fun StatusBarNotification.missedCallCaller(): String? {
        val extras = notification.extras ?: return null
        val publicExtras = notification.publicVersion?.extras
        val labels =
            listOfNotNull(
                publicExtras?.getCharSequence(Notification.EXTRA_TITLE),
                publicExtras?.getCharSequence(Notification.EXTRA_TEXT),
            ).map { it.toString().trim() }.toSet()
        return listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)
            .mapNotNull { extras.getCharSequence(it)?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull { it !in labels }
    }

    /**
     * A connected call. Call style notifications (Android 12+) say whether the call is ringing or
     * connected; otherwise a ringing call is told apart by its full-screen intent, which connected
     * calls don't use.
     */
    private fun StatusBarNotification.toOngoingCall(context: Context): OngoingCallNotification? {
        if (notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return null
        val extras = notification.extras ?: return null
        // Google Meet posts a call style notification without the call category.
        val isCallStyle = extras.getString(Notification.EXTRA_TEMPLATE) == CALL_STYLE_TEMPLATE
        if (notification.category != Notification.CATEGORY_CALL && !isCallStyle) return toTelegramCall()
        val callType = extras.getInt(EXTRA_CALL_TYPE, CALL_TYPE_UNKNOWN)
        val isConnected =
            if (callType != CALL_TYPE_UNKNOWN) callType == CALL_TYPE_ONGOING else notification.fullScreenIntent == null
        if (!isConnected) return null
        return OngoingCallNotification(
            key = key,
            packageName = packageName,
            caller =
                (callPersonName(extras) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
                    ?.toString()
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
            startTime = callStartTime(context, extras),
            contentIntent = notification.contentIntent,
            hangUpIntent = hangUpIntent(extras),
        )
    }

    /**
     * Telegram (and its forks) posts its ongoing call as a plain foreground service notification with
     * no call category, always under the same id; the title is a localized "Ongoing Telegram call",
     * the text the caller, `when` the time the call started, and the only action ends the call.
     */
    private fun StatusBarNotification.toTelegramCall(): OngoingCallNotification? {
        if (!packageName.startsWith(TELEGRAM_PACKAGE_PREFIX) || id != TELEGRAM_ONGOING_CALL_ID) return null
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE == 0) return null
        val extras = notification.extras ?: return null
        return OngoingCallNotification(
            key = key,
            packageName = packageName,
            caller = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf(String::isNotEmpty),
            startTime = notification.`when`.takeIf { it > 0L },
            contentIntent = notification.contentIntent,
            hangUpIntent = notification.actions?.singleOrNull()?.actionIntent,
        )
    }

    /**
     * When the call connected: the standard chronometer's `when`, or else the base of a running
     * chronometer in a custom layout, as Samsung's in-call screen uses. Null while it doesn't count,
     * such as while dialing.
     */
    private fun StatusBarNotification.callStartTime(
        context: Context,
        extras: Bundle,
    ): Long? {
        if (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) return notification.`when`.takeIf { it > 0L }
        val cached = remoteCallStartCache[key]
        if (cached != null && cached.first == postTime) return cached.second
        val chronometer = findRemoteChronometer(context, extras)
        val start =
            chronometer
                ?.takeIf { !it.isCountDown && it.isStarted() != false }
                ?.let { System.currentTimeMillis() - (SystemClock.elapsedRealtime() - it.base) }
        remoteCallStartCache[key] = postTime to start
        return start
    }

    /**
     * The hang up action, unless it is the same intent as the notification's tap target: Samsung's
     * phone app sets both to open its call screen, so it wouldn't hang up.
     */
    @Suppress("DEPRECATION")
    private fun StatusBarNotification.hangUpIntent(extras: Bundle): PendingIntent? =
        runCatching { extras.getParcelable<PendingIntent>(EXTRA_HANG_UP_INTENT) }
            .getOrNull()
            ?.takeIf { it != notification.contentIntent && it != notification.fullScreenIntent }

    @Suppress("DEPRECATION")
    private fun callPersonName(extras: Bundle): CharSequence? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { extras.getParcelable<Person>(EXTRA_CALL_PERSON)?.name }.getOrNull()
        } else {
            null
        }

    /**
     * [Notification.EXTRA_CALL_TYPE] with its values, [Notification.EXTRA_CALL_PERSON] and
     * [Notification.EXTRA_HANG_UP_INTENT]; only defined from Android 12.
     */
    private const val EXTRA_CALL_TYPE = "android.callType"
    private const val EXTRA_CALL_PERSON = "android.callPerson"
    private const val EXTRA_HANG_UP_INTENT = "android.hangUpIntent"
    private const val CALL_STYLE_TEMPLATE = "android.app.Notification\$CallStyle"

    /** Telegram's package (and its official builds' prefix) and its VoIPService ongoing call notification id. */
    private const val TELEGRAM_PACKAGE_PREFIX = "org.telegram.messenger"
    private const val TELEGRAM_ONGOING_CALL_ID = 201
    private const val CALL_TYPE_UNKNOWN = 0
    private const val CALL_TYPE_ONGOING = 2

    private fun Notification.channelIdOrNull(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) channelId else null


    /** [Notification.CATEGORY_MISSED_CALL], which is only defined from Android 10. */
    private const val CATEGORY_MISSED_CALL = "missed_call"

    /** Apps that handle the standard timer or alarm intents; only their chronometers count as timers. */
    private fun resolveClockPackages(context: Context): Set<String> {
        val packageManager = context.packageManager
        return listOf(AlarmClock.ACTION_SET_TIMER, AlarmClock.ACTION_SHOW_ALARMS)
            .flatMap { action ->
                runCatching { packageManager.queryIntentActivities(Intent(action), 0) }.getOrNull().orEmpty()
            }.mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private fun StatusBarNotification.toTimer(
        context: Context,
        clockPackages: Set<String>,
    ): TimerNotification? {
        if (packageName !in clockPackages) return null
        val extras = notification.extras ?: return null
        if (!extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
            val cached = remoteTimerCache[key]
            if (cached != null && cached.first == postTime) return cached.second
            return toRemoteViewTimer(context, extras).also { remoteTimerCache[key] = postTime to it }
        }
        if (notification.`when` <= 0L) return null
        return TimerNotification(
            key = key,
            packageName = packageName,
            isCountDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
            chronometerBase = notification.`when`,
            contentIntent = notification.contentIntent,
        )
    }

    /**
     * Many clock apps leave the standard chronometer extras unset and show the live count in a
     * chronometer view instead: Samsung Clock in an extra of its own (its `when` is only the post
     * time), Google Clock and other AOSP-based clocks in a custom notification layout. Inflating the
     * first view that has one gives the chronometer's base and direction.
     */
    private fun StatusBarNotification.toRemoteViewTimer(
        context: Context,
        extras: Bundle,
    ): TimerNotification? {
        val chronometer = findRemoteChronometer(context, extras) ?: return null
        val elapsedNow = SystemClock.elapsedRealtime()
        val isCountDown = chronometer.isCountDown
        val base = System.currentTimeMillis() - (elapsedNow - chronometer.base)
        return TimerNotification(
            key = key,
            packageName = packageName,
            isCountDown = isCountDown,
            chronometerBase = base,
            contentIntent = notification.contentIntent,
            pausedMillis =
                if (chronometer.isStarted() == false) {
                    (if (isCountDown) chronometer.base - elapsedNow else elapsedNow - chronometer.base)
                        .coerceAtLeast(0L)
                } else {
                    null
                },
        )
    }

    /**
     * The first chronometer view in the notification's Samsung chronometer extra or its custom
     * layouts. Runs on the listener's main thread, as view inflation needs.
     */
    @Suppress("DEPRECATION")
    private fun StatusBarNotification.findRemoteChronometer(
        context: Context,
        extras: Bundle,
    ): Chronometer? {
        val tag = extras.getString(EXTRA_SAMSUNG_CHRONOMETER_TAG)
        return listOfNotNull(
            extras.getParcelable<RemoteViews>(EXTRA_SAMSUNG_CHRONOMETER_VIEW),
            notification.contentView,
            notification.bigContentView,
            notification.headsUpContentView,
        ).firstNotNullOfOrNull { remoteViews ->
            runCatching {
                val view = remoteViews.apply(context, FrameLayout(context))
                (tag?.let { view.findViewWithTag<View>(it) } as? Chronometer) ?: view.findChronometer()
            }.getOrNull()
        }
    }

    private fun View.findChronometer(): Chronometer? {
        if (this is Chronometer) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findChronometer()?.let { return it }
        }
        return null
    }

    /** Whether the chronometer is counting, or null when that cannot be read (it is not public API). */
    private fun Chronometer.isStarted(): Boolean? =
        runCatching {
            Chronometer::class.java.getDeclaredField("mStarted").apply { isAccessible = true }.getBoolean(this)
        }.getOrNull()

    private const val EXTRA_SAMSUNG_CHRONOMETER_VIEW = "android.ongoingActivityNoti.chronometerRemoteView"
    private const val EXTRA_SAMSUNG_CHRONOMETER_TAG = "android.ongoingActivityNoti.chronometerRemoteViewTag"
}
