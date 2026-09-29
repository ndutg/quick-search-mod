package com.tk.quicksearch.tools.aiSearch

/**
 * Picks the model selected automatically when a provider has no valid selection, based on the live
 * catalog so new model versions are chosen without an app update. Falls back to the first listed
 * model when the preferred family is missing.
 */
object LlmDefaultModels {
    private const val GEMINI_DEFAULT_MODEL_ID = "gemini-flash-latest"
    private val metaMuseSparkRegex = Regex("^muse-spark-(\\d+(?:\\.\\d+)*)$", RegexOption.IGNORE_CASE)
    private val anthropicSonnetRegex = Regex("^claude-sonnet-(\\d+(?:-\\d+)*)$", RegexOption.IGNORE_CASE)
    private val openAiLunaRegex = Regex("^gpt-(\\d+(?:\\.\\d+)*)-luna$", RegexOption.IGNORE_CASE)
    private val datedSnapshotSuffixRegex = Regex("-\\d{8}$")

    fun pick(
        providerId: AiSearchLlmProviderId,
        models: List<LlmTextModel>,
    ): String {
        val preferred =
            when (providerId) {
                AiSearchLlmProviderId.GEMINI ->
                    models.firstOrNull { it.id == GEMINI_DEFAULT_MODEL_ID }?.id
                AiSearchLlmProviderId.META ->
                    latestVersion(models) { id -> metaMuseSparkRegex.find(id)?.groupValues?.get(1)?.split('.') }
                AiSearchLlmProviderId.ANTHROPIC ->
                    latestVersion(models) { id ->
                        anthropicSonnetRegex
                            .find(id.replace(datedSnapshotSuffixRegex, ""))
                            ?.groupValues
                            ?.get(1)
                            ?.split('-')
                    }
                AiSearchLlmProviderId.OPENAI ->
                    latestVersion(models) { id -> openAiLunaRegex.find(id)?.groupValues?.get(1)?.split('.') }
                // Groq and custom providers use the first listed model.
                else -> null
            }
        return preferred ?: models.firstOrNull()?.id.orEmpty()
    }

    /** Highest numeric version among matching ids; ties prefer the shorter (alias) id. */
    private fun latestVersion(
        models: List<LlmTextModel>,
        versionParts: (String) -> List<String>?,
    ): String? =
        models
            .mapNotNull { model ->
                val parts = versionParts(model.id.trim()) ?: return@mapNotNull null
                val version = parts.map { it.toIntOrNull() ?: return@mapNotNull null }
                model.id to version
            }.maxWithOrNull(
                Comparator<Pair<String, List<Int>>> { a, b -> compareVersions(a.second, b.second) }
                    .thenByDescending { it.first.length },
            )?.first

    private fun compareVersions(
        a: List<Int>,
        b: List<Int>,
    ): Int {
        for (index in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(index) { 0 }.compareTo(b.getOrElse(index) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}
