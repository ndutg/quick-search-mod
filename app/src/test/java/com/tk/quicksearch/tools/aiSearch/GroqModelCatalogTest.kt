package com.tk.quicksearch.tools.aiSearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqModelCatalogTest {
    @Test
    fun retiredModelsMapToReplacements() {
        assertEquals("openai/gpt-oss-120b", GroqModelCatalog.replaceRetiredModel("llama-3.3-70b-versatile"))
        assertEquals("openai/gpt-oss-20b", GroqModelCatalog.replaceRetiredModel("llama-3.1-8b-instant"))
        assertEquals("qwen/qwen3.6-27b", GroqModelCatalog.replaceRetiredModel("qwen/qwen3.6-27b"))
    }

    @Test
    fun namespacedTextModelsAreKept() {
        assertTrue(GroqModelCatalog.isLikelyTextModel("openai/gpt-oss-120b"))
        assertTrue(GroqModelCatalog.isLikelyTextModel("qwen/qwen3.6-27b"))
        assertTrue(GroqModelCatalog.isLikelyTextModel("moonshotai/kimi-k2-instruct"))
        assertFalse(GroqModelCatalog.isLikelyTextModel("meta-llama/llama-guard-4-12b"))
        assertFalse(GroqModelCatalog.isLikelyTextModel("whisper-large-v3"))
    }
}
