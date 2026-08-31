package com.tk.quicksearch.tools.aiSearch

/**
 * Shared cleanup for model text that may include reasoning traces or markdown fences
 * before a JSON/plain answer.
 */
object LlmResponseText {
    private val REDACTED_THINKING_BLOCK =
        Regex(
            "<redacted_thinking>.*?</redacted_thinking>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )

    private val SHORT_THINKING_BLOCK =
        Regex(
            "<think>.*?</think>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )

    private val CHANNEL_THINKING_BLOCK =
        Regex("""<\|channel>thought[\s\S]*?<channel\|>""")

    private val OPEN_THINKING_TAG =
        Regex("<(?:think|redacted_thinking)\\b[^>]*>", RegexOption.IGNORE_CASE)

    private val CLOSE_THINKING_TAG =
        Regex("</(?:think|redacted_thinking)>", RegexOption.IGNORE_CASE)

    fun stripThinkingMarkers(text: String): String {
        var t = text
        repeat(8) {
            val next =
                CHANNEL_THINKING_BLOCK.replace(
                    SHORT_THINKING_BLOCK.replace(REDACTED_THINKING_BLOCK.replace(t, ""), ""),
                    "",
                )
            val danglingRemoved = stripDanglingThinkingPrefix(next)
            if (danglingRemoved == t) return danglingRemoved.trim()
            t = danglingRemoved
        }
        return t.trim()
    }

    fun extractJsonObjectPayload(raw: String): String {
        val stripped = stripThinkingMarkers(stripCodeFences(raw)).trim()
        val start = stripped.indexOf('{')
        if (start < 0) return stripped
        var depth = 0
        var inString = false
        var escape = false
        for (index in start until stripped.length) {
            val char = stripped[index]
            if (inString) {
                when {
                    escape -> escape = false
                    char == '\\' -> escape = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return stripped.substring(start, index + 1)
                }
            }
        }
        return stripped.substring(start).trim()
    }

    private fun stripCodeFences(raw: String): String {
        var text = raw.trim()
        if (!text.startsWith("```")) return text
        text = text.removePrefix("```json").removePrefix("```").trim()
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }
        return text
    }

    private fun stripDanglingThinkingPrefix(text: String): String {
        val open = OPEN_THINKING_TAG.find(text) ?: return text
        val close = CLOSE_THINKING_TAG.find(text, open.range.last)
        val jsonStart = text.indexOf('{', open.range.first)
        val endExclusive =
            when {
                close != null -> close.range.last + 1
                jsonStart >= 0 -> jsonStart
                else -> text.length
            }
        return text.removeRange(open.range.first, endExclusive)
    }
}
