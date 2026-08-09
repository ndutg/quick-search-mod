@file:Suppress("DEPRECATION")

package com.tk.quicksearch.shared.ui.theme

import me.tatarka.google.material.palettes.CorePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialColorUtilitiesParityTest {
    @Test
    fun replacementMatchesPreviousCorePaletteOutputs() {
        val palette = CorePalette.of(0xFF4285F4.toInt())
        val expectedTones =
            mapOf(
                10 to Triple(0xFF001A41.toInt(), 0xFF141B2C.toInt(), 0xFF29132D.toInt()),
                20 to Triple(0xFF002E69.toInt(), 0xFF293041.toInt(), 0xFF402843.toInt()),
                30 to Triple(0xFF004494.toInt(), 0xFF3F4759.toInt(), 0xFF583E5B.toInt()),
                40 to Triple(0xFF005AC1.toInt(), 0xFF575E71.toInt(), 0xFF715573.toInt()),
                80 to Triple(0xFFADC6FF.toInt(), 0xFFBFC6DC.toInt(), 0xFFDEBCDF.toInt()),
                90 to Triple(0xFFD8E2FF.toInt(), 0xFFDBE2F9.toInt(), 0xFFFBD7FC.toInt()),
                100 to Triple(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()),
            )

        expectedTones.forEach { (tone, expected) ->
            assertEquals("primary tone $tone", expected.first, palette.a1.tone(tone))
            assertEquals("secondary tone $tone", expected.second, palette.a2.tone(tone))
            assertEquals("tertiary tone $tone", expected.third, palette.a3.tone(tone))
        }
    }
}
