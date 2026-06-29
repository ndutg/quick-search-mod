package com.tk.quicksearch.shared.util

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Returns true when this app is currently set as the device's default Home app (launcher). */
fun android.content.Context.isDefaultHomeApp(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
            return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        }
    }

    val resolveInfo =
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        ) ?: return false

    val resolvedPackage = resolveInfo.activityInfo?.packageName ?: return false
    if (resolvedPackage == "android") return false
    return resolvedPackage == packageName
}
