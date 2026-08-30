package com.tk.quicksearch.tools.aiSearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmResponseTextTest {
    @Test
    fun stripsClosedQwenThinkBlock() {
        val raw =
            """
            <think>
            User asked for Tokyo time. I should output JSON.
            </think>
            {"world_clock_text":"2:31 AM","time_text":"Monday, August 31, 2026","place_text":"Tokyo, Japan","time_zone_text":"Japan Standard Time (JST)"}
            """.trimIndent()

        val payload = LlmResponseText.extractJsonObjectPayload(raw)
        assertTrue(payload.startsWith("{"))
        assertTrue(payload.contains("\"world_clock_text\":\"2:31 AM\""))
        assertTrue(payload.contains("\"place_text\":\"Tokyo, Japan\""))
        assertFalse(payload.contains("<think>"))
    }

    @Test
    fun extractsJsonWhenThinkBlockIsUnclosed() {
        val raw =
            """
            <think>
            Still reasoning about the timezone
            {"world_clock_text":"9:00 AM","time_text":"Tuesday","place_text":"Tokyo, Japan","time_zone_text":"JST"}
            """.trimIndent()

        val payload = LlmResponseText.extractJsonObjectPayload(raw)
        assertEquals(
            """{"world_clock_text":"9:00 AM","time_text":"Tuesday","place_text":"Tokyo, Japan","time_zone_text":"JST"}""",
            payload,
        )
    }

    @Test
    fun stripThinkingRemovesUnclosedThinkWithoutJson() {
        val stripped = LlmResponseText.stripThinkingMarkers("<think>internal notes only")
        assertFalse(stripped.contains("<think>", ignoreCase = true))
        assertTrue(stripped.isEmpty())
    }
}
