package com.tk.quicksearch.search.searchScreen.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchBarInputSyncPolicyTest {
    @Test
    fun `keeps a newer local voice transcription when state has its prefix`() {
        assertTrue(
            shouldDeferTextFieldValueSync(
                stateQuery = "What's",
                localText = "What's the weather in Barcelona today?",
                localInputAwaitingStateAck = "What's the weather in Barcelona today?",
            ),
        )
    }

    @Test
    fun `keeps a newer local transcription when state is still empty`() {
        assertTrue(
            shouldDeferTextFieldValueSync(
                stateQuery = "",
                localText = "What's",
                localInputAwaitingStateAck = "What's",
            ),
        )
    }

    @Test
    fun `applies an unrelated external query update`() {
        assertFalse(
            shouldDeferTextFieldValueSync(
                stateQuery = "weather Barcelona",
                localText = "What's",
                localInputAwaitingStateAck = "What's",
            ),
        )
    }

    @Test
    fun `does not defer when state has caught up`() {
        assertFalse(
            shouldDeferTextFieldValueSync(
                stateQuery = "What's the weather",
                localText = "What's the weather",
                localInputAwaitingStateAck = "What's the weather",
            ),
        )
    }

    @Test
    fun `applies a clear that was not initiated by pending local input`() {
        assertFalse(
            shouldDeferTextFieldValueSync(
                stateQuery = "",
                localText = "What's the weather in Barcelona today?",
                localInputAwaitingStateAck = null,
            ),
        )
    }
}
