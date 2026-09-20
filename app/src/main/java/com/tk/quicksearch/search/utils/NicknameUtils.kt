package com.tk.quicksearch.search.utils

/**
 * Nicknames are stored as a single comma-separated string so that one item can be reached by
 * several aliases ("tv, remote"). Storage stays a plain string for backward compatibility; the
 * split happens on read.
 */
object NicknameUtils {
    private const val SEPARATOR = ','
    private const val DISPLAY_SEPARATOR = ", "

    /** True when the stored value holds more than one alias and therefore needs splitting. */
    fun hasMultiple(nickname: String): Boolean = nickname.indexOf(SEPARATOR) >= 0

    /** Splits a stored nickname into its individual aliases, dropping blanks and duplicates. */
    fun split(nickname: String?): List<String> {
        if (nickname.isNullOrBlank()) return emptyList()
        if (!hasMultiple(nickname)) {
            return listOf(nickname.trim()).filter { it.isNotBlank() }
        }
        return nickname
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Normalizes user input for storage: trims each alias, drops blanks and duplicates, and
     * rejoins with ", ". Returns null when nothing usable remains (e.g. input was just commas).
     */
    fun normalizeInput(input: String?): String? =
        split(input).takeIf { it.isNotEmpty() }?.joinToString(DISPLAY_SEPARATOR)

    /**
     * Renders a stored nickname as supporting search text: commas become word boundaries so token
     * coverage and fuzzy matching see separate words rather than one glued token.
     */
    fun searchText(nickname: String?): String? =
        split(nickname).takeIf { it.isNotEmpty() }?.joinToString(" ")
}
