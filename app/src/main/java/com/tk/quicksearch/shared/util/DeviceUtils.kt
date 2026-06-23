package com.tk.quicksearch.shared.util

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Utility functions for device detection and configuration.
 */

/**
 * Checks if the current device is a tablet based on screen size.
 * Tablets are typically devices with smallest screen width >= 600dp.
 */
@Composable
fun isTablet(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.smallestScreenWidthDp >= 600
}

/**
 * Checks if the device is in landscape orientation.
 */
@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@Composable
fun rememberPhysicalKeyboardConnected(): Boolean {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var inputDeviceChangeVersion by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        val listener =
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    inputDeviceChangeVersion++
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    inputDeviceChangeVersion++
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    inputDeviceChangeVersion++
                }
            }

        inputManager?.registerInputDeviceListener(listener, null)
        onDispose {
            inputManager?.unregisterInputDeviceListener(listener)
        }
    }

    return remember(configuration, inputDeviceChangeVersion) {
        context.isPhysicalKeyboardConnected()
    }
}

/**
 * Gets the appropriate number of columns for app grid based on device type and orientation.
 * Tablets show 7 columns in portrait, 9 columns in landscape. Phones use [phoneColumnOverride].
 */
@Composable
fun getAppGridColumns(phoneColumnOverride: Int = 5): Int =
    if (isTablet()) {
        if (isLandscape()) 9 else 7
    } else {
        phoneColumnOverride
    }

/**
 * Checks if the device is a tablet using Context.
 * Useful when not in a Composable context.
 */
fun isTablet(context: Context): Boolean {
    val configuration = context.resources.configuration
    return configuration.smallestScreenWidthDp >= 600
}

/**
 * Checks if the device is in landscape orientation using Context.
 * Useful when not in a Composable context.
 */
fun isLandscape(context: Context): Boolean {
    val configuration = context.resources.configuration
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

fun Context.isPhysicalKeyboardConnected(): Boolean =
    resources.configuration.hasAvailablePhysicalKeyboard() || hasAlphabeticPhysicalKeyboardInputDevice()

private fun Configuration.hasAvailablePhysicalKeyboard(): Boolean =
    keyboard != Configuration.KEYBOARD_NOKEYS &&
        hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO

private fun hasAlphabeticPhysicalKeyboardInputDevice(): Boolean =
    InputDevice.getDeviceIds().any { deviceId ->
        val device = InputDevice.getDevice(deviceId) ?: return@any false
        !device.isVirtual &&
            device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
            (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
    }

/**
 * Gets the appropriate number of columns for app grid based on device type and orientation using Context.
 * Tablets show 7 columns in portrait, 9 columns in landscape. Phones use [phoneColumnOverride].
 */
fun getAppGridColumns(context: Context, phoneColumnOverride: Int = 5): Int =
    if (isTablet(context)) {
        if (isLandscape(context)) 9 else 7
    } else {
        phoneColumnOverride
    }

/**
 * Checks whether the device is classified by Android as low-RAM.
 * Useful for reducing expensive search workloads on constrained devices.
 */
fun isLowRamDevice(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager?.isLowRamDevice == true
}
