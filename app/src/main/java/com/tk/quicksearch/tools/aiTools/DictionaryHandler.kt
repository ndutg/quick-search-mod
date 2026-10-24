package com.tk.quicksearch.tools.aiTools

import android.content.Context
import com.tk.quicksearch.R
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

// Free English dictionary (Wiktionary data); no API key needed.
private const val DICTIONARY_API_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/"
private const val MAX_SYNONYMS = 5

class DictionaryHandler(
        private val context: Context,
) {
    suspend fun define(confirmed: ConfirmedDictionaryQuery): Result<DictionaryModelResult> =
            runCatching {
                val term = confirmed.term.trim()
                var connection: HttpURLConnection? = null
                try {
                    val encodedTerm = URLEncoder.encode(term.lowercase(Locale.ROOT), "UTF-8").replace("+", "%20")
                    connection = (URL(DICTIONARY_API_URL + encodedTerm).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                    }
                    val responseCode = runCatching { connection.responseCode }.getOrElse { error(unavailableMessage()) }
                    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                        error(context.getString(R.string.dictionary_definition_not_found, term))
                    }
                    if (responseCode !in 200..299) error(unavailableMessage())
                    val body = runCatching { connection.inputStream.bufferedReader().use { it.readText() } }
                            .getOrElse { error(unavailableMessage()) }
                    parseDictionaryEntries(body) ?: error(unavailableMessage())
                } finally {
                    connection?.disconnect()
                }
            }

    private fun unavailableMessage(): String = context.getString(R.string.dictionary_unavailable)
}

/** Returns null when the body isn't a usable entry list (e.g. a Cloudflare challenge page). */
internal fun parseDictionaryEntries(body: String): DictionaryModelResult? = runCatching {
    val entries = JSONArray(body)
    for (i in 0 until entries.length()) {
        val entry = entries.optJSONObject(i) ?: continue
        val word = entry.optString("word").trim()
        val meanings = entry.optJSONArray("meanings") ?: continue
        for (j in 0 until meanings.length()) {
            val meaning = meanings.optJSONObject(j) ?: continue
            val definitions = meaning.optJSONArray("definitions") ?: continue
            val definition = definitions.objects().firstOrNull { it.optString("definition").isNotBlank() }
                    ?: continue
            val example = definitions.objects().map { it.optString("example").trim() }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
            val synonyms = (definition.stringList("synonyms") + meaning.stringList("synonyms"))
                    .distinct()
                    .take(MAX_SYNONYMS)
            if (word.isBlank()) return@runCatching null
            return@runCatching DictionaryModelResult(
                    word = word,
                    partOfSpeech = meaning.optString("partOfSpeech").trim(),
                    meaning = definition.optString("definition").trim(),
                    example = example,
                    synonyms = synonyms,
            )
        }
    }
    null
}.getOrNull()

private fun JSONArray.objects(): List<JSONObject> = List(length()) { optJSONObject(it) }.filterNotNull()

private fun JSONObject.stringList(key: String): List<String> =
        optJSONArray(key)?.let { array -> List(array.length()) { array.optString(it).trim() } }
                ?.filter { it.isNotBlank() }
                .orEmpty()

data class DictionaryModelResult(
        val word: String,
        val partOfSpeech: String,
        val meaning: String,
        val example: String,
        val synonyms: List<String>,
)
