package com.tk.quicksearch.edgeGesture

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.tk.quicksearch.app.MainActivity
import com.tk.quicksearch.search.data.preferences.EdgeGestureConfig
import com.tk.quicksearch.search.data.preferences.EdgeGesturePreferences
import com.tk.quicksearch.search.data.preferences.EdgeGestureSide
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Invisible edge handle drawn as an accessibility overlay. Swiping it, or holding it briefly,
 * opens Quick Search from any app. It never reads screen content.
 */
class EdgeGestureOverlay(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val preferences = EdgeGesturePreferences(service)
    private var handleView: HandleView? = null
    private var previewVisible = false

    // Held as a field: SharedPreferences keeps listeners only weakly.
    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (EdgeGesturePreferences.isEdgeGestureKey(key)) refresh()
        }

    fun attach() {
        preferences.registerListener(preferenceListener)
        refresh()
    }

    fun detach() {
        preferences.unregisterListener(preferenceListener)
        removeHandle()
    }

    /** Highlights the handle while the user customizes it; swipes then only give feedback. */
    fun setPreviewVisible(visible: Boolean) {
        if (previewVisible == visible) return
        previewVisible = visible
        refresh()
    }

    fun refresh() {
        val config = preferences.getConfig()
        if (!config.enabled) {
            removeHandle()
            return
        }
        val params = layoutParams(config)
        val view = handleView
        if (view == null) {
            val newView = HandleView(service)
            newView.background = handleBackground(config)
            runCatching { windowManager.addView(newView, params) }
                .onSuccess {
                    handleView = newView
                    Log.d(
                        TAG,
                        "handle added: ${params.width}x${params.height} at x=${params.x} y=${params.y}",
                    )
                }
                .onFailure { error -> Log.w(TAG, "handle not added", error) }
        } else {
            view.background = handleBackground(config)
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    private fun removeHandle() {
        handleView?.let { view ->
            // A hold in flight must not launch after the handle is gone.
            view.cancelPendingTrigger()
            runCatching { windowManager.removeView(view) }
        }
        handleView = null
    }

    private fun layoutParams(config: EdgeGestureConfig): WindowManager.LayoutParams {
        val screenHeight = screenHeightPx()
        val height = (screenHeight * config.size).roundToInt().coerceIn(1, screenHeight)
        val centerY = (screenHeight * config.position).roundToInt()
        val top = (centerY - height / 2).coerceIn(0, screenHeight - height)
        return WindowManager.LayoutParams(
            dpToPx(config.widthDp),
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity =
                Gravity.TOP or
                    if (config.side == EdgeGestureSide.LEFT) Gravity.LEFT else Gravity.RIGHT
            x = dpToPx(config.offsetDp)
            y = top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun handleBackground(config: EdgeGestureConfig): GradientDrawable? {
        // The customization screen always shows the handle, however faint the user made it.
        val opacity = if (previewVisible) maxOf(config.opacity, PREVIEW_MIN_OPACITY) else config.opacity
        if (opacity <= 0f) return null
        // Radii wider than the handle itself make the shape collapse, so cap them.
        val radius = minOf(dpToPx(HANDLE_CORNER_RADIUS_DP), dpToPx(config.widthDp) / 2).toFloat()
        // Flush against the edge only the inner side is rounded; once it is inset it is a pill.
        val radii =
            when {
                config.offsetDp > 0 -> FloatArray(8) { radius }
                config.side == EdgeGestureSide.LEFT ->
                    floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                else -> floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
            }
        return GradientDrawable().apply {
            setColor(handleColor(opacity))
            cornerRadii = radii
        }
    }

    private fun handleColor(opacity: Float): Int {
        val accent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                service.getColor(android.R.color.system_accent1_400)
            } else {
                Color.rgb(0x42, 0x85, 0xF4)
            }
        val alpha = (opacity * 255).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))
    }

    private fun screenHeightPx(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            service.resources.displayMetrics.heightPixels
        }

    private fun dpToPx(dp: Int): Int =
        TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), service.resources.displayMetrics)
            .roundToInt()

    private fun onTrigger(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (previewVisible) {
            Log.d(TAG, "trigger ignored: customization preview")
            return
        }
        val keyguardManager = service.getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isKeyguardLocked == true) {
            Log.d(TAG, "trigger ignored: keyguard locked")
            return
        }
        runCatching { service.startActivity(launchIntent(service)) }
            .onSuccess { Log.d(TAG, "launch requested") }
            .onFailure { error -> Log.w(TAG, "launch failed", error) }
    }

    @SuppressLint("ViewConstructor")
    private inner class HandleView(
        context: Context,
    ) : View(context) {
        private val triggerDistancePx = dpToPx(TRIGGER_DISTANCE_DP).toFloat()
        private var downX = 0f
        private var downY = 0f
        private var triggered = false

        // Either a swipe in any direction or a press held in place. Inset from the screen edge the
        // handle gets the whole pointer stream, so a swipe is safe here; at the edge the system
        // back gesture takes it first, which is what the offset setting exists to avoid.
        private val holdRunnable = Runnable { trigger() }

        fun cancelPendingTrigger() = removeCallbacks(holdRunnable)

        private fun trigger() {
            if (triggered) return
            triggered = true
            cancelPendingTrigger()
            onTrigger(this)
        }

        override fun onSizeChanged(
            width: Int,
            height: Int,
            oldWidth: Int,
            oldHeight: Int,
        ) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            // Honoured on AOSP, ignored by some OEMs; it costs nothing and keeps the back gesture
            // off the handle where it does work. The framework caps how much of a side edge an
            // ordinary window may exclude, so a tall handle only keeps part of its length.
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    triggered = false
                    postDelayed(holdRunnable, TRIGGER_HOLD_MS)
                }

                MotionEvent.ACTION_MOVE ->
                    if (!triggered &&
                        (abs(event.rawX - downX) > triggerDistancePx ||
                            abs(event.rawY - downY) > triggerDistancePx)
                    ) {
                        trigger()
                    }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelPendingTrigger()
            }
            return true
        }
    }

    companion object {
        private const val TAG = "EdgeGesture"
        private const val TRIGGER_HOLD_MS = 350L
        private const val TRIGGER_DISTANCE_DP = 16
        private const val PREVIEW_MIN_OPACITY = 0.7f
        private const val HANDLE_CORNER_RADIUS_DP = 12

        fun launchIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
            }
    }
}
