package com.tk.quicksearch.search.utils

private val FILE_SEARCH_SEPARATOR_REGEX = "[^\\p{L}\\p{Nd}]+".toRegex()

object FileSearchTextNormalizer {
    /**
     * Treat punctuation and repeated separators as word boundaries for file-name search.
     * This lets "teja karlapudi" match "teja-karlapudi", "teja--karlapudi",
     * "teja_karlapudi", and similar file-name variants.
     */
    fun normalizeForFileSearch(text: String): String =
        SearchTextNormalizer
            .normalizeForSearch(text)
            .replace(FILE_SEARCH_SEPARATOR_REGEX, " ")
            .trim()
            .replace(QUERY_WHITESPACE_REGEX, " ")

    fun queryTokens(text: String): List<String> =
        normalizeForFileSearch(text)
            .split(QUERY_WHITESPACE_REGEX)
            .filter { it.isNotBlank() }

    /**
     * Builds a case- and accent-insensitive SQLite GLOB pattern for a normalized query token.
     * MediaStore names keep their accents and SQLite only folds ASCII case, so each letter
     * becomes a character class of its variants: "cagri" matches "Çağrı" and "çağrı".
     */
    fun containsGlobPattern(normalizedToken: String): String =
        buildString(normalizedToken.length * 6 + 2) {
            append('*')
            normalizedToken.forEach { char ->
                val variants = GLOB_LETTER_VARIANTS[char]
                when {
                    variants != null -> append('[').append(char).append(char.uppercaseChar()).append(variants).append(']')
                    char.isLetter() && char.uppercaseChar() != char ->
                        append('[').append(char).append(char.uppercaseChar()).append(']')
                    char == '*' || char == '?' || char == '[' -> append('[').append(char).append(']')
                    else -> append(char)
                }
            }
            append('*')
        }
}

/** Accented forms (both cases) that [SearchTextNormalizer.normalizeForSearch] folds to each letter. */
private val GLOB_LETTER_VARIANTS =
    mapOf(
        'a' to "àáâãäåāăąÀÁÂÃÄÅĀĂĄ",
        'c' to "çćčÇĆČ",
        'd' to "ďĎ",
        'e' to "èéêëēėęěÈÉÊËĒĖĘĚ",
        'g' to "ğĞ",
        'i' to "ìíîïīįıÌÍÎÏĪĮİ",
        'k' to "ķĶ",
        'l' to "ĺļľĹĻĽ",
        'n' to "ñńņňÑŃŅŇ",
        'o' to "òóôõöōőÒÓÔÕÖŌŐ",
        'r' to "ŕřŔŘ",
        's' to "śşšșŚŞŠȘ",
        't' to "ťţțŤŢȚ",
        'u' to "ùúûüūůűųÙÚÛÜŪŮŰŲ",
        'y' to "ýÿÝŸ",
        'z' to "źżžŹŻŽ",
    )

private val QUERY_WHITESPACE_REGEX = "\\s+".toRegex()
