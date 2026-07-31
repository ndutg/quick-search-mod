package com.tk.quicksearch.tools.aiTools

object WeatherIntentParser {
    private val prefixPattern = Regex("""(?i)^weather(?:\s+(?:in|for))?(?:\s+(.+))?$""")
    private val suffixPattern = Regex("""(?i)^(.+?)\s+weather$""")

    fun isCandidate(trimmedQuery: String): Boolean {
        val normalized = trimmedQuery.trim()
        return normalized.equals("weather", ignoreCase = true) ||
            normalized.startsWith("weather ", ignoreCase = true) ||
            normalized.endsWith(" weather", ignoreCase = true)
    }

    fun parseConfirmed(trimmedQuery: String): ConfirmedWeatherQuery? {
        val normalized = trimmedQuery.trim()
        if (normalized.isBlank()) return null
        val location =
            prefixPattern.matchEntire(normalized)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                .ifBlank {
                    suffixPattern.matchEntire(normalized)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                }
        if (!isCandidate(normalized)) return null
        return ConfirmedWeatherQuery(
            requestedLocation = location.takeIf { it.isNotBlank() },
            originalQuery = normalized,
        )
    }
}

data class ConfirmedWeatherQuery(
    val requestedLocation: String?,
    val originalQuery: String,
)
