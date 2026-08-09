package com.tk.quicksearch.app.startup

import android.os.Trace

/** Lightweight trace markers used to measure user-visible startup readiness milestones. */
object StartupTrace {
    fun mark(name: String) {
        Trace.beginSection(name)
        Trace.endSection()
    }
}
