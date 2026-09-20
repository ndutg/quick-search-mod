package com.tk.quicksearch.tools.aiSearch

import com.tk.quicksearch.search.data.UserAppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Prompt and native-search flag for one request after the Tavily setting is applied. */
data class PreparedWebSearch(
    val prompt: String,
    val useNativeSearch: Boolean,
)

/** Whether the provider can run its own web search as part of an answer request. */
fun providerSupportsNativeSearch(providerId: AiSearchLlmProviderId): Boolean =
    providerId != AiSearchLlmProviderId.OPENAI &&
        providerId != AiSearchLlmProviderId.GROQ &&
        !providerId.isCustom

/**
 * Whether [modelId] can ground answers itself. Falls back to the Gemma heuristic used elsewhere
 * when the catalog entry is missing (e.g. stale cache).
 */
fun modelSupportsGrounding(
    modelId: String,
    models: List<LlmTextModel>,
): Boolean =
    models.firstOrNull { it.id == modelId }?.supportsGrounding
        ?: !modelId.lowercase().startsWith("gemma-")

/**
 * Applies the Tavily web search setting to one request. If Tavily fails, the prompt is sent
 * unchanged and the model's own web search is used when it is available and turned on.
 *
 * @param onTavilyFailure invoked when the Tavily call fails, so callers can surface a toast.
 */
suspend fun prepareWebSearch(
    userPreferences: UserAppPreferences,
    searchQuery: String,
    prompt: String,
    nativeSearchSupported: Boolean,
    nativeSearchRequested: Boolean,
    onTavilyFailure: () -> Unit = {},
): PreparedWebSearch {
    val (mode, tavilyApiKey) =
        withContext(Dispatchers.IO) {
            userPreferences.getTavilyWebSearchMode() to userPreferences.getTavilyApiKey()
        }
    val plan =
        resolveWebSearchPlan(
            mode = mode,
            hasTavilyKey = !tavilyApiKey.isNullOrBlank(),
            nativeSearchSupported = nativeSearchSupported,
            nativeSearchRequested = nativeSearchRequested,
        )
    if (!plan.useTavily || tavilyApiKey == null) {
        return PreparedWebSearch(prompt = prompt, useNativeSearch = plan.useNativeSearch)
    }
    return TavilyClient(tavilyApiKey)
        .search(searchQuery)
        .fold(
            onSuccess = { results ->
                PreparedWebSearch(
                    prompt = TavilyClient.buildPromptWithResults(prompt, results),
                    useNativeSearch = false,
                )
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                onTavilyFailure()
                PreparedWebSearch(
                    prompt = prompt,
                    useNativeSearch = nativeSearchSupported && nativeSearchRequested,
                )
            },
        )
}
