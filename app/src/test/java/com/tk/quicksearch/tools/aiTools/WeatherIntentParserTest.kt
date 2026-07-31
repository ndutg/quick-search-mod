package com.tk.quicksearch.tools.aiTools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherIntentParserTest {
    @Test
    fun exactWeatherUsesConfiguredLocation() {
        assertEquals(null, WeatherIntentParser.parseConfirmed("weather")?.requestedLocation)
    }

    @Test
    fun weatherPrefixExtractsLocation() {
        assertEquals(
            "New York, NY",
            WeatherIntentParser.parseConfirmed("weather in New York, NY")?.requestedLocation,
        )
    }

    @Test
    fun weatherSuffixExtractsLocation() {
        assertEquals("Tokyo", WeatherIntentParser.parseConfirmed("Tokyo weather")?.requestedLocation)
    }

    @Test
    fun unrelatedQueryIsNotConfirmed() {
        assertNull(WeatherIntentParser.parseConfirmed("tomorrow forecast"))
    }
}
