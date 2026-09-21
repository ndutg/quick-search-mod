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
import android.view.ViewConfiguration
import android.view.WindowManager
import com.tk.quicksearch.app.MainActivity
import com.tk.quicksearch.search.data.preferences.EdgeGestureActivation
import com.tk.quicksearch.search.data.preferences.EdgeGestureConfig
import com.tk.quicksearch.search.data.preferences.EdgeGesturePreferences
import com.tk.quicksearch.search.data.preferences.EdgeGestureSide
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Invisible edge handle drawn as an accessibility overlay. Tapping or sliding it, according to
 * the user's selection, opens Quick Search from any app. It never reads screen content.
 */
class EdgeGestureOverlay(
    private val service: AccessibilityService,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val preferences = EdgeGesturePreferences(service)
    private val handleViews = mutableMapOf<HandleLocation, HandleView>()
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

    /** Prevents the customization preview from launching Quick Search. */
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
        val desiredLocations = handleLocations(config.side)
        (handleViews.keys - desiredLocations.toSet()).forEach(::removeHandle)
        desiredLocations.forEach { location ->
            val params = layoutParams(config, location)
            val view = handleViews[location]
            if (view == null) {
                val newView = HandleView(service).apply { activation = config.activation }
                newView.background = handleBackground(config, location)
                runCatching { windowManager.addView(newView, params) }
                    .onSuccess {
                        handleViews[location] = newView
                        // Request the exclusion again after WindowManager has attached and measured
                        // the view, matching the lifecycle used by established edge-overlay apps.
                        newView.post { newView.updateGestureExclusion() }
                        Log.d(
                            TAG,
                            "$location handle added: ${params.width}x${params.height} " +
                                "at x=${params.x} y=${params.y}",
                        )
                    }.onFailure { error -> Log.w(TAG, "$location handle not added", error) }
            } else {
                view.activation = config.activation
                view.background = handleBackground(config, location)
                runCatching { windowManager.updateViewLayout(view, params) }
                    .onSuccess { view.post { view.updateGestureExclusion() } }
            }
        }
    }

    private fun removeHandle(location: HandleLocation) {
        val view = handleViews.remove(location) ?: return
        runCatching { windowManager.removeView(view) }
    }

    private fun removeHandle() {
        handleViews.keys.toList().forEach(::removeHandle)
    }

    private fun handleLocations(side: EdgeGestureSide): List<HandleLocation> =
        when (side) {
            EdgeGestureSide.LEFT -> listOf(HandleLocation.LEFT_EDGE)
            EdgeGestureSide.RIGHT -> listOf(HandleLocation.RIGHT_EDGE)
        }

    private fun layoutParams(
        config: EdgeGestureConfig,
        location: HandleLocation,
    ): WindowManager.LayoutParams {
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
                when (location) {
                    HandleLocation.LEFT_EDGE -> Gravity.TOP or Gravity.LEFT
                    HandleLocation.RIGHT_EDGE -> Gravity.TOP or Gravity.RIGHT
                }
            x = 0
            y = top
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // This overlay deliberately owns its exact edge bounds instead of being inset by
                // system bars. Its requested gesture exclusion still remains subject to Android's
                // per-edge limit.
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun handleBackground(
        config: EdgeGestureConfig,
        location: HandleLocation,
    ): GradientDrawable? {
        // Use the configured opacity even on the customization screen so the slider previews live.
        val opacity = config.opacity
        if (opacity <= 0f) return null
        // Radii wider than the handle itself make the shape collapse, so cap them.
        val radius = minOf(dpToPx(HANDLE_CORNER_RADIUS_DP), dpToPx(config.widthDp) / 2).toFloat()
        val radii =
            when (location) {
                HandleLocation.LEFT_EDGE ->
                    floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
                HandleLocation.RIGHT_EDGE ->
                    floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
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
        private val tapSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        private var downX = 0f
        private var downY = 0f
        private var triggered = false
        var activation = EdgeGestureActivation.SLIDE

        fun updateGestureExclusion() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || width <= 0 || height <= 0) return
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }

        private fun trigger() {
            if (triggered) return
            triggered = true
            onTrigger(this)
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.onLayout(changed, left, top, right, bottom)
            // Give this narrow edge control priority over Back. Android limits the total honored
            // vertical extent of gesture exclusions on each edge to 200 dp.
            updateGestureExclusion()
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    triggered = false
                }

                MotionEvent.ACTION_MOVE ->
                    if (activation == EdgeGestureActivation.SLIDE &&
                        !triggered &&
                        (abs(event.rawX - downX) > triggerDistancePx ||
                            abs(event.rawY - downY) > triggerDistancePx)
                    ) {
                        trigger()
                    }

                MotionEvent.ACTION_UP -> {
                    if (activation == EdgeGestureActivation.TAP &&
                        !triggered &&
                        abs(event.rawX - downX) <= tapSlopPx &&
                        abs(event.rawY - downY) <= tapSlopPx
                    ) {
                        trigger()
                    }
                }

                MotionEvent.ACTION_CANCEL -> Unit
            }
            return true
        }
    }

    companion object {
        private const val TAG = "EdgeGesture"
        private const val TRIGGER_DISTANCE_DP = 16
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

    private enum class HandleLocation {
        LEFT_EDGE,
        RIGHT_EDGE,
    }
}
