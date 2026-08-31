package com.tk.quicksearch.tools.aiSearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqReasoningTest {
    @Test
    fun hidesReasoningForCurrentQwenModelsEvenWhenThinkingIsOff() {
        val controls =
            GroqReasoningControls.forModel("qwen/qwen3.8-27b", thinkingEnabled = false)
        assertEquals("hidden", controls.reasoningFormat)
        assertEquals("none", controls.reasoningEffort)
    }

    @Test
    fun enablesDefaultEffortForQwenWhenThinkingIsOn() {
        val controls = GroqReasoningControls.forModel("qwen/qwen3-32b", thinkingEnabled = true)
        assertEquals("hidden", controls.reasoningFormat)
        assertEquals("default", controls.reasoningEffort)
    }

    @Test
    fun doesNotAttachQwenReasoningControlsToLlama() {
        val controls =
            GroqReasoningControls.forModel("llama-3.3-70b-versatile", thinkingEnabled = false)
        assertNull(controls.reasoningFormat)
        assertNull(controls.reasoningEffort)
        assertNull(controls.includeReasoning)
    }

    @Test
    fun requestsJsonObjectWhenMimeTypeIsJson() {
        assertTrue(GroqReasoningControls.shouldRequestJsonObject("application/json"))
        assertFalse(GroqReasoningControls.shouldRequestJsonObject("text/plain"))
    }
}
