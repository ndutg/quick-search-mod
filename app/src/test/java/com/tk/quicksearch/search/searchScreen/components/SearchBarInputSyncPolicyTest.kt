package com.tk.quicksearch.search.searchScreen.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `detects an alias consumed without a trailing space`() {
        assertEquals(
            "fs",
            detectConsumedPrefixAlias(previousText = "fs", currentQuery = "", activePrefixAliases = setOf("fs")),
        )
    }

    @Test
    fun `detects an alias consumed with a remainder`() {
        assertEquals(
            "fs",
            detectConsumedPrefixAlias(previousText = "fs notes", currentQuery = "notes", activePrefixAliases = setOf("fs")),
        )
    }

    @Test
    fun `ignores text that is not an alias`() {
        assertNull(
            detectConsumedPrefixAlias(previousText = "fx", currentQuery = "", activePrefixAliases = setOf("fs")),
        )
    }
}
