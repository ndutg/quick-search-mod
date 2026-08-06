package com.tk.quicksearch.search.core

import java.lang.reflect.Constructor
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The extractors round-trip the composite [SearchUiState] back into its sub-states after every
 * legacy state update, so any field they forget silently reverts to its data-class default. Each
 * test flips every boolean flag away from its default and asserts the round-trip returns it
 * unchanged, which fails as soon as a newly added flag is left out of the extractor.
 */
class SearchStateExtractorTest {
    @Test
    fun featureStateRoundTripPreservesEveryFlag() {
        val features = withFlippedBooleans(SearchFeatureState())

        assertEquals(features, SearchStateExtractor.extractFeatureState(stateWith(features = features)))
    }

    @Test
    fun configStateRoundTripPreservesEveryFlag() {
        val config = withFlippedBooleans(SearchUiConfigState())

        assertEquals(config, SearchStateExtractor.extractConfigState(stateWith(config = config)))
    }

    private fun stateWith(
        features: SearchFeatureState = SearchFeatureState(),
        config: SearchUiConfigState = SearchUiConfigState(),
    ) = SearchUiState(
        results = SearchResultsState(),
        permissions = SearchPermissionState(),
        features = features,
        config = config,
    )

    /**
     * Rebuilds [instance] through its primary constructor with every boolean property inverted.
     * Kotlin emits backing fields in constructor-parameter order, so the declared fields line up
     * with the constructor arguments positionally.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> withFlippedBooleans(instance: T): T {
        val constructor = primaryConstructorOf(instance.javaClass)
        val fields =
            instance.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        check(fields.size == constructor.parameterCount) {
            "Expected ${constructor.parameterCount} backing fields, found ${fields.size}"
        }

        val arguments =
            fields.map { field ->
                field.isAccessible = true
                val value = field.get(instance)
                if (value is Boolean) !value else value
            }
        return constructor.newInstance(*arguments.toTypedArray()) as T
    }

    private fun primaryConstructorOf(type: Class<*>): Constructor<*> =
        type.declaredConstructors
            .filterNot { constructor ->
                constructor.parameterTypes.any { it.name.endsWith("DefaultConstructorMarker") }
            }.maxByOrNull { it.parameterCount }
            ?: error("No primary constructor found for ${type.name}")
}
