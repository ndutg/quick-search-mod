package com.tk.quicksearch.tools.aiTools

import android.content.Context
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.UserAppPreferences
import com.tk.quicksearch.tools.aiSearch.AiSearchLlmProviderRegistry
import com.tk.quicksearch.tools.aiSearch.LlmRequest

class WeatherHandler(
    private val context: Context,
    private val userPreferences: UserAppPreferences,
) {
    suspend fun getWeather(
        confirmed: ConfirmedWeatherQuery,
    ): Result<Pair<WeatherModelResult, String>> {
        val configuredLocation = userPreferences.getWeatherLocation()
        val location = confirmed.requestedLocation?.trim().orEmpty().ifBlank { configuredLocation }
        if (location.isBlank()) {
            return Result.failure(
                IllegalStateException(context.getString(R.string.weather_error_location_required)),
            )
        }
        val temperatureUnit = userPreferences.getWeatherTemperatureUnit()
        val windSpeedUnit = userPreferences.getWeatherWindSpeedUnit()
        val providerId = userPreferences.getWeatherProviderId()
        val provider = AiSearchLlmProviderRegistry.get(providerId, context)
        val apiKey = userPreferences.getLlmApiKey(providerId)?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            return Result.failure(
                IllegalStateException(context.getString(R.string.direct_search_error_no_key)),
            )
        }
        val modelId = userPreferences.getWeatherModel().trim().ifBlank { provider.defaultModelId }
        val advancedPayload = userPreferences.getWeatherAdvancedPayload()
        return provider.fetchAnswer(
            apiKey = apiKey,
            context = context,
            request =
                LlmRequest(
                    query = buildWeatherRequestQuery(location, temperatureUnit.promptValue, windSpeedUnit.promptValue),
                    modelId = modelId,
                    useGroundingWithGoogleSearch = true,
                    thinkingEnabled = userPreferences.isWeatherThinkingEnabled(),
                    useSystemInstruction = true,
                    systemInstruction = userPreferences.getWeatherSystemPrompt(),
                    responseMimeType = "text/plain",
                    advancedPayloadJson = advancedPayload.second.takeIf { advancedPayload.first },
                ),
        ).mapCatching { response ->
            val summary = response.text.trim()
            if (summary.isBlank()) error("empty weather response")
            WeatherModelResult(location = location, summary = summary) to modelId
        }
    }
}

data class WeatherModelResult(
    val location: String,
    val summary: String,
)

internal fun buildWeatherRequestQuery(
    location: String,
    temperatureUnit: String,
    windSpeedUnit: String,
): String =
    "Today's weather for $location. Use $temperatureUnit for temperatures and $windSpeedUnit " +
        "for wind speed. Respond with plain text only."
