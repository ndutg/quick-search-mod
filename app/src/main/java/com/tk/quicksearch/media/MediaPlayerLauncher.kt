package com.tk.quicksearch.media

import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import com.tk.quicksearch.shared.util.sendFromUserTap

/**
 * Opens the player behind [controller]. The session activity usually opens the player itself; fall
 * back to the app's launcher entry when the session has none or the send is refused.
 */
fun openMediaSessionPlayer(
    context: Context,
    controller: MediaController,
) {
    val openedSessionActivity =
        runCatching { controller.sessionActivity }.getOrNull()?.sendFromUserTap() == true
    if (openedSessionActivity) return
    val launchIntent = context.packageManager.getLaunchIntentForPackage(controller.packageName) ?: return
    runCatching { context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
