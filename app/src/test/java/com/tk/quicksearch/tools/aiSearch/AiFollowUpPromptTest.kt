package com.tk.quicksearch.tools.aiSearch

import org.junit.Assert.assertEquals
import org.junit.Test

class AiFollowUpPromptTest {
    @Test
    fun includesEveryPreviousQuestionAndAnswerBeforeFinalFollowUp() {
        val prompt =
            buildAiFollowUpPrompt(
                previousTurns =
                    listOf(
                        AiConversationTurn("Who wrote Dune?", "Frank Herbert wrote Dune."),
                        AiConversationTurn("When was it published?", "It was published in 1965."),
                    ),
                followUpQuestion = "What other books are in that series?",
            )

        assertEquals(
            "Use the complete conversation below as context for the final follow-up question.\n\n" +
                "User: Who wrote Dune?\n" +
                "Assistant: Frank Herbert wrote Dune.\n\n" +
                "User: When was it published?\n" +
                "Assistant: It was published in 1965.\n\n" +
                "User follow-up: What other books are in that series?",
            prompt,
        )
    }
}
