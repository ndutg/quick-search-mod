package com.tk.quicksearch.tools.aiSearch

/** Per-family version selection shared by the provider model pickers. */
internal object LlmModelVersions {
    /** A model's family (e.g. "sonnet") and numeric version. */
    data class FamilyVersion(
        val family: String,
        val version: List<Int>,
    )

    fun familyVersion(
        family: String,
        versionParts: List<String>,
    ): FamilyVersion? =
        FamilyVersion(family.lowercase(), versionParts.map { it.toIntOrNull() ?: return null })

    /**
     * Keeps, per family, one model for each of its [versionsPerFamily] highest versions (ties prefer
     * the shorter, alias id), plus any model matching [alwaysKeep]. Order is preserved. When nothing
     * matches (e.g. an unexpected naming scheme), the input list is returned unchanged.
     */
    fun filterLatest(
        models: List<LlmTextModel>,
        versionsPerFamily: Int = 1,
        alwaysKeep: (String) -> Boolean = { false },
        parseId: (String) -> FamilyVersion?,
    ): List<LlmTextModel> {
        val latestIds =
            models
                .mapNotNull { model -> parseId(model.id.trim().lowercase())?.let { model.id to it } }
                .groupBy { it.second.family }
                .values
                .flatMap { family ->
                    family
                        .groupBy { it.second.version }
                        .entries
                        .sortedWith { a, b -> compare(b.key, a.key) }
                        .take(versionsPerFamily)
                        .map { (_, sameVersion) -> sameVersion.minBy { it.first.length }.first }
                }.toSet()
        return models
            .filter { it.id in latestIds || alwaysKeep(it.id.trim().lowercase()) }
            .ifEmpty { models }
    }

    /** Ids of every model sharing the highest version (e.g. all sizes of the newest generation). */
    fun allOfLatestVersion(
        models: List<LlmTextModel>,
        parseVersion: (String) -> List<Int>?,
    ): Set<String> {
        val versioned =
            models.mapNotNull { model -> parseVersion(model.id.trim().lowercase())?.let { model.id to it } }
        val latest = versioned.maxOfWithOrNull({ a, b -> compare(a, b) }) { it.second } ?: return emptySet()
        return versioned.filter { compare(it.second, latest) == 0 }.map { it.first }.toSet()
    }

    private fun compare(
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
