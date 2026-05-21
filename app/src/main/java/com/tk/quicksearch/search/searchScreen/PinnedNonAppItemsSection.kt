package com.tk.quicksearch.search.searchScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.tk.quicksearch.search.core.CallingApp
import com.tk.quicksearch.search.core.MessagingApp
import com.tk.quicksearch.search.appShortcuts.AppShortcutResultsSection
import com.tk.quicksearch.search.calendar.CalendarEventsSection
import com.tk.quicksearch.search.contacts.ContactResultsSection
import com.tk.quicksearch.search.data.AppShortcutRepository.StaticShortcut
import com.tk.quicksearch.search.data.AppShortcutRepository.shortcutKey
import com.tk.quicksearch.search.deviceSettings.DeviceSettingsResultsSection
import com.tk.quicksearch.search.deviceSettings.DeviceSetting
import com.tk.quicksearch.search.files.FileResultsSection
import com.tk.quicksearch.search.models.CalendarEventInfo
import com.tk.quicksearch.search.models.ContactInfo
import com.tk.quicksearch.search.models.DeviceFile
import com.tk.quicksearch.search.models.NoteInfo
import com.tk.quicksearch.search.notes.NotesResultsSection
import com.tk.quicksearch.shared.ui.theme.DesignTokens

@Composable
internal fun PinnedNonAppItemsSection(
    pinnedItemOrder: List<String>,
    contacts: List<ContactInfo>,
    files: List<DeviceFile>,
    appShortcuts: List<StaticShortcut>,
    settings: List<DeviceSetting>,
    calendarEvents: List<CalendarEventInfo>,
    notes: List<NoteInfo>,
    contactsParams: ContactsSectionParams,
    filesParams: FilesSectionParams,
    appShortcutsParams: AppShortcutsSectionParams,
    settingsParams: SettingsSectionParams,
    calendarParams: CalendarSectionParams,
    notesParams: NotesSectionParams,
    showWallpaperBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val orderedItems =
        remember(
            pinnedItemOrder,
            contacts,
            files,
            appShortcuts,
            settings,
            calendarEvents,
            notes,
        ) {
            orderedPinnedNonAppItems(
                pinnedItemOrder = pinnedItemOrder,
                contacts = contacts,
                files = files,
                appShortcuts = appShortcuts,
                settings = settings,
                calendarEvents = calendarEvents,
                notes = notes,
            )
        }

    if (orderedItems.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
    ) {
        orderedItems.forEach { item ->
            when (item) {
                is PinnedNonAppItem.AppShortcut ->
                    AppShortcutResultsSection(
                        shortcuts = listOf(item.shortcut),
                        isExpanded = true,
                        pinnedShortcutIds = appShortcutsParams.pinnedShortcutIds,
                        excludedShortcutIds = appShortcutsParams.excludedShortcutIds,
                        onShortcutClick = appShortcutsParams.onShortcutClick,
                        onTogglePin = appShortcutsParams.onTogglePin,
                        onMovePinned = appShortcutsParams.onMovePinned,
                        onExclude = appShortcutsParams.onExclude,
                        onInclude = appShortcutsParams.onInclude,
                        onAppInfoClick = appShortcutsParams.onAppInfoClick,
                        onNicknameClick = appShortcutsParams.onNicknameClick,
                        onTriggerClick = appShortcutsParams.onTriggerClick,
                        onEditCustomShortcut = appShortcutsParams.onEditCustomShortcut,
                        onEditShortcutIcon = appShortcutsParams.onEditShortcutIcon,
                        getShortcutNickname = appShortcutsParams.getShortcutNickname,
                        getShortcutTrigger = appShortcutsParams.getShortcutTrigger,
                        showAllResults = true,
                        showExpandControls = false,
                        onExpandClick = appShortcutsParams.onExpandClick,
                        expandedCardMaxHeight = appShortcutsParams.expandedCardMaxHeight,
                        iconPackPackage = appShortcutsParams.iconPackPackage,
                        showWallpaperBackground = showWallpaperBackground,
                        predictedTarget = null,
                        fillExpandedHeight = false,
                        showPinnedItemMenu = true,
                    )

                is PinnedNonAppItem.Contact ->
                    ContactResultsSection(
                        hasPermission = contactsParams.hasPermission,
                        contacts = listOf(item.contact),
                        isExpanded = true,
                        callingApp = contactsParams.callingApp ?: CallingApp.CALL,
                        messagingApp = contactsParams.messagingApp ?: MessagingApp.MESSAGES,
                        onContactClick = contactsParams.onContactClick,
                        onShowContactMethods = contactsParams.onShowContactMethods,
                        onCallContact = contactsParams.onCallContact,
                        onSmsContact = contactsParams.onSmsContact,
                        onContactMethodClick = contactsParams.onContactMethodClick,
                        pinnedContactIds = contactsParams.pinnedContactIds,
                        onTogglePin = contactsParams.onTogglePin,
                        onMovePinned = contactsParams.onMovePinned,
                        onExclude = contactsParams.onExclude,
                        onNicknameClick = contactsParams.onNicknameClick,
                        onTriggerClick = contactsParams.onTriggerClick,
                        getContactNickname = contactsParams.getContactNickname,
                        getContactTrigger = contactsParams.getContactTrigger,
                        getPrimaryContactCardAction = contactsParams.getPrimaryContactCardAction,
                        getSecondaryContactCardAction = contactsParams.getSecondaryContactCardAction,
                        onPrimaryActionLongPress = contactsParams.onPrimaryActionLongPress,
                        onSecondaryActionLongPress = contactsParams.onSecondaryActionLongPress,
                        onCustomAction = contactsParams.onCustomAction,
                        onOpenAppSettings = contactsParams.onOpenAppSettings,
                        showAllResults = true,
                        showExpandControls = false,
                        onExpandClick = contactsParams.onExpandClick,
                        expandedCardMaxHeight = contactsParams.expandedCardMaxHeight,
                        showContactActionHint = false,
                        onContactActionHintDismissed = contactsParams.onContactActionHintDismissed,
                        permissionDisabledCard = contactsParams.permissionDisabledCard,
                        showWallpaperBackground = showWallpaperBackground,
                        predictedTarget = null,
                        fillExpandedHeight = false,
                        showPinnedItemMenu = true,
                    )

                is PinnedNonAppItem.File ->
                    FileResultsSection(
                        hasPermission = filesParams.hasPermission,
                        files = listOf(item.file),
                        isExpanded = true,
                        onFileClick = filesParams.onFileClick,
                        onOpenFolder = filesParams.onOpenFolder,
                        onRequestPermission = filesParams.onRequestPermission,
                        pinnedFileUris = filesParams.pinnedFileUris,
                        onTogglePin = filesParams.onTogglePin,
                        onMovePinned = filesParams.onMovePinned,
                        onExclude = filesParams.onExclude,
                        onExcludeExtension = filesParams.onExcludeExtension,
                        onNicknameClick = filesParams.onNicknameClick,
                        onTriggerClick = filesParams.onTriggerClick,
                        getFileNickname = filesParams.getFileNickname,
                        getFileTrigger = filesParams.getFileTrigger,
                        showAllResults = true,
                        showExpandControls = false,
                        onExpandClick = filesParams.onExpandClick,
                        expandedCardMaxHeight = filesParams.expandedCardMaxHeight,
                        permissionDisabledCard = filesParams.permissionDisabledCard,
                        showWallpaperBackground = showWallpaperBackground,
                        predictedTarget = null,
                        fillExpandedHeight = false,
                        showPinnedItemMenu = true,
                    )

                is PinnedNonAppItem.Setting ->
                    DeviceSettingsResultsSection(
                        settings = listOf(item.setting),
                        isExpanded = true,
                        pinnedSettingIds = settingsParams.pinnedSettingIds,
                        onSettingClick = settingsParams.onSettingClick,
                        onTogglePin = settingsParams.onTogglePin,
                        onMovePinned = settingsParams.onMovePinned,
                        onExclude = settingsParams.onExclude,
                        onNicknameClick = settingsParams.onNicknameClick,
                        onTriggerClick = settingsParams.onTriggerClick,
                        getSettingNickname = settingsParams.getSettingNickname,
                        getSettingTrigger = settingsParams.getSettingTrigger,
                        showAllResults = true,
                        showExpandControls = false,
                        onExpandClick = settingsParams.onExpandClick,
                        expandedCardMaxHeight = settingsParams.expandedCardMaxHeight,
                        showWallpaperBackground = showWallpaperBackground,
                        predictedTarget = null,
                        fillExpandedHeight = false,
                        showPinnedItemMenu = true,
                    )

                is PinnedNonAppItem.CalendarEvent ->
                    CalendarEventsSection(
                        events = listOf(item.event),
                        hasPermission = calendarParams.hasPermission,
                        isExpanded = true,
                        pinnedEventIds = calendarParams.pinnedEventIds,
                        excludedEventIds = calendarParams.excludedEventIds,
                        onEventClick = calendarParams.onEventClick,
                        onRequestPermission = calendarParams.onRequestPermission,
                        onTogglePin = calendarParams.onTogglePin,
                        onMovePinned = calendarParams.onMovePinned,
                        onExclude = calendarParams.onExclude,
                        onInclude = calendarParams.onInclude,
                        onNicknameClick = calendarParams.onNicknameClick,
                        onArchiveTodayEvent = calendarParams.onArchiveTodayEvent,
                        getEventNickname = calendarParams.getEventNickname,
                        showAllResults = true,
                        showExpandControls = false,
                        onExpandClick = calendarParams.onExpandClick,
                        expandedCardMaxHeight = calendarParams.expandedCardMaxHeight,
                        permissionDisabledCard = calendarParams.permissionDisabledCard,
                        showWallpaperBackground = showWallpaperBackground,
                        predictedTarget = null,
                        fillExpandedHeight = false,
                        isHomeScreenMode = true,
                        showPinnedItemMenu = true,
                    )

                is PinnedNonAppItem.Note ->
                    NotesResultsSection(
                        notes = listOf(item.note),
                        pinnedNoteIds = notesParams.pinnedNoteIds,
                        onNoteClick = notesParams.onNoteClick,
                        onTogglePin = notesParams.onTogglePin,
                        onMovePinned = notesParams.onMovePinned,
                        onDelete = notesParams.onDelete,
                        onTriggerClick = notesParams.onTriggerClick,
                        getNoteTrigger = notesParams.getNoteTrigger,
                        isExpanded = true,
                        showAllResults = true,
                        showExpandControls = false,
                        onExpandClick = notesParams.onExpandClick,
                        expandedCardMaxHeight = notesParams.expandedCardMaxHeight,
                        showWallpaperBackground = showWallpaperBackground,
                        predictedTarget = null,
                        fillExpandedHeight = false,
                        showPinnedItemMenu = true,
                    )
            }
        }
    }
}

