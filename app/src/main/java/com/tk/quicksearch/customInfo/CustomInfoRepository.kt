package com.tk.quicksearch.customInfo

import android.content.Context
import com.tk.quicksearch.tools.aiSearch.AiSearchLlmProviderId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * A scheduled AI prompt. [dueMillis] is the next run, or null once a one-time item has run.
 * [status] and [answer] describe the latest run; [showOnHome] is cleared when the user dismisses
 * that result from Home and set again by the next run. [enabled] pauses the schedule entirely.
 * [zoneId] is the time zone [dueMillis] and [anchorMillis] were set in; null means the current one.
 */
data class CustomInfoItem(
    val id: Int,
    val title: String,
    val prompt: String,
    val providerId: AiSearchLlmProviderId,
    val modelId: String,
    val webSearch: Boolean,
    val thinking: Boolean,
    val dueMillis: Long?,
    val sendNotification: Boolean,
    val repeat: CustomInfoRepeat? = null,
    val anchorMillis: Long? = dueMillis,
    val enabled: Boolean = true,
    val showOnHome: Boolean = true,
    val status: String = PENDING,
    val answer: String = "",
    val lastRunMillis: Long? = null,
    val zoneId: String? = null,
) {
    /** The next run after a run that finished at [nowMillis]; null ends a one-time item. */
    fun nextDueAfter(nowMillis: Long): Long? {
        val anchor = anchorMillis ?: dueMillis ?: return null
        return repeat?.nextRunAfter(anchor, nowMillis)
    }

    /**
     * The same local times in [zone], so an 8 AM item made in New York still runs at 8 AM after the
     * phone moves to San Francisco. Items without a stored zone are taken to be in [zone] already.
     */
    fun inZone(zone: ZoneId): CustomInfoItem {
        val from = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return copy(zoneId = zone.id)
        if (from == zone) return copy(zoneId = zone.id)
        fun shift(millis: Long?): Long? =
            millis?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), from).atZone(zone).toInstant().toEpochMilli() }
        return copy(dueMillis = shift(dueMillis), anchorMillis = shift(anchorMillis), zoneId = zone.id)
    }

    /**
     * The first run of [newRepeat] after [nowMillis] at the item's original time of day, for a
     * one-time item that already ran and is being made repeating. Null if it has no time to follow.
     */
    fun restartedRunAfter(newRepeat: CustomInfoRepeat, nowMillis: Long): Long? =
        (anchorMillis ?: lastRunMillis)?.let { newRepeat.nextRunAfter(it, nowMillis) }

    /**
     * Applies the editor's [draft], whose time was [editorDueMillis] at its last save. An untouched
     * time keeps the stored schedule, so an edit doesn't move a month-end anchor to a clamped run or
     * undo a run that happened while the editor was open; a new time also becomes the new anchor.
     */
    fun withEdits(draft: CustomInfoItem, editorDueMillis: Long?): CustomInfoItem {
        val timeChanged = draft.dueMillis != editorDueMillis
        return copy(
            title = draft.title,
            prompt = draft.prompt,
            providerId = draft.providerId,
            modelId = draft.modelId,
            webSearch = draft.webSearch,
            thinking = draft.thinking,
            dueMillis = if (timeChanged) draft.dueMillis else dueMillis,
            anchorMillis = if (timeChanged) draft.dueMillis else anchorMillis,
            sendNotification = draft.sendNotification,
            repeat = draft.repeat,
        )
    }

    /** Resumed at [nowMillis]: a repeating item skips missed runs; a one-time item keeps its time. */
    fun resumedAt(nowMillis: Long): CustomInfoItem {
        val due = dueMillis
        return copy(
            enabled = true,
            showOnHome = true,
            dueMillis = if (due != null && due <= nowMillis && repeat != null) nextDueAfter(nowMillis) else due,
        )
    }

    /** Whether a scheduled (not retry) job at [nowMillis] should run, within [toleranceMillis] of due. */
    fun isDueForScheduledRun(nowMillis: Long, toleranceMillis: Long): Boolean {
        val due = dueMillis ?: return false
        return enabled && due <= nowMillis + toleranceMillis
    }

    /** Records a run finished at [nowMillis]; a scheduled run also moves to the next run. */
    fun afterRun(answer: Result<String>, nowMillis: Long, retry: Boolean): CustomInfoItem =
        copy(
            status = if (answer.isSuccess) COMPLETE else ERROR,
            answer = answer.getOrElse { "" },
            showOnHome = true,
            lastRunMillis = nowMillis,
            dueMillis = if (retry) dueMillis else nextDueAfter(nowMillis),
        )

    companion object {
        const val PENDING = "pending"
        const val COMPLETE = "complete"
        const val ERROR = "error"
    }
}

