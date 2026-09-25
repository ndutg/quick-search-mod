package com.tk.quicksearch.widgets.mediaControlsWidget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.tk.quicksearch.media.openMediaSessionPlayer
import com.tk.quicksearch.search.apps.appLock.AppLockGate
import com.tk.quicksearch.search.apps.notificationDots.NotificationDotsPermission
import com.tk.quicksearch.search.data.MediaPlaybackRepository

/**
 * Invisible trampoline for the Media Controls widget's album art and title. Starting another app's
 * activity needs a foreground app, so the widget launches this first; it opens the playing app's
 * player, or the notification access screen when the widget cannot read playback yet.
 */
class MediaControlsOpenPlayerActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = MediaPlaybackRepository(this)
        val controller = repository.priorityController()
        when {
            !repository.mediaControlsHasAccess() -> NotificationDotsPermission.openNotificationListenerSettings(this)
            controller != null -> {
                // A locked player stays shut until the prompt succeeds; finish either way after.
                AppLockGate.runAfterUnlock(this, controller.packageName, onCancelled = ::finish) {
                    openMediaSessionPlayer(this, controller)
                    finish()
                }
                return
            }
        }
        finish()
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, MediaControlsOpenPlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