private sealed class PinnedNonAppItem(
    val key: String,
) {
    class AppShortcut(val shortcut: StaticShortcut) : PinnedNonAppItem("shortcut:${shortcutKey(shortcut)}")
    class Contact(val contact: ContactInfo) : PinnedNonAppItem("contact:${contact.contactId}")
    class File(val file: DeviceFile) : PinnedNonAppItem("file:${file.uri}")
    class Setting(val setting: DeviceSetting) : PinnedNonAppItem("setting:${setting.id}")
    class CalendarEvent(val event: CalendarEventInfo) : PinnedNonAppItem("calendar:${event.eventId}")
    class Note(val note: NoteInfo) : PinnedNonAppItem("note:${note.noteId}")
}

private fun orderedPinnedNonAppItems(
    pinnedItemOrder: List<String>,
    contacts: List<ContactInfo>,
    files: List<DeviceFile>,
    appShortcuts: List<StaticShortcut>,
    settings: List<DeviceSetting>,
    calendarEvents: List<CalendarEventInfo>,
    notes: List<NoteInfo>,
): List<PinnedNonAppItem> {
    val defaultItems =
        buildList {
            addAll(appShortcuts.map { PinnedNonAppItem.AppShortcut(it) })
            addAll(contacts.map { PinnedNonAppItem.Contact(it) })
            addAll(files.map { PinnedNonAppItem.File(it) })
            addAll(calendarEvents.map { PinnedNonAppItem.CalendarEvent(it) })
            addAll(settings.map { PinnedNonAppItem.Setting(it) })
            addAll(notes.map { PinnedNonAppItem.Note(it) })
        }
    if (defaultItems.isEmpty()) return emptyList()

    val itemByKey = defaultItems.associateBy { it.key }
    val orderedKeys = pinnedItemOrder.filter { it in itemByKey }
    val missingKeys = defaultItems.map { it.key }.filterNot { it in orderedKeys }
    return (orderedKeys + missingKeys).mapNotNull(itemByKey::get)
}
