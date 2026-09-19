package com.tk.quicksearch.search.searchScreen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityEvent
import com.tk.quicksearch.edgeGesture.EdgeGestureOverlay

/** Hosts the Home double-tap lock action and the system-wide edge swipe handle. */
class LockScreenAccessibilityService : AccessibilityService() {
    private var edgeGestureOverlay: EdgeGestureOverlay? = null

    override fun onServiceConnected() {
        instance = this
        edgeGestureOverlay =
            EdgeGestureOverlay(this).also { overlay ->
                overlay.setPreviewVisible(edgeGesturePreviewVisible)
                overlay.attach()
            }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        edgeGestureOverlay?.refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        edgeGestureOverlay?.detach()
        edgeGestureOverlay = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun lockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    companion object {
        @Volatile
        private var instance: LockScreenAccessibilityService? = null

        @Volatile
        private var edgeGesturePreviewVisible = false

        fun lockScreen() {
            instance?.lockScreen()
        }

        /** Must be called on the main thread. */
        fun setEdgeGesturePreviewVisible(visible: Boolean) {
            edgeGesturePreviewVisible = visible
            instance?.edgeGestureOverlay?.setPreviewVisible(visible)
        }

        fun isEnabled(context: Context): Boolean {
            val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
                ?: return false
            return accessibilityManager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { service ->
                    service.resolveInfo.serviceInfo.packageName == context.packageName &&
                        service.resolveInfo.serviceInfo.name == LockScreenAccessibilityService::class.java.name
                }
        }
    }
}