/** Small durable queue and home-card history. API keys stay in the existing encrypted preferences. */
class CustomInfoRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Every item, with times moved to the current time zone (see [CustomInfoItem.inZone]). */
    fun all(): List<CustomInfoItem> = synchronized(lock) {
        val zone = ZoneId.systemDefault()
        val json = runCatching { JSONArray(prefs.getString(KEY_ITEMS, "[]")) }.getOrElse { JSONArray() }
        (0 until json.length()).mapNotNull { index ->
            runCatching {
                val item = json.getJSONObject(index)
                val repeatUnit = CustomInfoRepeatUnit.fromStorageValue(item.optString("repeatUnit"))
                CustomInfoItem(
                    id = item.getInt("id"),
                    title = item.optString("title").ifBlank { item.getString("prompt") },
                    prompt = item.getString("prompt"),
                    providerId = AiSearchLlmProviderId.fromStorageValue(item.getString("provider")),
                    modelId = item.getString("model"),
                    webSearch = item.optBoolean("webSearch"),
                    thinking = item.optBoolean("thinking"),
                    dueMillis = item.optLong("due", 0L).takeIf { it > 0L },
                    sendNotification = item.optBoolean("notify"),
                    repeat = repeatUnit?.let { CustomInfoRepeat(it, item.optInt("repeatInterval", 1).coerceAtLeast(1)) },
                    anchorMillis = item.optLong("anchor", 0L).takeIf { it > 0L },
                    enabled = item.optBoolean("enabled", true),
                    showOnHome = item.optBoolean("showOnHome", true),
                    status = item.optString("status", CustomInfoItem.PENDING),
                    answer = item.optString("answer"),
                    lastRunMillis = item.optLong("lastRun", 0L).takeIf { it > 0L },
                    zoneId = item.optString("zone").ifBlank { null },
                ).inZone(zone)
            }.getOrNull()
        }
    }

    fun get(id: Int): CustomInfoItem? = all().firstOrNull { it.id == id }

    /** Stores [item] under a fresh id and returns the stored copy. */
    fun add(item: CustomInfoItem): CustomInfoItem = synchronized(lock) {
        val items = all()
        val stored = item.copy(id = (items.maxOfOrNull { it.id } ?: 0) + 1)
        write(items + stored)
        stored
    }

    /** Applies [transform] to the item with [id] and returns the result, or null if it's gone. */
    fun update(id: Int, transform: (CustomInfoItem) -> CustomInfoItem): CustomInfoItem? = synchronized(lock) {
        var updated: CustomInfoItem? = null
        write(all().map { if (it.id == id) transform(it).also { result -> updated = result } else it })
        updated
    }

    fun delete(id: Int) = synchronized(lock) { write(all().filterNot { it.id == id }) }

    private fun write(items: List<CustomInfoItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("prompt", item.prompt)
                    put("provider", item.providerId.storageValue)
                    put("model", item.modelId)
                    put("webSearch", item.webSearch)
                    put("thinking", item.thinking)
                    put("due", item.dueMillis ?: 0L)
                    put("notify", item.sendNotification)
                    item.repeat?.let {
                        put("repeatUnit", it.unit.storageValue)
                        put("repeatInterval", it.interval)
                    }
                    put("anchor", item.anchorMillis ?: 0L)
                    put("enabled", item.enabled)
                    put("showOnHome", item.showOnHome)
                    put("status", item.status)
                    put("answer", item.answer)
                    put("lastRun", item.lastRunMillis ?: 0L)
                    put("zone", item.zoneId ?: ZoneId.systemDefault().id)
                },
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        notifyChanged()
    }

    companion object {
        const val PREFS_NAME = "custom_info"
        private const val KEY_ITEMS = "items"
        private val lock = Any()
        val changes = MutableStateFlow(0)

        /** Tells open screens to re-read, for writes that bypass this class (backup restore). */
        fun notifyChanged() {
            changes.update { it + 1 }
        }
    }
}
