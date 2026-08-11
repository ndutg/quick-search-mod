package com.tk.quicksearch.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSectionRegistryContractTest {
    @Test
    fun enabledSectionsHaveUniqueSearchAndSettingsMappings() {
        val definitions = SearchSectionRegistry.orderedDefinitions

        assertEquals(definitions.size, definitions.map { it.section }.distinct().size)
        assertEquals(definitions.size, definitions.map { it.aliasTargetId }.distinct().size)
        assertEquals(definitions.size, definitions.map { it.itemType }.distinct().size)
        assertEquals(definitions.size, definitions.map { it.appSettingsToggleKey }.distinct().size)
        assertTrue(definitions.all { it.aliasTargetId.isNotBlank() })
    }

    @Test
    fun everyEnabledSectionCanBeResolvedThroughEachRegistrationKey() {
        SearchSectionRegistry.orderedDefinitions.forEach { definition ->
            assertEquals(definition, SearchSectionRegistry.definitionFor(definition.section))
            assertEquals(
                definition,
                SearchSectionRegistry.definitionForAliasTargetId(definition.aliasTargetId),
            )
            assertEquals(
                definition.section,
                SearchSectionRegistry.sectionForToggle(definition.appSettingsToggleKey),
            )
            assertEquals(
                definition.section,
                SearchSectionRegistry.sectionForItemType(definition.itemType),
            )
        }
    }

    @Test
    fun everyEnabledSectionHasAnAppSettingsToggle() {
        SearchSectionRegistry.orderedSections.forEach { section ->
            assertNotNull(SearchSectionRegistry.definitionFor(section).appSettingsToggleKey)
        }
    }
}
