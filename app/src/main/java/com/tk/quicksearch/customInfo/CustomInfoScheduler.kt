package com.tk.quicksearch.customInfo

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tk.quicksearch.R
import com.tk.quicksearch.reminders.ReminderPermissions
import com.tk.quicksearch.search.data.userAppPreferences.UserAppPreferences
import com.tk.quicksearch.tools.aiSearch.AiSearchLlmProviderRegistry
import com.tk.quicksearch.tools.aiSearch.LlmRequest
import com.tk.quicksearch.tools.aiSearch.isThinkingRequestSupported
import com.tk.quicksearch.tools.aiSearch.modelSupportsGrounding
import com.tk.quicksearch.tools.aiSearch.prepareWebSearch
import com.tk.quicksearch.tools.aiSearch.providerSupportsNativeSearch
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Shared by scheduled delivery and the editor's preview. Call from a background dispatcher. */
suspend fun fetchCustomInfoAnswer(context: Context, item: CustomInfoItem): Result<String> {
    val preferences = UserAppPreferences(context)
    val provider = AiSearchLlmProviderRegistry.get(item.providerId, context)
    val key = preferences.getLlmApiKey(item.providerId)
    if (key.isNullOrBlank()) {
        return Result.failure(IllegalStateException(context.getString(R.string.direct_search_error_no_key)))
    }
    val models = provider.fallbackTextModels
    val web = prepareWebSearch(
        userPreferences = preferences,
        searchQuery = item.prompt,
        prompt = item.prompt,
        nativeSearchSupported = providerSupportsNativeSearch(item.providerId) &&
            modelSupportsGrounding(item.modelId, models, item.providerId),
        nativeSearchRequested = item.webSearch,
    )
    val customPayload = preferences.getCustomLlmAdvancedPayloadByProvider()[item.providerId]
    return provider.fetchAnswer(
        apiKey = key,
        context = context,
        request = LlmRequest(
            query = web.prompt,
            modelId = item.modelId,
            useGroundingWithGoogleSearch = web.useNativeSearch,
            thinkingEnabled = item.thinking && isThinkingRequestSupported(item.providerId),
            useSystemInstruction = models.firstOrNull { it.id == item.modelId }?.supportsSystemInstructions ?: true,
            systemInstruction = "Answer the user's request briefly. Keep the entire answer to at most 5–6 short lines.",
            advancedPayloadJson = customPayload?.second?.takeIf { customPayload.first },
        ),
    ).map { it.text }
}

/** An alarm starts a network job; JobScheduler keeps the request alive if the network is unavailable. */
object CustomInfoScheduler {
    private const val ALARM_ACTION = "com.tk.quicksearch.CUSTOM_INFO_DUE"
    internal const val EXTRA_ID = "id"
    internal const val EXTRA_RETRY = "retry"
    private const val JOB_BASE = 4_000_000
    private const val RETRY_JOB_BASE = 5_000_000
    private const val NOTIFICATION_CHANNEL_ID = "custom_info"

    /** Arms the alarm for [item]'s next run, or queues the run now if it's already due. */
    fun schedule(context: Context, item: CustomInfoItem) {
        val due = item.dueMillis
        if (!item.enabled || due == null) return
        if (due <= System.currentTimeMillis()) {
            enqueue(context, item.id, retry = false)
            return
        }
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = alarmIntent(context, item.id)
        if (ReminderPermissions.canScheduleExactAlarms(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, intent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, intent)
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context, id))
        context.getSystemService(JobScheduler::class.java)?.let { jobs ->
            jobs.cancel(JOB_BASE + id)
            jobs.cancel(RETRY_JOB_BASE + id)
        }
        cancelNotification(context, id)
    }

    fun cancelNotification(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(JOB_BASE + id)
    }

    /**
     * Re-arms every enabled item. Alarms and jobs don't survive reboot or restore, and a clock or
     * time zone change moves the local time they should fire at.
     */
    fun rescheduleAll(context: Context) {
        CustomInfoRepository(context).all().forEach { schedule(context, it) }
    }

    /**
     * Pauses or resumes [id]. A resumed repeating item skips the runs it missed while paused; a
     * resumed one-time item that never ran runs now.
     */
    fun setEnabled(context: Context, id: Int, enabled: Boolean) {
        if (!enabled) {
            cancel(context, id)
            CustomInfoRepository(context).update(id) { it.copy(enabled = false) }
            return
        }
        val now = System.currentTimeMillis()
        val resumed =
            CustomInfoRepository(context).update(id) { it.resumedAt(now) } ?: return
        schedule(context, resumed)
    }

    /** Runs [id] again now, without moving its schedule. */
    fun retry(context: Context, id: Int) = enqueue(context, id, retry = true)

    private fun enqueue(context: Context, id: Int, retry: Boolean) {
        val job = JobInfo.Builder(
            (if (retry) RETRY_JOB_BASE else JOB_BASE) + id,
            ComponentName(context, CustomInfoJobService::class.java),
        )
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setMinimumLatency(0L)
            .setExtras(
                PersistableBundle().apply {
                    putInt(EXTRA_ID, id)
                    putInt(EXTRA_RETRY, if (retry) 1 else 0)
                },
            )
            .build()
        context.getSystemService(JobScheduler::class.java)?.schedule(job)
    }

    private fun alarmIntent(context: Context, id: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            JOB_BASE + id,
            Intent(context, CustomInfoAlarmReceiver::class.java).setAction(ALARM_ACTION).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun onAlarm(context: Context, intent: Intent) {
        if (intent.action != ALARM_ACTION) return
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val item = CustomInfoRepository(context).get(id) ?: return
        if (item.enabled && item.dueMillis != null) enqueue(context, id, retry = false)
    }

    /** Runs [id] and stores the result. A scheduled run also moves the item to its next run. */
    internal suspend fun run(context: Context, id: Int, retry: Boolean) {
        val repository = CustomInfoRepository(context)
        val item = repository.get(id) ?: return
        if (!item.enabled) return
        // A scheduled job whose run was already handled, or moved later by an edit or a time zone
        // change, has nothing to do now; re-arm in case the alarm was set for the old time.
        if (!retry && !item.isDueForScheduledRun(System.currentTimeMillis(), DUE_TOLERANCE_MILLIS)) {
            schedule(context, item)
            return
        }
        val result =
            try {
                fetchCustomInfoAnswer(context, item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        val now = System.currentTimeMillis()
        val updated =
            repository.update(id) { it.afterRun(result, now, retry) } ?: return
        if (!retry) schedule(context, updated)
        if (result.isSuccess) notifyResult(context, updated)
    }

    private fun notifyResult(context: Context, item: CustomInfoItem) {
        if (!item.sendNotification || !ReminderPermissions.hasPostNotifications(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.custom_info_title),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        // Opening the app is enough: the answer is already on Home in At a Glance.
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(context, JOB_BASE + item.id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(item.title)
            .setContentText(item.answer)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.answer))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(JOB_BASE + item.id, notification) }
    }

    private const val DUE_TOLERANCE_MILLIS = 60_000L
}

class CustomInfoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = CustomInfoScheduler.onAlarm(context, intent)
}

class CustomInfoJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val id = params.extras.getInt(CustomInfoScheduler.EXTRA_ID, -1)
        if (id <= 0) return false
        val retry = params.extras.getInt(CustomInfoScheduler.EXTRA_RETRY, 0) == 1
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                CustomInfoScheduler.run(applicationContext, id, retry)
                jobFinished(params, false)
            } finally {
                jobs.remove(params.jobId)
            }
        }
        jobs[params.jobId] = job
        job.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobs.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
