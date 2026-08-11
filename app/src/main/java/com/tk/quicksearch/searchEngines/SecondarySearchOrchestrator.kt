package com.tk.quicksearch.searchEngines

import android.os.Looper
import com.tk.quicksearch.search.core.SearchSection
import com.tk.quicksearch.search.core.SearchSectionPermissionRequirement
import com.tk.quicksearch.search.core.SearchSectionRegistry
import com.tk.quicksearch.search.core.SearchUiState
import com.tk.quicksearch.search.core.UnifiedSearchResults
import com.tk.quicksearch.search.core.UnifiedSectionSearchConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

interface SecondarySearchDataSource {
    suspend fun performSearch(
        query: String,
        enabledFileTypes: Set<com.tk.quicksearch.search.models.FileType>,
        sectionSearchConfig: Map<SearchSection, UnifiedSectionSearchConfig>,
        showFolders: Boolean,
        showSystemFiles: Boolean,
        aliasSection: SearchSection?,
    ): UnifiedSearchResults
}

interface SecondaryWebSuggestionController {
    val isEnabled: Boolean

    fun fetchWebSuggestions(
        query: String,
        currentQueryVersion: Long,
        activeQueryVersionProvider: () -> Long,
        activeQueryProvider: () -> String,
    )

    fun cancelSuggestions()
}

interface DisabledSearchSectionsProvider {
    val disabledSections: Set<SearchSection>
}

