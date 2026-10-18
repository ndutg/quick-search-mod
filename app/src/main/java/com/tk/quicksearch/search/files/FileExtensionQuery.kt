package com.tk.quicksearch.search.files

import com.tk.quicksearch.search.utils.SearchQueryContext
import java.util.Locale

/**
 * A files-only query that starts with an extension, e.g. ".mkv" (every mkv file) or
 * ".mkv holiday" (mkv files matching "holiday").
 */
data class FileExtensionQuery(
    val extension: String,
    val nameQuery: String,
) {
    val nameQueryContext: SearchQueryContext by lazy { SearchQueryContext.fromRawQuery(nameQuery) }

    fun matchesFileName(
        displayName: String,
        isDirectory: Boolean,
    ): Boolean =
        !isDirectory &&
            displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT) == extension

    companion object {
        private const val MAX_EXTENSION_LENGTH = 10

        // Letters are required so decimals like ".5" stay with the calculator.
        private val PATTERN = Regex("^\\.([a-zA-Z0-9]*[a-zA-Z][a-zA-Z0-9]*)(?:\\s+(.*))?$")

        fun parse(query: String): FileExtensionQuery? {
            val match = PATTERN.matchEntire(query.trim()) ?: return null
            val extension = match.groupValues[1]
            if (extension.length > MAX_EXTENSION_LENGTH) return null
            return FileExtensionQuery(
                extension = extension.lowercase(Locale.ROOT),
                nameQuery = match.groupValues[2].trim(),
            )
        }

        /**
         * Whether a query that returned no files proves [query] can't return any either. Extension
         * queries only qualify when the extension is identical, since ".mk" having no results says
         * nothing about ".mkv".
         */
        fun isNoResultPrefixReusable(
            noResultQuery: String,
            query: String,
        ): Boolean {
            if (!query.startsWith(noResultQuery)) return false
            val current = parse(query) ?: return parse(noResultQuery) == null
            val previous = parse(noResultQuery) ?: return false
            return previous.extension == current.extension
        }
    }
}
