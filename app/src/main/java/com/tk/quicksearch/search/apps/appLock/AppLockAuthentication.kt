package com.tk.quicksearch.search.apps.appLock

import androidx.compose.runtime.staticCompositionLocalOf

/** Requests device biometric authentication and runs the supplied action only after success. */
typealias AppLockAuthenticator = (promptTitle: String, onAuthenticated: () -> Unit) -> Unit

val LocalAppLockAuthenticator = staticCompositionLocalOf<AppLockAuthenticator> { { _, _ -> } }

val LocalAppLockCredentialAuthenticator = staticCompositionLocalOf<AppLockAuthenticator> { { _, _ -> } }
