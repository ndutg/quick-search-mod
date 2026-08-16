package com.tk.quicksearch.search.searchScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tk.quicksearch.search.appSettings.AppSettingResult
import com.tk.quicksearch.search.core.SectionRenderContext
import com.tk.quicksearch.search.data.AppShortcutRepository.StaticShortcut
import com.tk.quicksearch.search.deviceSettings.DeviceSetting
import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.search.models.CalendarEventInfo
import com.tk.quicksearch.search.models.ContactInfo
import com.tk.quicksearch.search.models.DeviceFile
import com.tk.quicksearch.search.models.NoteInfo
import com.tk.quicksearch.search.searchScreen.searchScreenLayout.SectionRenderingState

internal class SettledSearchResultsBuffer<T>(private val emptyValue: T) {
    private var settledQuery = ""
    private var settledValue = emptyValue

    fun displayedValue(
        query: String,
        currentValue: T,
        isSearchRefreshing: Boolean,
    ): T {
        if (query.isBlank()) {
            settledQuery = ""
            settledValue = emptyValue
        } else if (!isSearchRefreshing) {
            settledQuery = query
            settledValue = currentValue
        }

        return if (isSearchRefreshing && settledQuery.isBlank()) emptyValue else settledValue
    }
}

@Composable
internal fun rememberSettledTopMatches(
    query: String,
    currentMatches: List<TopMatchItem>,
    isSearchRefreshing: Boolean,
): List<TopMatchItem> =
    remember { SettledSearchResultsBuffer(emptyList<TopMatchItem>()) }
        .displayedValue(
            query = query,
            currentValue = currentMatches,
            isSearchRefreshing = isSearchRefreshing,
        )

private data class RegularSearchResultsSnapshot(
    val apps: List<AppInfo> = emptyList(),
    val appShortcuts: List<StaticShortcut> = emptyList(),
    val contacts: List<ContactInfo> = emptyList(),
    val files: List<DeviceFile> = emptyList(),
    val settings: List<DeviceSetting> = emptyList(),
    val appSettings: List<AppSettingResult> = emptyList(),
    val calendarEvents: List<CalendarEventInfo> = emptyList(),
    val notes: List<NoteInfo> = emptyList(),
)

@Composable
internal fun rememberSettledRegularSearchRenderingState(
    query: String,
    currentState: SectionRenderingState,
    isSearchRefreshing: Boolean,
): SectionRenderingState {
    val currentSnapshot =
        RegularSearchResultsSnapshot(
            apps = currentState.displayApps,
            appShortcuts = currentState.appShortcutResults,
            contacts = currentState.contactResults,
            files = currentState.fileResults,
            settings = currentState.settingResults,
            appSettings = currentState.appSettingResults,
            calendarEvents = currentState.calendarEvents,
            notes = currentState.noteResults,
        )
    val displayedSnapshot =
        remember { SettledSearchResultsBuffer(RegularSearchResultsSnapshot()) }
            .displayedValue(
                query = query,
                currentValue = currentSnapshot,
                isSearchRefreshing = isSearchRefreshing,
            )

    if (query.isBlank()) return currentState

    return currentState.copy(
        hasAppResults = displayedSnapshot.apps.isNotEmpty(),
        hasAppShortcutResults = displayedSnapshot.appShortcuts.isNotEmpty(),
        hasContactResults = displayedSnapshot.contacts.isNotEmpty(),
        hasFileResults = displayedSnapshot.files.isNotEmpty(),
        hasSettingResults = displayedSnapshot.settings.isNotEmpty(),
        hasAppSettingResults = displayedSnapshot.appSettings.isNotEmpty(),
        hasCalendarResults = displayedSnapshot.calendarEvents.isNotEmpty(),
        hasNoteResults = displayedSnapshot.notes.isNotEmpty(),
        displayApps = displayedSnapshot.apps,
        appShortcutResults = displayedSnapshot.appShortcuts,
        contactResults = displayedSnapshot.contacts,
        fileResults = displayedSnapshot.files,
        settingResults = displayedSnapshot.settings,
        appSettingResults = displayedSnapshot.appSettings,
        calendarEvents = displayedSnapshot.calendarEvents,
        noteResults = displayedSnapshot.notes,
    )
}

@Composable
internal fun rememberSettledRegularSectionContext(
    query: String,
    currentContext: SectionRenderContext,
    isSearchRefreshing: Boolean,
): SectionRenderContext {
    val displayedContext =
        remember { SettledSearchResultsBuffer(SectionRenderContext()) }
            .displayedValue(
                query = query,
                currentValue = currentContext,
                isSearchRefreshing = isSearchRefreshing,
            )

    return if (query.isBlank()) currentContext else displayedContext
}
