package com.tk.quicksearch.shared.util

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.io.Writer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object MemoryDiagnostics {
    private const val LOG_DIR = "crash_logs"
    private const val CURRENT_FILE = "memory_diagnostics.txt"
    private const val PREVIOUS_FILE = "memory_diagnostics.previous.txt"
    private const val MAX_FILE_SIZE_BYTES = 96 * 1024L
    private const val INITIAL_SAMPLE_DELAY_SECONDS = 10L
    private const val SAMPLE_INTERVAL_SECONDS = 60L

    private val installed = AtomicBoolean(false)
    private val activityCreatedCount = AtomicInteger(0)
    private val activityDestroyedCount = AtomicInteger(0)
    private val activeActivityCount = AtomicInteger(0)
    private val resumedActivityCount = AtomicInteger(0)
    private val widgetHostCount = AtomicInteger(0)
    private val widgetViewCount = AtomicInteger(0)
    private val writer =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "memory-diagnostics").apply { isDaemon = true }
        }

    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val application = context.applicationContext as? Application ?: return
        appContext = application
        application.registerActivityLifecycleCallbacks(ActivityCallbacks)
        application.registerComponentCallbacks(MemoryCallbacks)
        recordAsync("diagnostics-installed")
        writer.scheduleAtFixedRate(
            { recordSnapshot("periodic") },
            INITIAL_SAMPLE_DELAY_SECONDS,
            SAMPLE_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    fun widgetHostCreated() {
        widgetHostCount.incrementAndGet()
        recordAsync("widget-host-created")
    }

    fun widgetHostReleased() {
        widgetHostCount.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
        recordAsync("widget-host-released")
    }

    fun widgetViewCreated() {
        widgetViewCount.incrementAndGet()
    }

    fun widgetViewsReleased(count: Int) {
        if (count <= 0) return
        widgetViewCount.updateAndGet { value -> (value - count).coerceAtLeast(0) }
    }

    fun appendTo(output: Writer, context: Context) {
        val dir = File(context.filesDir, LOG_DIR)
        val previous = File(dir, PREVIOUS_FILE)
        val current = File(dir, CURRENT_FILE)
        if (!previous.exists() && !current.exists()) return

        output.append("\n=== Pre-crash Memory Diagnostics ===\n")
        if (previous.exists()) previous.bufferedReader().use { it.copyTo(output) }
        if (current.exists()) current.bufferedReader().use { it.copyTo(output) }
    }

    private fun recordAsync(reason: String) {
        writer.execute { recordSnapshot(reason) }
    }

    private fun recordSnapshot(reason: String) {
        val context = appContext ?: return
        runCatching {
            val runtime = Runtime.getRuntime()
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val processMemory =
                activityManager
                    ?.getProcessMemoryInfo(intArrayOf(Process.myPid()))
                    ?.firstOrNull()
            val line =
                buildString(256) {
                    append(System.currentTimeMillis())
                    append(" uptimeMs=").append(SystemClock.elapsedRealtime())
                    append(" reason=").append(reason)
                    append(" javaUsedKb=").append((runtime.totalMemory() - runtime.freeMemory()) / 1024L)
                    append(" javaTotalKb=").append(runtime.totalMemory() / 1024L)
                    append(" javaMaxKb=").append(runtime.maxMemory() / 1024L)
                    append(" nativeKb=").append(Debug.getNativeHeapAllocatedSize() / 1024L)
                    append(" totalPssKb=").append(processMemory?.totalPss ?: -1)
                    append(" privateDirtyKb=").append(processMemory?.totalPrivateDirty ?: -1)
                    append(" memoryClassMb=").append(activityManager?.memoryClass ?: -1)
                    append(" largeMemoryClassMb=").append(activityManager?.largeMemoryClass ?: -1)
                    append(" activities=").append(activeActivityCount.get())
                    append(" resumed=").append(resumedActivityCount.get())
                    append(" created=").append(activityCreatedCount.get())
                    append(" destroyed=").append(activityDestroyedCount.get())
                    append(" widgetHosts=").append(widgetHostCount.get())
                    append(" widgetViews=").append(widgetViewCount.get())
                    append('\n')
                }
            appendBounded(context, line)
        }
    }

    @Synchronized
    private fun appendBounded(context: Context, line: String) {
        val dir = File(context.filesDir, LOG_DIR).also { it.mkdirs() }
        val current = File(dir, CURRENT_FILE)
        if (current.length() + line.length > MAX_FILE_SIZE_BYTES) {
            val previous = File(dir, PREVIOUS_FILE)
            previous.delete()
            current.renameTo(previous)
        }
        current.appendText(line)
    }

    private object ActivityCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activityCreatedCount.incrementAndGet()
            activeActivityCount.incrementAndGet()
            recordAsync("activity-created:${activity.javaClass.simpleName}")
        }

        override fun onActivityDestroyed(activity: Activity) {
            activityDestroyedCount.incrementAndGet()
            activeActivityCount.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
            recordAsync("activity-destroyed:${activity.javaClass.simpleName}")
        }

        override fun onActivityResumed(activity: Activity) {
            resumedActivityCount.incrementAndGet()
        }

        override fun onActivityPaused(activity: Activity) {
            resumedActivityCount.updateAndGet { value -> (value - 1).coerceAtLeast(0) }
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    @Suppress("DEPRECATION")
    private object MemoryCallbacks : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            recordAsync("trim-memory:$level")
        }

        override fun onLowMemory() {
            recordAsync("low-memory")
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit
    }
}
