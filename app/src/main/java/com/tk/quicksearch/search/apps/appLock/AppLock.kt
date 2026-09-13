package com.tk.quicksearch.search.apps.appLock

import android.content.Context

/** Persistent per-package biometric lock state used while Quick Search is the default launcher. */
object AppLock {
    private const val PreferencesName = "app_lock_state"
    private const val LockedPackagesKey = "locked_packages"

    fun isLocked(context: Context, packageName: String): Boolean =
        packageName in lockedPackages(context)

    fun setLocked(context: Context, packageName: String, locked: Boolean) {
        val updatedPackages = lockedPackages(context).toMutableSet().apply {
            if (locked) add(packageName) else remove(packageName)
        }
        preferences(context).edit().putStringSet(LockedPackagesKey, updatedPackages).apply()
    }

    private fun lockedPackages(context: Context): Set<String> =
        preferences(context).getStringSet(LockedPackagesKey, emptySet()).orEmpty()

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}
