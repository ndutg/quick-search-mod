package com.tk.quicksearch.tools.aiSearch

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val CURRENT_TIME_PLACEHOLDER = "{time}"

private val currentTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())

internal fun expandCustomToolPrompt(prompt: String, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): String {
    if (!prompt.contains(CURRENT_TIME_PLACEHOLDER)) return prompt

    val currentTime = "${currentTimeFormatter.format(now)} (${now.zone.id})"
    return prompt.replace(CURRENT_TIME_PLACEHOLDER, currentTime)
}
