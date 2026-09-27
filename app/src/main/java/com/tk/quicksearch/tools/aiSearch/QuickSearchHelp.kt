package com.tk.quicksearch.tools.aiSearch

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Built-in `@help` alias: a question typed with `@help` as its first or last word is answered by
 * the selected AI model using the bundled feature documentation instead of the web.
 */
object QuickSearchHelp {
    private const val LOG_TAG = "QuickSearchHelp"
    private const val ALIAS = "@help"
    private const val FEATURES_ASSET_FILE_NAME = "FEATURES.md"
    private const val PROMPT =
        "You are the in-app help assistant for Quick Search, an Android launcher and search app. " +
            "Answer the user's questions about Quick Search using only the documentation below.\n" +
            "- The user is already inside Quick Search, asking from its search bar. Never tell them to open " +
            "or launch Quick Search; start any steps from the search screen they are on.\n" +
            "- Keep answers short and practical. When the user asks how to do something, give clear steps.\n" +
            "- If the documentation does not cover the question, say you don't know and suggest " +
            "using the Contact Developer button below the answer.\n" +
            "- Do not invent features, settings, or menu names.\n" +
            "- Reply in the same language as the user's question.\n" +
            "- Use plain text with no markdown, bullets, emphasis, or special characters like *, _, `, or ~.\n\n" +
            "Documentation:\n"

    private val PREFIX_ALIAS = Regex("""^@help(?:\s+|$)""", RegexOption.IGNORE_CASE)
    private val SUFFIX_ALIAS = Regex("""(?:^|\s+)@help$""", RegexOption.IGNORE_CASE)

    @Volatile private var cachedDocumentation: String? = null

    /** Returns the question without the `@help` alias, or null when [query] doesn't use it. */
    fun questionOrNull(query: String): String? {
        val trimmed = query.trim()
        return when {
            PREFIX_ALIAS.containsMatchIn(trimmed) -> trimmed.replaceFirst(PREFIX_ALIAS, "").trim()
            SUFFIX_ALIAS.containsMatchIn(trimmed) -> trimmed.replaceFirst(SUFFIX_ALIAS, "").trim()
            else -> null
        }
    }

    /** Index range of the `@help` token in the untrimmed [query], for alias highlighting. */
    fun aliasRange(query: String): IntRange? {
        val start = query.length - query.trimStart().length
        val end = query.trimEnd().length
        if (end - start < ALIAS.length) return null
        return when {
            PREFIX_ALIAS.containsMatchIn(query.substring(start, end)) -> start until start + ALIAS.length
            SUFFIX_ALIAS.containsMatchIn(query.substring(start, end)) -> end - ALIAS.length until end
            else -> null
        }
    }

    /** Returns [query] without the `@help` alias, or the trimmed query when it doesn't use it. */
    fun stripAlias(query: String): String = questionOrNull(query) ?: query.trim()

    suspend fun systemInstruction(context: Context): String = PROMPT + documentation(context)

    private suspend fun documentation(context: Context): String {
        cachedDocumentation?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(FEATURES_ASSET_FILE_NAME).bufferedReader().use { it.readText() }
            }.onFailure { error ->
                Log.w(LOG_TAG, "Unable to read $FEATURES_ASSET_FILE_NAME", error)
            }.getOrNull()
                ?.also { cachedDocumentation = it }
                .orEmpty()
        }
    }
}
