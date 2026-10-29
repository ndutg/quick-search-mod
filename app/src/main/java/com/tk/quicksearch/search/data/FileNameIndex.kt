package com.tk.quicksearch.search.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.mtp.MtpConstants
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import com.tk.quicksearch.search.utils.SearchTextNormalizer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * MediaStore ids and normalized display names, newest-modified first. File search matches names
 * here instead of running an unindexable GLOB scan over the whole MediaStore table per keystroke,
 * then reads full rows only for the matched ids.
 */
internal class FileNameIndex(
    private val ids: LongArray,
    private val normalizedNames: Array<String>,
    private val isFolder: BooleanArray,
) {
    /**
     * Ids in index order whose name contains every token, or contains the joined tokens once spaces,
     * hyphens, underscores, and dots are ignored. With [extension], only `.<extension>` names match;
     * otherwise only folders and names with a dot, mirroring the provider query this replaces.
     */
    fun matchingIds(
        queryTokens: List<String>,
        extension: String?,
        foldersOnly: Boolean,
        limit: Int,
        cancellationSignal: CancellationSignal?,
    ): List<Long> {
        if (limit <= 0) return emptyList()
        val compactQuery = queryTokens.joinToString("")
        val extensionSuffix = extension?.let { ".${SearchTextNormalizer.normalizeForSearch(it)}" }
        val results = ArrayList<Long>(minOf(limit, 64))
        for (index in ids.indices) {
            if (index and CANCELLATION_CHECK_MASK == 0) cancellationSignal?.throwIfCanceled()
            val name = normalizedNames[index]
            val folder = isFolder[index]
            val matchesType =
                when {
                    foldersOnly -> folder
                    extensionSuffix != null -> name.endsWith(extensionSuffix)
                    else -> folder || name.indexOf('.') >= 0
                }
            if (!matchesType) continue
            if (queryTokens.isNotEmpty() &&
                !queryTokens.all(name::contains) &&
                !containsIgnoringSeparators(name, compactQuery)
            ) {
                continue
            }
            results.add(ids[index])
            if (results.size >= limit) break
        }
        return results
    }

    companion object {
        private const val CANCELLATION_CHECK_MASK = 0x3FF
        private const val MAX_AGE_MILLIS = 10L * 60L * 1000L

        // Past this size the index costs more memory than the provider scan is worth.
        private const val MAX_INDEXED_FILES = 150_000

        private class Cached(
            val index: FileNameIndex?,
            val loadedAtElapsedMillis: Long,
            val changeGeneration: Long,
        )

        private val changeGeneration = AtomicLong()
        private val isObserverRegistered = AtomicBoolean(false)
        private val loadLock = Any()
        @Volatile private var cached: Cached? = null

        /** The current index, or null when the library is too large to index or the read failed. */
        fun get(
            contentResolver: ContentResolver,
            filesUri: Uri,
        ): FileNameIndex? {
            ensureObserverRegistered(contentResolver, filesUri)
            currentOrNull()?.let { return it.index }
            // One load at a time; keystrokes that arrive meanwhile wait for it rather than rescan.
            synchronized(loadLock) {
                currentOrNull()?.let { return it.index }
                val generation = changeGeneration.get()
                val index = runCatching { load(contentResolver, filesUri) }.getOrNull()
                cached = Cached(index, SystemClock.elapsedRealtime(), generation)
                return index
            }
        }

        fun invalidate() {
            changeGeneration.incrementAndGet()
        }

        private fun currentOrNull(): Cached? {
            val snapshot = cached ?: return null
            val age = SystemClock.elapsedRealtime() - snapshot.loadedAtElapsedMillis
            return snapshot.takeIf {
                it.changeGeneration == changeGeneration.get() && age in 0 until MAX_AGE_MILLIS
            }
        }

        private fun load(
            contentResolver: ContentResolver,
            filesUri: Uri,
        ): FileNameIndex? {
            val projection =
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    "format",
                )
            return contentResolver
                .query(
                    filesUri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
                )?.use { cursor ->
                    if (cursor.count > MAX_INDEXED_FILES) return null
                    val ids = LongArray(cursor.count)
                    val names = arrayOfNulls<String>(cursor.count)
                    val folders = BooleanArray(cursor.count)
                    var size = 0
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1) ?: continue
                        ids[size] = cursor.getLong(0)
                        names[size] = SearchTextNormalizer.normalizeForSearch(name)
                        folders[size] = !cursor.isNull(2) && cursor.getInt(2) == MtpConstants.FORMAT_ASSOCIATION
                        size++
                    }
                    @Suppress("UNCHECKED_CAST")
                    FileNameIndex(
                        ids = ids.copyOf(size),
                        normalizedNames = names.copyOf(size) as Array<String>,
                        isFolder = folders.copyOf(size),
                    )
                }
        }

        private fun ensureObserverRegistered(
            contentResolver: ContentResolver,
            filesUri: Uri,
        ) {
            if (!isObserverRegistered.compareAndSet(false, true)) return
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        changeGeneration.incrementAndGet()
                    }
                }
            val registered =
                runCatching { contentResolver.registerContentObserver(filesUri, true, observer) }.isSuccess
            // Without an observer the TTL alone bounds staleness; retry registration next search.
            if (!registered) isObserverRegistered.set(false)
        }

        private fun isFileNameSeparator(char: Char): Boolean =
            char == ' ' || char == '-' || char == '_' || char == '.'

        /** Whether [needle] occurs in [text] once file-name separators in [text] are skipped. */
        internal fun containsIgnoringSeparators(
            text: String,
            needle: String,
        ): Boolean {
            if (needle.isEmpty()) return true
            for (start in text.indices) {
                if (isFileNameSeparator(text[start]) || text[start] != needle[0]) continue
                var textIndex = start
                var needleIndex = 0
                while (textIndex < text.length && needleIndex < needle.length) {
                    val char = text[textIndex++]
                    if (isFileNameSeparator(char)) continue
                    if (char != needle[needleIndex]) break
                    needleIndex++
                }
                if (needleIndex == needle.length) return true
            }
            return false
        }
    }
}
