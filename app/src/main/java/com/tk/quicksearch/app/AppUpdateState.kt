package com.tk.quicksearch.app

/** Where a Google Play flexible update stands, as reported by [UpdateHelper]. */
enum class AppUpdateState {
    /** No update worth prompting for. */
    NONE,

    /** An update is available and the user hasn't started it. */
    AVAILABLE,

    /** The user accepted the update and Play is downloading it. */
    DOWNLOADING,

    /** The update is downloaded and waits for the user to restart. */
    READY_TO_INSTALL,
}
