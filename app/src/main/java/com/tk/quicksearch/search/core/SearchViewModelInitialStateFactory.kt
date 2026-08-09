package com.tk.quicksearch.search.core

import android.content.Context
import com.tk.quicksearch.search.data.UserAppPreferences
import com.tk.quicksearch.search.data.filterAvailableStartupApps
import com.tk.quicksearch.search.data.preferences.UiPreferences
import com.tk.quicksearch.search.startup.StartupSurfaceSnapshot
import com.tk.quicksearch.search.startup.StartupSurfaceStore
import com.tk.quicksearch.searchEngines.getId
import com.tk.quicksearch.shared.util.isLowRamDevice
import com.tk.quicksearch.app.startup.StartupTrace

internal data class SearchViewModelInitialState(
    val instantStartupSurfaceEnabled: Boolean,
    val startupSnapshot: StartupSurfaceSnapshot?,
    val resultsState: SearchResultsState,
    val featureState: SearchFeatureState,
    val configState: SearchUiConfigState,
)

internal object SearchViewModelInitialStateFactory {
    fun create(
        appContext: Context,
        startupPreferencesReader: UserAppPreferences,
        startupSurfaceStore: StartupSurfaceStore,
        inMemoryRetainedQuery: String,
    ): SearchViewModelInitialState {
        val instantStartupSurfaceEnabled = startupPreferencesReader.isInstantStartupSurfaceEnabled()
        val startupSnapshot =
            if (instantStartupSurfaceEnabled) {
                startupSurfaceStore.loadSnapshot()?.let { snapshot ->
                    val availableSuggestions =
                        filterAvailableStartupApps(
                            context = appContext,
                            apps = snapshot.suggestedApps,
                        )
                    snapshot.copy(
                        suggestedApps =
                            applyRecentLaunchOrderToStartupSuggestions(
                                apps = availableSuggestions,
                                pinnedAppKeys = startupPreferencesReader.getPinnedPackages(),
                                recentLaunchKeys = startupPreferencesReader.getRecentAppLaunches(),
                            ),
                    )
                }
            } else {
                null
            }
        if (startupSnapshot != null) {
            StartupTrace.mark("QS.Home.StartupSnapshotAvailable")
        }

        val initialBackgroundSource = startupPreferencesReader.getBackgroundSource()
        val initialCustomImageUri = startupPreferencesReader.getCustomImageUri()
        val initialAppThemeMode = startupPreferencesReader.getAppThemeMode()
        val initialIsDarkMode =
            when (initialAppThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> {
                    val nightModeFlags =
                        appContext.resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }

        val initialPreviewPath =
            startupSnapshot?.startupBackgroundPreviewPath?.takeIf { snapshotPath ->
                startupSnapshot.backgroundSource == initialBackgroundSource &&
                    (initialBackgroundSource != BackgroundSource.CUSTOM_IMAGE ||
                        startupSnapshot.customImageUri == initialCustomImageUri) &&
                    !snapshotPath.isNullOrBlank()
            }

        val clearQueryOnLaunch = startupPreferencesReader.isClearQueryOnLaunchEnabled()
        val hasCachedEnabledSearchTargets =
            startupSnapshot?.let { snapshot ->
                snapshot.searchTargetsOrder.any { target ->
                    target.getId() !in snapshot.disabledSearchTargetIds
                }
            } == true

        val initialResultsState =
            SearchResultsState(
                query = if (clearQueryOnLaunch) "" else inMemoryRetainedQuery,
                recentApps = startupSnapshot?.suggestedApps.orEmpty(),
                pinnedNonAppItemOrder = startupPreferencesReader.getPinnedNonAppItemOrder(),
                indexedAppCount = startupSnapshot?.suggestedApps?.size ?: 0,
                searchEnginesState =
                    if (startupSnapshot?.isSearchEngineCompactMode == true && hasCachedEnabledSearchTargets) {
                        SearchEnginesVisibility.Compact
                    } else {
                        SearchEnginesVisibility.Hidden
                    },
            )

        // Seeded from preferences so fuzzy matching never runs against the enabled-by-default
        // value during the window before startup phase 2 hydrates the feature state.
        val isLowRamDevice = isLowRamDevice(appContext)

        val initialFeatureState =
            SearchFeatureState(
                searchTargetsOrder = startupSnapshot?.searchTargetsOrder.orEmpty(),
                disabledSearchTargetIds = startupSnapshot?.disabledSearchTargetIds.orEmpty(),
                isSearchEngineCompactMode =
                    startupSnapshot?.isSearchEngineCompactMode == true && hasCachedEnabledSearchTargets,
                searchEngineCompactRowCount =
                    startupSnapshot?.searchEngineCompactRowCount?.coerceIn(1, 2) ?: 1,
                fuzzySearchEnabled =
                    !isLowRamDevice && startupPreferencesReader.isFuzzySearchEnabled(),
                fuzzySearchAvailable = !isLowRamDevice,
                secondaryRankingSignal = startupPreferencesReader.getSecondaryRankingSignal(),
                isSearchEngineAliasSuffixEnabled =
                    startupPreferencesReader.isSearchEngineAliasSuffixEnabled(),
                isAliasTriggerAfterSpaceEnabled =
                    startupPreferencesReader.isAliasTriggerAfterSpaceEnabled(),
                showTodayEvents = startupPreferencesReader.getShowTodayEvents(),
                topMatchesEnabled = startupPreferencesReader.isTopMatchesEnabled(),
                topMatchesLimit = startupPreferencesReader.getTopMatchesLimit(),
                topMatchesSectionOrder = startupPreferencesReader.getTopMatchesSectionOrder(),
                disabledTopMatchesSections = startupPreferencesReader.getDisabledTopMatchesSections(),
                showRateQuickSearchCard = startupPreferencesReader.shouldShowRateQuickSearchCard(),
            )

        val initialConfigState =
            SearchUiConfigState(
                startupPhase = StartupPhase.PHASE_1_CACHE_PREFS,
                isInitializing = true,
                isLoading = true,
                isStartupCoreSurfaceReady = startupSnapshot != null,
                showWallpaperBackground =
                    startupSnapshot?.showWallpaperBackground
                        ?: initialBackgroundSource != BackgroundSource.THEME,
                wallpaperBackgroundAlpha =
                    startupSnapshot?.wallpaperBackgroundAlpha
                        ?: startupPreferencesReader.getWallpaperBackgroundAlpha(initialIsDarkMode),
                wallpaperBlurRadius =
                    startupSnapshot?.wallpaperBlurRadius
                        ?: startupPreferencesReader.getWallpaperBlurRadius(initialIsDarkMode),
                appTheme = startupSnapshot?.appTheme ?: startupPreferencesReader.getAppTheme(),
                overlayThemeIntensity =
                    (startupSnapshot?.overlayThemeIntensity
                            ?: startupPreferencesReader.getOverlayThemeIntensity())
                        .coerceIn(
                            UiPreferences.MIN_OVERLAY_THEME_INTENSITY,
                            UiPreferences.MAX_OVERLAY_THEME_INTENSITY,
                        ),
                appThemeMode = initialAppThemeMode,
                backgroundSource = initialBackgroundSource,
                customImageUri = initialCustomImageUri,
                startupBackgroundPreviewPath = initialPreviewPath,
                selectedIconPackPackage = startupPreferencesReader.getSelectedIconPackPackage(),
                oneHandedMode =
                    startupSnapshot?.oneHandedMode ?: startupPreferencesReader.isOneHandedMode(),
                bottomSearchBarEnabled = startupPreferencesReader.isBottomSearchBarEnabled(),
                unifiedPinnedItemsEnabled = startupPreferencesReader.isUnifiedPinnedItemsEnabled(),
                searchHintsEnabled = startupPreferencesReader.isSearchHintsEnabled(),
                settingsIconEnabled = startupPreferencesReader.isSettingsIconEnabled(),
                topResultIndicatorEnabled =
                    startupSnapshot?.topResultIndicatorEnabled
                        ?: startupPreferencesReader.isTopResultIndicatorEnabled(),
                openKeyboardOnLaunch = startupPreferencesReader.isOpenKeyboardOnLaunchEnabled(),
                clearQueryOnLaunch = clearQueryOnLaunch,
                autoCloseOverlay = startupPreferencesReader.isAutoCloseOverlayEnabled(),
                fontScaleMultiplier =
                    (startupSnapshot?.fontScaleMultiplier
                            ?: startupPreferencesReader.getFontScaleMultiplier())
                        .coerceIn(
                            UiPreferences.MIN_FONT_SCALE_MULTIPLIER,
                            UiPreferences.MAX_FONT_SCALE_MULTIPLIER,
                        ),
                useSystemFont =
                    startupSnapshot?.useSystemFont ?: startupPreferencesReader.shouldUseSystemFont(),
                launcherAppIcon = startupPreferencesReader.getLauncherAppIcon(),
                showAppLabels =
                    startupSnapshot?.showAppLabels ?: startupPreferencesReader.shouldShowAppLabels(),
                appIconSizeStep =
                    startupSnapshot?.appIconSizeStep
                        ?: startupPreferencesReader.getAppIconSizeStep(),
                appIconShape = startupPreferencesReader.getAppIconShape(),
                themedIconsEnabled = startupPreferencesReader.isThemedIconsEnabled(),
                deviceThemeEnabled = startupPreferencesReader.isDeviceThemeEnabled(),
                maskUnsupportedIconPackIcons =
                    startupPreferencesReader.isIconPackUnsupportedIconMaskEnabled(),
                appSuggestionsEnabled =
                    startupSnapshot?.appSuggestionsEnabled
                        ?: startupPreferencesReader.areAppSuggestionsEnabled(),
                showAllAppsButton = startupPreferencesReader.shouldShowAllAppsButton(),
                includeNonLaunchableAppsInSearch =
                    startupPreferencesReader.shouldIncludeNonLaunchableAppsInSearch(),
                selectedAppSuggestionTab = startupPreferencesReader.getSelectedAppSuggestionTab(),
                enabledAppSuggestionTabs = startupPreferencesReader.getEnabledAppSuggestionTabs(),
                selectRetainedQuery = !clearQueryOnLaunch && inMemoryRetainedQuery.isNotEmpty(),
            )

        return SearchViewModelInitialState(
            instantStartupSurfaceEnabled = instantStartupSurfaceEnabled,
            startupSnapshot = startupSnapshot,
            resultsState = initialResultsState,
            featureState = initialFeatureState,
            configState = initialConfigState,
        )
    }

    internal fun applyRecentLaunchOrderToStartupSuggestions(
        apps: List<com.tk.quicksearch.search.models.AppInfo>,
        pinnedAppKeys: Set<String>,
        recentLaunchKeys: List<String>,
    ): List<com.tk.quicksearch.search.models.AppInfo> {
        if (apps.size < 2 || recentLaunchKeys.isEmpty()) return apps

        val recentRank = recentLaunchKeys.withIndex().associate { (index, key) -> key to index }
        val originalRank = apps.withIndex().associate { (index, app) -> app.launchCountKey() to index }
        val (pinnedApps, recentApps) = apps.partition { it.launchCountKey() in pinnedAppKeys }

        return pinnedApps +
            recentApps.sortedWith(
                compareBy<com.tk.quicksearch.search.models.AppInfo> {
                    recentRank[it.launchCountKey()] ?: Int.MAX_VALUE
                }.thenBy { originalRank[it.launchCountKey()] ?: Int.MAX_VALUE },
            )
    }
}
