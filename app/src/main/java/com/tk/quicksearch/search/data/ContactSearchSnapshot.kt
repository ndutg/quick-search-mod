package com.tk.quicksearch.search.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import com.tk.quicksearch.search.models.ContactInfo
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** A contact with a phone number plus the lowercased names used for in-memory matching. */
internal class ContactSearchEntry(
    val contact: ContactInfo,
    primaryName: String,
    alternativeName: String?,
) {
    val lowerPrimaryName = primaryName.lowercase(Locale.getDefault())
    val lowerAlternativeName = alternativeName?.lowercase(Locale.getDefault())
    val compactPrimaryName = lowerPrimaryName.replace(" ", "")
    val compactAlternativeName = lowerAlternativeName?.replace(" ", "")
}

/**
 * Process-wide snapshot of every contact with a phone number, sorted by display name. Typing
 * reuses it for name-token matching and fuzzy candidates instead of re-reading the provider on
 * each keystroke. A ContactsContract observer and a TTL invalidate it.
 */
internal object ContactSearchSnapshot {
    private const val MAX_AGE_MILLIS = 10L * 60L * 1000L

    private class Cached(
        val entries: List<ContactSearchEntry>,
        val loadedAtElapsedMillis: Long,
        val changeGeneration: Long,
    )

    private val changeGeneration = AtomicLong()
    private val isObserverRegistered = AtomicBoolean(false)
    @Volatile private var cached: Cached? = null

    fun get(
        contentResolver: ContentResolver,
        load: () -> List<ContactSearchEntry>,
    ): List<ContactSearchEntry> {
        ensureObserverRegistered(contentResolver)
        val nowElapsed = SystemClock.elapsedRealtime()
        val generation = changeGeneration.get()
        cached?.let { snapshot ->
            if (snapshot.changeGeneration == generation &&
                nowElapsed - snapshot.loadedAtElapsedMillis in 0 until MAX_AGE_MILLIS
            ) {
                return snapshot.entries
            }
        }
        val entries = load()
        cached = Cached(entries, nowElapsed, generation)
        return entries
    }

    /** Every whitespace token appears in one of the names, or the space-free query appears in one. */
    fun findByNameTokens(
        entries: List<ContactSearchEntry>,
        query: String,
        limit: Int,
        excludedContactIds: Set<Long>,
    ): List<ContactInfo> {
        if (limit <= 0) return emptyList()
        val lowerQuery = query.lowercase(Locale.getDefault())
        val tokens = lowerQuery.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val compactQuery = lowerQuery.replace(" ", "")
        val results = ArrayList<ContactInfo>(minOf(limit, 16))
        for (entry in entries) {
            if (entry.contact.contactId in excludedContactIds) continue
            val matchesTokens =
                tokens.all { token ->
                    entry.lowerPrimaryName.contains(token) ||
                        entry.lowerAlternativeName?.contains(token) == true
                }
            val matchesCompact =
                entry.compactPrimaryName.contains(compactQuery) ||
                    entry.compactAlternativeName?.contains(compactQuery) == true
            if (matchesTokens || matchesCompact) {
                results.add(entry.contact)
                if (results.size >= limit) break
            }
        }
        return results
    }

    private fun ensureObserverRegistered(contentResolver: ContentResolver) {
        if (!isObserverRegistered.compareAndSet(false, true)) return
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    changeGeneration.incrementAndGet()
                }
            }
        val registered =
            runCatching {
                contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
            }.isSuccess
        // Without an observer the TTL alone bounds staleness; retry registration next search.
        if (!registered) isObserverRegistered.set(false)
    }
}
