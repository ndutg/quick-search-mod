package com.tk.quicksearch.tools.aiSearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TavilyWebSearchPlanTest {
    @Test
    fun withoutTavilyKeyKeepsNativeBehavior() {
        TavilyWebSearchMode.entries.forEach { mode ->
            assertEquals(
                WebSearchPlan(useNativeSearch = true, useTavily = false),
                resolveWebSearchPlan(mode, hasTavilyKey = false, nativeSearchSupported = true, nativeSearchRequested = true),
            )
            assertEquals(
                WebSearchPlan(useNativeSearch = false, useTavily = false),
                resolveWebSearchPlan(mode, hasTavilyKey = false, nativeSearchSupported = false, nativeSearchRequested = true),
            )
        }
    }

    @Test
    fun alwaysModeReplacesNativeSearch() {
        assertEquals(
            WebSearchPlan(useNativeSearch = false, useTavily = true),
            resolveWebSearchPlan(TavilyWebSearchMode.ALWAYS, hasTavilyKey = true, nativeSearchSupported = true, nativeSearchRequested = true),
        )
    }

    @Test
    fun whenUnsupportedModeOnlyUsesTavilyForModelsWithoutNativeSearch() {
        val mode = TavilyWebSearchMode.WHEN_MODEL_UNSUPPORTED
        assertEquals(
            WebSearchPlan(useNativeSearch = true, useTavily = false),
            resolveWebSearchPlan(mode, hasTavilyKey = true, nativeSearchSupported = true, nativeSearchRequested = true),
        )
        assertEquals(
            WebSearchPlan(useNativeSearch = false, useTavily = false),
            resolveWebSearchPlan(mode, hasTavilyKey = true, nativeSearchSupported = true, nativeSearchRequested = false),
        )
        assertEquals(
            WebSearchPlan(useNativeSearch = false, useTavily = true),
            resolveWebSearchPlan(mode, hasTavilyKey = true, nativeSearchSupported = false, nativeSearchRequested = false),
        )
    }

    @Test
    fun promptIncludesResultsBeforeQuestion() {
        val prompt =
            TavilyClient.buildPromptWithResults(
                prompt = "Who won yesterday?",
                results = listOf(TavilySearchResult("Match report", "https://example.com", "Team A won 2-1.")),
            )
        assertTrue(prompt.contains("[1] Match report\nhttps://example.com\nTeam A won 2-1."))
        assertTrue(prompt.endsWith("Question:\nWho won yesterday?"))
    }

    @Test
    fun unknownStoredModeFallsBackToDefault() {
        assertEquals(TavilyWebSearchMode.DEFAULT, TavilyWebSearchMode.fromStorageValue("bogus"))
    }
}
