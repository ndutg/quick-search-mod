package com.tk.quicksearch.tools.aiSearch

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmConversationTest {
    @Test
    fun putsEveryPreviousTurnAsAlternatingUserAndAssistantMessages() {
        val messages =
            JSONArray().putChatHistory(
                listOf(
                    AiConversationTurn("Who wrote Dune?", "Frank Herbert wrote Dune."),
                    AiConversationTurn("When was it published?", "It was published in 1965."),
                ),
            )

        assertEquals(4, messages.length())
        assertEquals(
            listOf(
                "user" to "Who wrote Dune?",
                "assistant" to "Frank Herbert wrote Dune.",
                "user" to "When was it published?",
                "assistant" to "It was published in 1965.",
            ),
            (0 until messages.length()).map { index ->
                val message = messages.getJSONObject(index)
                message.getString("role") to message.getString("content")
            },
        )
    }

    @Test
    fun followUpRequestStartsWithThePreviousRequestUnchanged() {
        val firstTurn = AiConversationTurn("Who wrote Dune?", "Frank Herbert wrote Dune.")
        val secondTurn = AiConversationTurn("When was it published?", "It was published in 1965.")

        val previous = JSONArray().putChatHistory(listOf(firstTurn)).toString()
        val next = JSONArray().putChatHistory(listOf(firstTurn, secondTurn)).toString()

        assertEquals(previous.dropLast(1), next.take(previous.length - 1))
    }
}
