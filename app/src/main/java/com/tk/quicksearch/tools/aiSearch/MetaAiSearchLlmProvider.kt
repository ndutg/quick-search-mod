package com.tk.quicksearch.tools.aiSearch

import android.content.Context

object MetaAiSearchLlmProvider : AiSearchLlmProvider {
    override val id: AiSearchLlmProviderId = AiSearchLlmProviderId.META
    override val displayName: String = "Meta AI"
    override val defaultModelId: String = MetaModelCatalog.DEFAULT_MODEL_ID
    override val defaultGroundingEnabled: Boolean = MetaModelCatalog.DEFAULT_GROUNDING_ENABLED
    override val fallbackTextModels: List<LlmTextModel> = MetaModelCatalog.FALLBACK_TEXT_MODELS

    override suspend fun fetchAvailableTextModels(
        apiKey: String,
        context: Context,
    ): Result<List<LlmTextModel>> = MetaClient.fetchAvailableTextModels(apiKey, context)

    override suspend fun fetchAnswer(
        apiKey: String,
        context: Context,
        request: LlmRequest,
    ): Result<LlmResponse> =
        MetaClient(apiKey = apiKey, context = context).fetchAnswer(
            query = request.query,
            personalContext = request.personalContext,
            modelId = request.modelId,
            useGrounding = request.useGroundingWithGoogleSearch,
            thinkingEnabled = request.thinkingEnabled,
            useSystemInstruction = request.useSystemInstruction,
            systemInstruction = request.systemInstruction,
            responseMimeType = request.responseMimeType,
        ).map(::LlmResponse)
}