class SecondarySearchOrchestrator(
    private val scope: CoroutineScope,
    private val unifiedSearchHandler: SecondarySearchDataSource,
    private val webSuggestionHandler: SecondaryWebSuggestionController,
    private val sectionManager: DisabledSearchSectionsProvider,
    private val uiStateUpdater: ((SearchUiState) -> SearchUiState) -> Unit,
    private val currentStateProvider: () -> SearchUiState,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val mainThreadChecker: () -> Boolean = {
        Looper.myLooper() == Looper.getMainLooper()
    },
) {
    private var searchJob: Job? = null
    private val queryVersion = AtomicLong(0L)

    // Track query prefixes that yielded no results to avoid redundant searches.
    private val lastQueryWithNoResultsBySection = mutableMapOf<SearchSection, String>()
    private var lastQueryLength: Int = 0

    companion object {
        private const val SECONDARY_SEARCH_DEBOUNCE_MS = 150L
        private const val WEB_SUGGESTIONS_AFTER_LOCAL_RESULTS_DELAY_MS = 32L
    }

    fun willRunSecondarySearch(query: String): Boolean {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return false

        val currentState = currentStateProvider()
        val isBackspacing = trimmedQuery.length < lastQueryLength
        return SearchSectionRegistry.secondarySearchDefinitions.any { definition ->
            val section = definition.section
            val skipNoResultsCache =
                definition.minimumQueryLength == 1 && trimmedQuery.length == 1
            val shouldSkipSection =
                !skipNoResultsCache &&
                    shouldSkipSearchForSection(trimmedQuery, section, isBackspacing)
            val isSectionEnabledForSearchResults = section !in sectionManager.disabledSections
            val isSectionEnabledForTopMatches =
                isSectionEnabledForTopMatches(currentState, section)
            trimmedQuery.length >= definition.minimumQueryLength &&
                hasPermissionForSection(currentState, section) &&
                (isSectionEnabledForSearchResults || isSectionEnabledForTopMatches) &&
                !shouldSkipSection
        }
    }

    fun performSecondarySearches(query: String) {
        if (!isOnMainThread()) {
            scope.launch(mainDispatcher) {
                performSecondarySearchesInternal(query)
            }
            return
        }
        performSecondarySearchesInternal(query)
    }

    private fun performSecondarySearchesInternal(query: String) {
        searchJob?.cancel()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            // Clear all no-results tracking when query is cleared.
            clearNoResultTracking()
            lastQueryLength = 0

            uiStateUpdater { state ->
                state.copy(
                    contactResults = emptyList(),
                    fileResults = emptyList(),
                    settingResults = emptyList(),
                    calendarEvents = emptyList(),
                    noteResults = emptyList(),
                    appSettingResults = emptyList(),
                    appShortcutResults = emptyList(),
                    isSecondarySearchInProgress = false,
                    webSuggestionsLoading = false,
                    // Flush any staged app results (query was cleared, so clear them too).
                    searchResults = emptyList(),
                    pendingSearchResults = null,
                )
            }
            return
        }

        // Detect backspacing: if query is shorter, reset relevant no-results prefixes
        val isBackspacing = trimmedQuery.length < lastQueryLength
        if (isBackspacing) {
            resetNoResultPrefixesForBackspace(trimmedQuery)
        }

        val currentState = currentStateProvider()
        val sectionSearchConfig =
            SearchSectionRegistry.secondarySearchDefinitions.associate { definition ->
                val section = definition.section
                val isSectionEnabledForSearchResults = section !in sectionManager.disabledSections
                val isSectionEnabledForTopMatches =
                    isSectionEnabledForTopMatches(currentState, section)
                val canSearchSection =
                    trimmedQuery.length >= definition.minimumQueryLength &&
                        hasPermissionForSection(currentState, section) &&
                        (isSectionEnabledForSearchResults || isSectionEnabledForTopMatches)
                val skipNoResultsCache =
                    definition.minimumQueryLength == 1 && trimmedQuery.length == 1
                if (skipNoResultsCache) {
                    // Keep first-letter section alias searches responsive even if a previous
                    // single-letter query was cached as "no results" before data finished loading.
                    clearNoResultTracking(section)
                }
                val shouldSkipSection =
                    !skipNoResultsCache &&
                        shouldSkipSearchForSection(trimmedQuery, section, isBackspacing)
                val shouldSearch = canSearchSection && !shouldSkipSection
                val enableFuzzyMatching =
                    shouldSearch &&
                        currentState.fuzzySearchEnabled &&
                        supportsFuzzySecondarySearch(section)
                section to
                    UnifiedSectionSearchConfig(
                        shouldSearch = shouldSearch,
                        enableFuzzyMatching = enableFuzzyMatching,
                    )
            }
        val hasAnySecondarySectionToSearch = sectionSearchConfig.values.any { it.shouldSearch }
        val shouldFetchWebSuggestions = trimmedQuery.length >= 2 && webSuggestionHandler.isEnabled

        if (!hasAnySecondarySectionToSearch) {
            val currentVersion = queryVersion.incrementAndGet()
            lastQueryLength = trimmedQuery.length
            // No secondary sections — flush any pending app results immediately so they
            // aren't held back waiting for a secondary search that will never complete.
            uiStateUpdater { state ->
                state.copy(
                    isSecondarySearchInProgress = false,
                    webSuggestionsLoading = false,
                    searchResults = state.pendingSearchResults ?: state.searchResults,
                    pendingSearchResults = null,
                )
            }

            searchJob =
                scope.launch(workerDispatcher) {
                    delay(SECONDARY_SEARCH_DEBOUNCE_MS)
                    if (currentVersion != queryVersion.get()) return@launch

                    withContext(mainDispatcher) {
                        if (currentVersion != queryVersion.get()) return@withContext

                        if (shouldFetchWebSuggestions) {
                            webSuggestionHandler.fetchWebSuggestions(
                                trimmedQuery,
                                currentVersion,
                                activeQueryVersionProvider = {
                                    this@SecondarySearchOrchestrator.queryVersion.get()
                                },
                                activeQueryProvider = { currentStateProvider().query },
                            )
                        } else {
                            webSuggestionHandler.cancelSuggestions()
                            uiStateUpdater { state ->
                                state.copy(webSuggestions = emptyList(), webSuggestionsLoading = false)
                            }
                        }
                    }
                }
            return
        }

        val currentVersion = queryVersion.incrementAndGet()
        lastQueryLength = trimmedQuery.length
        uiStateUpdater { it.copy(isSecondarySearchInProgress = true) }

        searchJob =
            scope.launch(workerDispatcher) {
                // Debounce expensive contact/file queries during rapid typing
                delay(SECONDARY_SEARCH_DEBOUNCE_MS)
                if (currentVersion != queryVersion.get()) return@launch

                val unifiedResults =
                    unifiedSearchHandler.performSearch(
                        query = trimmedQuery,
                        enabledFileTypes = currentState.enabledFileTypes,
                        sectionSearchConfig = sectionSearchConfig,
                        showFolders = currentState.showFolders,
                        showSystemFiles = currentState.showSystemFiles,
                        aliasSection = null,
                    )

                withContext(mainDispatcher) {
                    if (currentVersion == queryVersion.get()) {
                        // Update no-results tracking based on search results
                        SearchSectionRegistry.secondarySearchDefinitions.forEach { definition ->
                            val section = definition.section
                            val shouldSearch = sectionSearchConfig[section]?.shouldSearch == true
                            updateNoResultTracking(
                                section = section,
                                shouldSearch = shouldSearch,
                                query = trimmedQuery,
                                hadResults = hasResultsForSection(unifiedResults, section),
                            )
                        }

                        uiStateUpdater { state ->
                            state.copy(
                                contactResults = unifiedResults.contactResults,
                                fileResults = unifiedResults.fileResults,
                                settingResults = unifiedResults.settingResults,
                                calendarEvents = unifiedResults.calendarEvents,
                                noteResults = unifiedResults.noteResults,
                                appSettingResults = unifiedResults.appSettingResults,
                                recentResultRecencyIndex = unifiedResults.recencyIndex,
                                // Preserve existing results when the search was skipped via the
                                // no-results cache; overwriting with empty would clear valid
                                // results that were found for the current or a prior query.
                                appShortcutResults =
                                    if (sectionSearchConfig[SearchSection.APP_SHORTCUTS]?.shouldSearch == true) {
                                        unifiedResults.appShortcutResults
                                    } else {
                                        state.appShortcutResults
                                    },
                                isSecondarySearchInProgress = false,
                                // Flush any staged app results atomically with secondary results
                                // so both appear in the UI in a single state update.
                                searchResults = state.pendingSearchResults ?: state.searchResults,
                                pendingSearchResults = null,
                            )
                        }

                        if (!shouldFetchWebSuggestions) {
                            // Clear suggestions if disabled or query too short
                            webSuggestionHandler.cancelSuggestions()
                            uiStateUpdater { state ->
                                state.copy(webSuggestions = emptyList(), webSuggestionsLoading = false)
                            }
                        }
                    }
                }

                if (shouldFetchWebSuggestions) {
                    // Let the local-results state update render before starting DNS/network work.
                    // On older, memory-constrained devices, launching both in the same frame can
                    // preempt the main thread while Compose is measuring the new result cards.
                    delay(WEB_SUGGESTIONS_AFTER_LOCAL_RESULTS_DELAY_MS)
                    if (currentVersion != queryVersion.get()) return@launch

                    withContext(mainDispatcher) {
                        if (currentVersion != queryVersion.get()) return@withContext
                        webSuggestionHandler.fetchWebSuggestions(
                            trimmedQuery,
                            currentVersion,
                            activeQueryVersionProvider = {
                                this@SecondarySearchOrchestrator.queryVersion.get()
                            },
                            activeQueryProvider = { currentStateProvider().query },
                        )
                    }
                }
            }
    }

    fun performTargetedSecondarySearch(
        query: String,
        section: SearchSection,
        ignoreSectionToggle: Boolean = false,
    ) {
        if (!isOnMainThread()) {
            scope.launch(mainDispatcher) {
                performTargetedSecondarySearchInternal(
                    query = query,
                    section = section,
                    ignoreSectionToggle = ignoreSectionToggle,
                )
            }
            return
        }
        performTargetedSecondarySearchInternal(
            query = query,
            section = section,
            ignoreSectionToggle = ignoreSectionToggle,
        )
    }

    private fun performTargetedSecondarySearchInternal(
        query: String,
        section: SearchSection,
        ignoreSectionToggle: Boolean,
    ) {
        searchJob?.cancel()
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            clearNoResultTracking()
            uiStateUpdater { state ->
                state.copy(
                    contactResults = emptyList(),
                    fileResults = emptyList(),
                    settingResults = emptyList(),
                    calendarEvents = emptyList(),
                    noteResults = emptyList(),
                    appSettingResults = emptyList(),
                    appShortcutResults = emptyList(),
                    webSuggestions = emptyList(),
                    webSuggestionsLoading = false,
                    isSecondarySearchInProgress = false,
                    // Flush staged app results (query cleared — discard them).
                    searchResults = emptyList(),
                    pendingSearchResults = null,
                )
            }
            return
        }

        val currentState = currentStateProvider()
        val isBackspacing = trimmedQuery.length < lastQueryLength
        if (isBackspacing) {
            resetNoResultPrefixesForBackspace(trimmedQuery)
        }
        val sectionDefinition = SearchSectionRegistry.definitionFor(section)
        val skipNoResultsCache =
            sectionDefinition.minimumQueryLength == 1 &&
                trimmedQuery.length == sectionDefinition.minimumQueryLength
        if (skipNoResultsCache) {
            clearNoResultTracking(section)
        }

        val canSearchTargetSection =
            sectionDefinition.participatesInSecondarySearch &&
                trimmedQuery.length >= sectionDefinition.minimumQueryLength &&
                hasPermissionForSection(currentState, section) &&
                (ignoreSectionToggle || section !in sectionManager.disabledSections)
        val shouldSkipSection =
            !skipNoResultsCache &&
                shouldSkipSearchForSection(trimmedQuery, section, isBackspacing)
        val shouldRunTargetedSearch = canSearchTargetSection && !shouldSkipSection

        val sectionSearchConfig =
            SearchSectionRegistry.secondarySearchDefinitions.associate { definition ->
                val shouldSearch = definition.section == section && shouldRunTargetedSearch
                val enableFuzzyMatching =
                    shouldSearch &&
                        currentState.fuzzySearchEnabled &&
                        supportsFuzzySecondarySearch(definition.section)
                definition.section to
                    UnifiedSectionSearchConfig(
                        shouldSearch = shouldSearch,
                        enableFuzzyMatching = enableFuzzyMatching,
                    )
            }

        val currentVersion = queryVersion.incrementAndGet()
        lastQueryLength = trimmedQuery.length
        if (!shouldRunTargetedSearch) {
            uiStateUpdater { state ->
                state.copy(
                    webSuggestions = emptyList(),
                    webSuggestionsLoading = false,
                    isSecondarySearchInProgress = false,
                    // Flush any staged app results — no targeted search will complete.
                    searchResults = state.pendingSearchResults ?: state.searchResults,
                    pendingSearchResults = null,
                )
            }
            return
        }

        uiStateUpdater { it.copy(isSecondarySearchInProgress = true) }
        searchJob =
            scope.launch(workerDispatcher) {
                delay(SECONDARY_SEARCH_DEBOUNCE_MS)
                if (currentVersion != queryVersion.get()) return@launch

                val unifiedResults =
                    unifiedSearchHandler.performSearch(
                        query = trimmedQuery,
                        enabledFileTypes = currentState.enabledFileTypes,
                        sectionSearchConfig = sectionSearchConfig,
                        showFolders = currentState.showFolders,
                        showSystemFiles = currentState.showSystemFiles,
                        aliasSection = section,
                    )

                withContext(mainDispatcher) {
                    if (currentVersion != queryVersion.get()) return@withContext
                    updateNoResultTracking(
                        section = section,
                        shouldSearch = shouldRunTargetedSearch,
                        query = trimmedQuery,
                        hadResults = hasResultsForSection(unifiedResults, section),
                    )
                    uiStateUpdater { state ->
                        state.copy(
                            contactResults = unifiedResults.contactResults,
                            fileResults = unifiedResults.fileResults,
                            settingResults = unifiedResults.settingResults,
                            calendarEvents = unifiedResults.calendarEvents,
                            noteResults = unifiedResults.noteResults,
                            appSettingResults = unifiedResults.appSettingResults,
                            appShortcutResults = unifiedResults.appShortcutResults,
                            recentResultRecencyIndex = unifiedResults.recencyIndex,
                            webSuggestions = emptyList(),
                            webSuggestionsLoading = false,
                            isSecondarySearchInProgress = false,
                            // Flush staged app results atomically with secondary results.
                            searchResults = state.pendingSearchResults ?: state.searchResults,
                            pendingSearchResults = null,
                        )
                    }
                }
            }
    }

    fun resetNoResultTracking() {
        if (!isOnMainThread()) {
            scope.launch(mainDispatcher) {
                resetNoResultTrackingInternal()
            }
            return
        }
        resetNoResultTrackingInternal()
    }

    private fun resetNoResultTrackingInternal() {
        clearNoResultTracking()
        lastQueryLength = 0
    }

    fun performWebSuggestionsOnly(query: String) {
        if (!isOnMainThread()) {
            scope.launch(mainDispatcher) {
                performWebSuggestionsOnlyInternal(query)
            }
            return
        }
        performWebSuggestionsOnlyInternal(query)
    }

    private fun performWebSuggestionsOnlyInternal(query: String) {
        searchJob?.cancel()
        val trimmedQuery = query.trim()
        val currentVersion = queryVersion.incrementAndGet()
        lastQueryLength = trimmedQuery.length
        // Web-suggestions-only path (search engine alias): secondary search is not
        // running, so flush any staged app results immediately.
        uiStateUpdater { state ->
            state.copy(
                isSecondarySearchInProgress = false,
                webSuggestionsLoading = false,
                searchResults = state.pendingSearchResults ?: state.searchResults,
                pendingSearchResults = null,
            )
        }

        searchJob =
            scope.launch(workerDispatcher) {
                // Debounce to match regular search behavior
                delay(SECONDARY_SEARCH_DEBOUNCE_MS)
                if (currentVersion != queryVersion.get()) return@launch

                withContext(mainDispatcher) {
                    // Clear all other results
                    uiStateUpdater { state ->
                        state.copy(
                            contactResults = emptyList(),
                            fileResults = emptyList(),
                            settingResults = emptyList(),
                            calendarEvents = emptyList(),
                            noteResults = emptyList(),
                            appSettingResults = emptyList(),
                            appShortcutResults = emptyList(),
                            isSecondarySearchInProgress = false,
                            webSuggestionsLoading = false,
                        )
                    }

                    // Fetch web suggestions if enabled and query is long enough
                    val suggestionsEnabled = webSuggestionHandler.isEnabled
                    val queryLengthCheck = trimmedQuery.length >= 2
                    if (suggestionsEnabled && queryLengthCheck) {
                        webSuggestionHandler.fetchWebSuggestions(
                            trimmedQuery,
                            currentVersion,
                            activeQueryVersionProvider = {
                                this@SecondarySearchOrchestrator.queryVersion.get()
                            },
                            activeQueryProvider = { currentStateProvider().query },
                        )
                    } else {
                        webSuggestionHandler.cancelSuggestions()
                        uiStateUpdater {
                            state -> state.copy(webSuggestions = emptyList(), webSuggestionsLoading = false)
                        }
                    }
                }
            }
    }

    fun cancel() {
        if (!isOnMainThread()) {
            scope.launch(mainDispatcher) {
                searchJob?.cancel()
                uiStateUpdater { state ->
                    state.copy(
                        isSecondarySearchInProgress = false,
                        webSuggestionsLoading = false,
                        searchResults = state.pendingSearchResults ?: state.searchResults,
                        pendingSearchResults = null,
                    )
                }
            }
            return
        }
        searchJob?.cancel()
        uiStateUpdater { state ->
            state.copy(
                isSecondarySearchInProgress = false,
                webSuggestionsLoading = false,
                // Flush any staged app results so they aren't permanently hidden.
                searchResults = state.pendingSearchResults ?: state.searchResults,
                pendingSearchResults = null,
            )
        }
    }

    private fun isOnMainThread(): Boolean = mainThreadChecker()

    private fun resetNoResultPrefixesForBackspace(trimmedQuery: String) {
        val currentLength = trimmedQuery.length
        val keysToClear =
            lastQueryWithNoResultsBySection
                .filterValues { cachedQuery -> currentLength < cachedQuery.length }
                .keys
        keysToClear.forEach { section -> clearNoResultTracking(section) }
    }

    private fun clearNoResultTracking(section: SearchSection? = null) {
        if (section == null) {
            lastQueryWithNoResultsBySection.clear()
        } else {
            lastQueryWithNoResultsBySection.remove(section)
        }
    }

    private fun shouldSkipSearchForSection(
        query: String,
        section: SearchSection,
        isBackspacing: Boolean,
    ): Boolean {
        if (isBackspacing) return false
        val lastNoResultQuery = lastQueryWithNoResultsBySection[section] ?: return false
        return query.startsWith(lastNoResultQuery)
    }

    private fun updateNoResultTracking(
        section: SearchSection,
        shouldSearch: Boolean,
        query: String,
        hadResults: Boolean,
    ) {
        if (!shouldSearch) return
        if (hadResults) {
            clearNoResultTracking(section)
        } else {
            lastQueryWithNoResultsBySection[section] = query
        }
    }

    private fun hasPermissionForSection(
        state: SearchUiState,
        section: SearchSection,
    ): Boolean =
        when (SearchSectionRegistry.definitionFor(section).permissionRequirement) {
            SearchSectionPermissionRequirement.CONTACTS -> state.hasContactPermission
            SearchSectionPermissionRequirement.FILES -> state.hasFilePermission
            SearchSectionPermissionRequirement.CALENDAR -> state.hasCalendarPermission
            null -> true
        }

    private fun isSectionEnabledForTopMatches(
        state: SearchUiState,
        section: SearchSection,
    ): Boolean {
        if (!state.topMatchesEnabled) return false
        if (section in state.disabledTopMatchesSections) return false
        return section in state.topMatchesSectionOrder
    }

    private fun supportsFuzzySecondarySearch(section: SearchSection): Boolean =
        when (section) {
            SearchSection.CONTACTS,
            SearchSection.FILES,
            SearchSection.SETTINGS,
            SearchSection.APP_SETTINGS,
            SearchSection.APP_SHORTCUTS,
            -> true

            SearchSection.APPS,
            SearchSection.CALENDAR,
            SearchSection.NOTES,
            -> false
        }

    private fun hasResultsForSection(
        results: UnifiedSearchResults,
        section: SearchSection,
    ): Boolean =
        when (section) {
            SearchSection.CONTACTS -> results.contactResults.isNotEmpty()
            SearchSection.FILES -> results.fileResults.isNotEmpty()
            SearchSection.SETTINGS -> results.settingResults.isNotEmpty()
            SearchSection.CALENDAR -> results.calendarEvents.isNotEmpty()
            SearchSection.NOTES -> results.noteResults.isNotEmpty()
            SearchSection.APP_SETTINGS -> results.appSettingResults.isNotEmpty()
            SearchSection.APP_SHORTCUTS -> results.appShortcutResults.isNotEmpty()
            SearchSection.APPS -> false
        }
}
