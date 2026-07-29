package com.tk.quicksearch.search.contacts

import java.util.Locale

internal fun contactInitials(displayName: String): String =
    displayName
        .trim()
        .split(Regex("\\s+"))
        .mapNotNull { namePart ->
            namePart
                .codePoints()
                .filter { codePoint -> Character.isLetterOrDigit(codePoint) }
                .findFirst()
                .orElse(-1)
                .takeIf { it >= 0 }
                ?.let { codePoint -> String(Character.toChars(codePoint)) }
        }.take(2)
        .joinToString("")
        .uppercase(Locale.getDefault())
