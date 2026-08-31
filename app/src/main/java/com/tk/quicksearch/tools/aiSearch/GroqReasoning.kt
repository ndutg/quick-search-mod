package com.tk.quicksearch.tools.aiSearch

import org.json.JSONObject

/** Groq chat-completion extras for reasoning models. */
internal data class GroqReasoningControls(
    val reasoningFormat: String? = null,
    val reasoningEffort: String? = null,
    val includeReasoning: Boolean? = null,
) {
    fun applyTo(root: JSONObject) {
        reasoningFormat?.let { root.put("reasoning_format", it) }
        reasoningEffort?.let { root.put("reasoning_effort", it) }
        includeReasoning?.let { root.put("include_reasoning", it) }
    }

    companion object {
        fun forModel(
            modelId: String,
            thinkingEnabled: Boolean,
        ): GroqReasoningControls {
            val id = modelId.lowercase()
            return when {
                id.contains("gpt-oss") ->
                    GroqReasoningControls(includeReasoning = thinkingEnabled)
                isQwenFamily(id) || id.contains("qwq") || id.contains("deepseek") -> {
                    // Default Groq format is `raw`, which embeds `<think>` in `content` and
                    // breaks JSON tools (world clock, dictionary). Hide traces; toggle only effort.
                    GroqReasoningControls(
                        reasoningFormat = "hidden",
                        reasoningEffort = if (isQwenFamily(id)) {
                            if (thinkingEnabled) "default" else "none"
                        } else {
                            null
                        },
                    )
                }
                thinkingEnabled && isLikelyReasoningModel(id) ->
                    GroqReasoningControls(reasoningEffort = "high")
                else -> GroqReasoningControls()
            }
        }

        fun shouldRequestJsonObject(responseMimeType: String): Boolean =
            responseMimeType.equals("application/json", ignoreCase = true)

        private fun isQwenFamily(modelId: String): Boolean = modelId.contains("qwen")

        private fun isLikelyReasoningModel(modelId: String): Boolean =
            modelId.contains("r1") || modelId.contains("reason")
    }
}
