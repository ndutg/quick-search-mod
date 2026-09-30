package com.tk.quicksearch.app

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Google Play flexible in-app updates. The user starts the download from the Home update card,
 * and the app restarts only when they tap Restart on that card once the download finishes.
 */
object UpdateHelper {
    private const val TAG = "UpdateHelper"

    /** Most Play users auto-update within a few days, so only prompt users who are behind. */
    private const val MIN_UPDATE_STALENESS_DAYS = 3
    private const val RESUME_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private var appUpdateManager: AppUpdateManager? = null
    private var installStateListener: InstallStateUpdatedListener? = null
    private var lastCheckAtMs = 0L

    fun checkForUpdates(
        activity: Activity,
        onUpdateStateChanged: (AppUpdateState) -> Unit,
    ) {
        lastCheckAtMs = SystemClock.elapsedRealtime()
        try {
            val manager = manager(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { appUpdateInfo ->
                    val state = appUpdateInfo.toUpdateState()
                    if (state == AppUpdateState.DOWNLOADING) {
                        listenForInstallState(manager, onUpdateStateChanged)
                    }
                    onUpdateStateChanged(state)
                }.addOnFailureListener { exception ->
                    Log.w(TAG, "Failed to check for updates", exception)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }
    }

    /**
     * Re-checks on resume at most every few hours. A Home launcher is rarely recreated, so the
     * startup check alone would leave it on an old version or miss a finished download.
     */
    fun checkForUpdatesIfDue(
        activity: Activity,
        onUpdateStateChanged: (AppUpdateState) -> Unit,
    ) {
        // The deferred startup check runs first; don't add Play work to the launch path.
        if (lastCheckAtMs == 0L) return
        if (SystemClock.elapsedRealtime() - lastCheckAtMs < RESUME_CHECK_INTERVAL_MS) return
        checkForUpdates(activity, onUpdateStateChanged)
    }

    fun startUpdate(
        activity: Activity,
        onUpdateStateChanged: (AppUpdateState) -> Unit,
    ) {
        val manager = manager(activity)
        manager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                onUpdateStateChanged(AppUpdateState.READY_TO_INSTALL)
                return@addOnSuccessListener
            }
            if (!appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return@addOnSuccessListener

            listenForInstallState(manager, onUpdateStateChanged)
            manager
                .startUpdateFlow(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                ).addOnSuccessListener { resultCode ->
                    if (resultCode == Activity.RESULT_OK) {
                        onUpdateStateChanged(AppUpdateState.DOWNLOADING)
                    } else {
                        stopListeningForInstallState(manager)
                    }
                }
        }
    }

    /** Installs a downloaded update. Play restarts the app, so call this only on user request. */
    fun completeUpdate(activity: Activity) {
        manager(activity).completeUpdate()
    }

    private fun manager(activity: Activity): AppUpdateManager =
        appUpdateManager
            ?: AppUpdateManagerFactory.create(activity.applicationContext).also {
                appUpdateManager = it
            }

    private fun listenForInstallState(
        manager: AppUpdateManager,
        onUpdateStateChanged: (AppUpdateState) -> Unit,
    ) {
        stopListeningForInstallState(manager)
        val listener =
            InstallStateUpdatedListener { installState ->
                when (installState.installStatus()) {
                    InstallStatus.PENDING,
                    InstallStatus.DOWNLOADING,
                    -> onUpdateStateChanged(AppUpdateState.DOWNLOADING)

                    InstallStatus.DOWNLOADED -> {
                        stopListeningForInstallState(manager)
                        onUpdateStateChanged(AppUpdateState.READY_TO_INSTALL)
                    }

                    InstallStatus.FAILED,
                    InstallStatus.CANCELED,
                    -> {
                        stopListeningForInstallState(manager)
                        onUpdateStateChanged(AppUpdateState.AVAILABLE)
                    }

                    InstallStatus.INSTALLED -> {
                        stopListeningForInstallState(manager)
                        onUpdateStateChanged(AppUpdateState.NONE)
                    }

                    else -> Unit
                }
            }
        installStateListener = listener
        manager.registerListener(listener)
    }

    private fun stopListeningForInstallState(manager: AppUpdateManager) {
        installStateListener?.let(manager::unregisterListener)
        installStateListener = null
    }

    private fun AppUpdateInfo.toUpdateState(): AppUpdateState =
        when (installStatus()) {
            InstallStatus.DOWNLOADED -> AppUpdateState.READY_TO_INSTALL
            InstallStatus.PENDING,
            InstallStatus.DOWNLOADING,
            -> AppUpdateState.DOWNLOADING

            else -> {
                val isStale = (clientVersionStalenessDays() ?: 0) >= MIN_UPDATE_STALENESS_DAYS
                if (
                    updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                    isStale
                ) {
                    AppUpdateState.AVAILABLE
                } else {
                    AppUpdateState.NONE
                }
            }
        }
}
