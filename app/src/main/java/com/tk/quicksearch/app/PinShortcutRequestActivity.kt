package com.tk.quicksearch.app

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.userAppPreferences.UserAppPreferences
import com.tk.quicksearch.shared.util.AppLanguageManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives [LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT] when Quick Search is the default launcher
 * and another app calls `ShortcutManager.requestPinShortcut`.
 *
 * The request is accepted without a confirmation dialog, so the shortcut becomes a launcher-pinned
 * shortcut returned by the `FLAG_MATCH_PINNED` query in `loadShortcutsViaLauncherApps`. It is also
 * pinned inside Quick Search, and the search screen reloads shortcuts on its next resume.
 */
class PinShortcutRequestActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguageManager.applySavedAppLanguage(this)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { handlePinRequest() }
                .onFailure { Log.w(TAG, "Failed to handle pin request", it) }
        }
        finish()
    }

    private fun handlePinRequest() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return
        val request = launcherApps.getPinItemRequest(intent) ?: return
        if (!request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            return
        }
        val info = request.shortcutInfo ?: return
        // Quick Search's own "Add to Home" items are already searchable in Quick Search.
        if (info.`package` == packageName) return
        if (!request.accept()) return

        val shortcutKey = "${info.`package`}:${info.id}"
        val userPreferences = UserAppPreferences(this)
        userPreferences.removeExcludedAppShortcut(shortcutKey)
        // The user just asked for this shortcut, so it must show even if the app's shortcuts are
        // disabled as a whole; the app's other shortcuts stay disabled.
        userPreferences.enableAppShortcutOverridingApp(
            packageName = info.`package`,
            id = shortcutKey,
            otherAppShortcutIds = otherShortcutKeys(launcherApps, info),
        )
        userPreferences.pinAppShortcut(shortcutKey)
        PinnedShortcutRequests.markRefreshNeeded()

        val label = (info.shortLabel ?: info.longLabel)?.toString()?.takeIf { it.isNotBlank() }
        val message =
            if (label != null) {
                getString(R.string.pin_shortcut_request_added, label)
            } else {
                getString(R.string.pin_shortcut_request_added_generic)
            }
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun otherShortcutKeys(
        launcherApps: LauncherApps,
        info: ShortcutInfo,
    ): List<String> {
        val query =
            LauncherApps.ShortcutQuery().apply {
                setPackage(info.`package`)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
            }
        return runCatching { launcherApps.getShortcuts(query, info.userHandle) }
            .getOrNull()
            .orEmpty()
            .filter { it.id != info.id }
            .map { "${it.`package`}:${it.id}" }
    }

    private companion object {
        const val TAG = "PinShortcutRequest"
    }
}

/** Process-wide signal telling the search screen to reload shortcuts after a pin request. */
object PinnedShortcutRequests {
    private val refreshNeeded = AtomicBoolean(false)

    fun markRefreshNeeded() = refreshNeeded.set(true)

    fun consumeRefreshNeeded(): Boolean = refreshNeeded.getAndSet(false)
}
