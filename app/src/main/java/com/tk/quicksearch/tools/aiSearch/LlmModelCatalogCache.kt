package com.tk.quicksearch.tools.aiSearch

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide memory of the last live model catalog per provider, so screens outside the search
 * ViewModel (e.g. the Custom Info editor) can show a model picker immediately instead of reloading.
 */
object LlmModelCatalogCache {
    private val catalogs = ConcurrentHashMap<AiSearchLlmProviderId, List<LlmTextModel>>()

    fun get(providerId: AiSearchLlmProviderId): List<LlmTextModel>? = catalogs[providerId]

    fun snapshot(): Map<AiSearchLlmProviderId, List<LlmTextModel>> = catalogs.toMap()

    fun put(providerId: AiSearchLlmProviderId, models: List<LlmTextModel>) {
        if (models.isNotEmpty()) catalogs[providerId] = models
    }
}
