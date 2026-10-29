package com.tk.quicksearch.search.apps.notificationDots

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.tk.quicksearch.search.data.GlanceNotificationsStore
import com.tk.quicksearch.search.data.preferences.BatteryPreferences
import com.tk.quicksearch.search.notificationHistory.NotificationHistoryAccess
import com.tk.quicksearch.search.notificationHistory.NotificationHistoryStore
import com.tk.quicksearch.widgets.atAGlanceWidget.AtAGlanceWidgetUpdater
import com.tk.quicksearch.widgets.utils.refreshAllWidgets
import com.tk.quicksearch.widgets.utils.refreshAtAGlanceWidgets
import com.tk.quicksearch.widgets.utils.refreshMediaControlsWidgets
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotificationDotsListenerService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeSessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val sessionCallbacks = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val widgetRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Rescans run on one background thread so the binder read of every posted notification does
    // not block the UI sharing this process, and a burst of updates (progress, navigation) collapses
    // into a single rescan. Postings wait here so they are recorded after the rescan they came with.
    private val rescanExecutor = Executors.newSingleThreadExecutor()
    private val pendingPostings = mutableListOf<StatusBarNotification>()
    private var isRescanQueued = false

    /** Ends a charging row dismissal on unplug, even while home is not showing to see it. */
    private val powerDisconnectedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                BatteryPreferences(context.applicationContext).clearChargingDismissal()
            }
        }
    private var isPowerReceiverRegistered = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = this
        onRescanThread {
            val active = runCatching { activeNotifications }.getOrNull()
            NotificationDotsStore.updateFromNotifications(active)
            GlanceNotificationsStore.update(this, active)
            NotificationHistoryStore.seed(this, active, ::isAppNotificationCandidate)
        }
        // A process started for the listener has no widget drawing yet to start the updater.
        AtAGlanceWidgetUpdater.startIfPlaced(this)
        NotificationHistoryAccess.set(true)
        startMediaPlaybackMonitoring()
        if (!isPowerReceiverRegistered) {
            isPowerReceiverRegistered =
                runCatching {
                    ContextCompat.registerReceiver(
                        this,
                        powerDisconnectedReceiver,
                        IntentFilter(Intent.ACTION_POWER_DISCONNECTED),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                }.isSuccess
        }
    }

    private fun unregisterPowerReceiver() {
        if (!isPowerReceiverRegistered) return
        runCatching { unregisterReceiver(powerDisconnectedReceiver) }
        isPowerReceiverRegistered = false
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (connected === this) connected = null
        onRescanThread {
            NotificationDotsStore.clear()
            GlanceNotificationsStore.clear()
        }
        NotificationHistoryAccess.set(false)
        stopMediaPlaybackMonitoring()
        unregisterPowerReceiver()
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        widgetRefreshScope.cancel()
        rescanExecutor.shutdown()
        unregisterPowerReceiver()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        requestRescan(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        requestRescan(null)
    }

    /** Queues one rescan for any number of changes that arrive before it runs. */
    private fun requestRescan(posted: StatusBarNotification?) {
        synchronized(pendingPostings) {
            if (posted != null) pendingPostings += posted
            if (isRescanQueued) return
            isRescanQueued = true
        }
        onRescanThread {
            val postings =
                synchronized(pendingPostings) {
                    isRescanQueued = false
                    pendingPostings.toList().also { pendingPostings.clear() }
                }
            updateActiveNotificationStores()
            postings.forEach { NotificationHistoryStore.record(this, it, ::isAppNotificationCandidate) }
        }
    }

    private fun onRescanThread(block: () -> Unit) {
        // Rejected once the service is destroyed, when there is nothing left to update.
        runCatching { rescanExecutor.execute { runCatching(block) } }
    }

    // Asked after the stores update, so notifications other At a Glance rows show are known.
    private fun isAppNotificationCandidate(sbn: StatusBarNotification): Boolean =
        GlanceNotificationsStore.isAppNotificationCandidate(this, sbn)

    private fun updateActiveNotificationStores() {
        val active = runCatching { activeNotifications }.getOrNull()
        NotificationDotsStore.updateFromNotifications(active)
        GlanceNotificationsStore.update(this, active)
    }

    /**
     * Mirrors active sessions' playback to placed widgets, so a Play/Pause button's icon (and the
     * media controls widget's track) updates as soon as playback changes rather than on the
     * widget's next unrelated redraw.
     */
    private fun startMediaPlaybackMonitoring() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        mediaSessionManager = manager
        val component = ComponentName(this, NotificationDotsListenerService::class.java)
        val listener =
            MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
                onActiveSessionsChanged(controllers.orEmpty())
            }
        runCatching {
            manager.addOnActiveSessionsChangedListener(listener, component)
            activeSessionsListener = listener
            onActiveSessionsChanged(manager.getActiveSessions(component))
        }
    }

    private fun stopMediaPlaybackMonitoring() {
        activeSessionsListener?.let { listener ->
            runCatching { mediaSessionManager?.removeOnActiveSessionsChangedListener(listener) }
        }
        activeSessionsListener = null
        mediaSessionManager = null
        sessionCallbacks.values.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        sessionCallbacks.clear()
    }

    private fun onActiveSessionsChanged(controllers: List<MediaController>) {
        val activeTokens = controllers.map { it.sessionToken }.toSet()
        sessionCallbacks.keys.filter { it !in activeTokens }.forEach { token ->
            sessionCallbacks.remove(token)?.let { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
        }
        controllers.forEach { controller ->
            if (sessionCallbacks.containsKey(controller.sessionToken)) return@forEach
            val callback =
                object : MediaController.Callback() {
                    // Sessions also report position updates as state changes, which no widget
                    // shows and which are too costly to redraw for every second.
                    private var lastWidgetState: Triple<Int, Long, List<String>>? = null
                    private var settleRefresh: Job? = null

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        // Compare the delivered state, not a re-read of controller.playbackState:
                        // as the session commits its new state that read can still expose the
                        // previous one (especially on pause), leaving the widget icon stuck on Pause.
                        // The state, supported actions (seeking) and custom buttons are all the
                        // widgets show of it.
                        val widgetState =
                            Triple(
                                state?.state ?: PlaybackState.STATE_NONE,
                                state?.actions ?: 0L,
                                state?.customActions.orEmpty().map { it.action },
                            )
                        if (widgetState == lastWidgetState) return
                        lastWidgetState = widgetState
                        refreshWidgets()
                        refreshGlanceWidgets()
                        // The widgets read the controller when they draw, so redraw once more after
                        // the session has settled in case the first draw saw the previous state.
                        settleRefresh?.cancel()
                        settleRefresh =
                            widgetRefreshScope.launch {
                                delay(PLAYBACK_SETTLE_REFRESH_DELAY_MS)
                                refreshAllWidgets(this@NotificationDotsListenerService)
                            }
                    }

                    // Only the media controls and At a Glance widgets show the track, so a new
                    // title or artwork does not need to redraw the search widgets.
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        widgetRefreshScope.launch {
                            refreshMediaControlsWidgets(this@NotificationDotsListenerService)
                        }
                        refreshGlanceWidgets()
                    }

                    override fun onSessionDestroyed() {
                        sessionCallbacks.remove(controller.sessionToken)
                    }
                }
            runCatching { controller.registerCallback(callback) }
            sessionCallbacks[controller.sessionToken] = controller to callback
        }
        refreshWidgets()
        refreshGlanceWidgets()
    }

    /** Redraws At a Glance widgets for their Now Playing row. */
    private fun refreshGlanceWidgets() {
        widgetRefreshScope.launch {
            refreshAtAGlanceWidgets(this@NotificationDotsListenerService)
        }
    }

    private fun refreshWidgets() {
        widgetRefreshScope.launch {
            refreshAllWidgets(this@NotificationDotsListenerService)
        }
    }

    companion object {
        private const val PLAYBACK_SETTLE_REFRESH_DELAY_MS = 750L

        @Volatile
        private var connected: NotificationDotsListenerService? = null

        /** Clears a posted notification from the shade; the system ignores ongoing ones. */
        fun cancelNotification(key: String) {
            connected?.let { service -> runCatching { service.cancelNotification(key) } }
        }
    }
}
