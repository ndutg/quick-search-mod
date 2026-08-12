package com.tk.quicksearch.widgets.utils

import com.tk.quicksearch.widgets.customButtonsWidget.CustomWidgetButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetPreferencesTest {
    @Test
    fun `default widget preferences use the white border shown by the default widget`() {
        assertEquals(BorderColorOption.WHITE, WidgetPreferences.Default.borderColorOption)
        assertEquals(WidgetDefaults.BORDER_COLOR_ARGB, WidgetPreferences.Default.borderColor)
    }

    @Test
    fun `standard widget with custom buttons keeps search affordance visible`() {
        val preferences =
            WidgetPreferences(
                showLabel = false,
                searchIconDisplay = SearchIconDisplay.OFF,
                customButtons = listOf(CustomWidgetButtonAction.App("com.example.app", "Example")),
            )

        val constrained = preferences.enforceVariantConstraints(WidgetVariant.STANDARD)

        assertEquals(SearchIconDisplay.LEFT, constrained.searchIconDisplay)
        assertFalse(constrained.showLabel)
    }

    @Test
    fun `custom buttons widget still has no search affordance`() {
        val preferences =
            WidgetPreferences(
                searchIconDisplay = SearchIconDisplay.LEFT,
                customButtons = listOf(CustomWidgetButtonAction.App("com.example.app", "Example")),
            )

        val constrained = preferences.enforceVariantConstraints(WidgetVariant.CUSTOM_BUTTONS_ONLY)

        assertEquals(SearchIconDisplay.OFF, constrained.searchIconDisplay)
        assertFalse(constrained.showLabel)
    }
}
