package com.tk.quicksearch.tools.aiSearch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeClientTest {
    private fun headers(format: OpenCodeApiFormat) =
        OpenCodeClient.buildHeaders(
            apiKey = "sk-test",
            format = format,
            sessionId = "session-1",
            requestId = "request-1",
        )

    private fun body(
        format: OpenCodeApiFormat,
        history: List<AiConversationTurn> = emptyList(),
        useSystemInstruction: Boolean = true,
        advancedPayloadJson: String? = null,
    ) = JSONObject(
        OpenCodeClient.buildRequestBody(
            format = format,
            modelId = "kimi-k3",
            query = "Capital of France?",
            personalContext = "Lives in Ghent",
            useSystemInstruction = useSystemInstruction,
            systemInstruction = "Be brief.",
            history = history,
            advancedPayloadJson = advancedPayloadJson,
        ),
    )

    @Test
    fun sendsOpenCodeSessionClientAndRequestHeadersOnEveryFormat() {
        OpenCodeApiFormat.entries.forEach { format ->
            val headers = headers(format)
            assertEquals("session-1", headers["x-opencode-session"])
            assertEquals("request-1", headers["x-opencode-request"])
            assertEquals("quick-search", headers["x-opencode-client"])
            assertEquals("application/json", headers["Content-Type"])
            assertTrue(headers.getValue("User-Agent").startsWith("quick-search/"))
        }
    }

    @Test
    fun usesBearerAuthForOpenAiFormatsAndApiKeyHeaderForMessages() {
        assertEquals("Bearer sk-test", headers(OpenCodeApiFormat.CHAT_COMPLETIONS)["Authorization"])
        assertEquals("Bearer sk-test", headers(OpenCodeApiFormat.RESPONSES)["Authorization"])

        val messages = headers(OpenCodeApiFormat.MESSAGES)
        assertEquals("sk-test", messages["x-api-key"])
        assertEquals("2023-06-01", messages["anthropic-version"])
        assertFalse(messages.containsKey("Authorization"))
    }

    @Test
    fun chatCompletionsBodyPutsSystemPromptHistoryAndQueryInMessages() {
        val root = body(OpenCodeApiFormat.CHAT_COMPLETIONS, history = listOf(AiConversationTurn("Hi", "Hello")))
        val messages = root.getJSONArray("messages")

        assertEquals("kimi-k3", root.getString("model"))
        assertEquals(4, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertTrue(messages.getJSONObject(0).getString("content").contains("Lives in Ghent"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("Capital of France?", messages.getJSONObject(3).getString("content"))
    }

    @Test
    fun messagesBodyMovesSystemPromptToTopLevelAndSetsMaxTokens() {
        val root = body(OpenCodeApiFormat.MESSAGES)
        val messages = root.getJSONArray("messages")

        assertTrue(root.getString("system").startsWith("Be brief."))
        assertTrue(root.getInt("max_tokens") > 0)
        assertEquals(1, messages.length())
        assertEquals("user", messages.getJSONObject(0).getString("role"))
    }

    @Test
    fun responsesBodyUsesInstructionsAndInput() {
        val root = body(OpenCodeApiFormat.RESPONSES)

        assertTrue(root.getString("instructions").startsWith("Be brief."))
        assertEquals(1, root.getJSONArray("input").length())
        assertFalse(root.has("messages"))
    }

    @Test
    fun omitsSystemPromptWhenDisabled() {
        assertEquals(1, body(OpenCodeApiFormat.CHAT_COMPLETIONS, useSystemInstruction = false).getJSONArray("messages").length())
        assertFalse(body(OpenCodeApiFormat.MESSAGES, useSystemInstruction = false).has("system"))
        assertFalse(body(OpenCodeApiFormat.RESPONSES, useSystemInstruction = false).has("instructions"))
    }

    @Test
    fun advancedPayloadCannotReplaceModelOrConversation() {
        val root =
            body(
                OpenCodeApiFormat.RESPONSES,
                advancedPayloadJson = """{"model":"other","input":[],"temperature":0.1}""",
            )

        assertEquals("kimi-k3", root.getString("model"))
        assertEquals(1, root.getJSONArray("input").length())
        assertEquals(0.1, root.getDouble("temperature"), 0.0)
    }

    @Test
    fun extractsTextFromEachResponseFormat() {
        assertEquals(
            "Paris",
            OpenCodeClient.extractAnswer(
                OpenCodeApiFormat.CHAT_COMPLETIONS,
                """{"choices":[{"message":{"role":"assistant","content":"**Paris**"}}]}""",
            ),
        )
        assertEquals(
            "Paris",
            OpenCodeClient.extractAnswer(
                OpenCodeApiFormat.MESSAGES,
                """{"content":[{"type":"thinking","thinking":"hmm"},{"type":"text","text":"Paris"}]}""",
            ),
        )
        assertEquals(
            "Paris",
            OpenCodeClient.extractAnswer(
                OpenCodeApiFormat.RESPONSES,
                """{"output":[{"type":"reasoning","summary":[]},""" +
                    """{"type":"message","content":[{"type":"output_text","text":"Paris"}]}]}""",
            ),
        )
        assertNull(OpenCodeClient.extractAnswer(OpenCodeApiFormat.RESPONSES, """{"output":[]}"""))
    }
}
