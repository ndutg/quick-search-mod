package com.tk.quicksearch.tools.aiSearch

import org.json.JSONArray
import org.json.JSONObject

/** One completed question/answer exchange in an AI search or Quick Search help conversation. */
data class AiConversationTurn(
    val question: String,
    val answer: String,
)

/**
 * Appends earlier turns to an OpenAI-style `messages`/`input` array as alternating user and
 * assistant messages. Sending history as real messages, rather than folding it into one prompt,
 * makes every follow-up request start with the previous request's messages unchanged, so
 * providers with automatic prompt caching can bill the repeated prefix at the cached rate.
 */
internal fun JSONArray.putChatHistory(history: List<AiConversationTurn>): JSONArray {
    history.forEach { turn ->
        put(JSONObject().put("role", "user").put("content", turn.question))
        put(JSONObject().put("role", "assistant").put("content", turn.answer))
    }
    return this
}
