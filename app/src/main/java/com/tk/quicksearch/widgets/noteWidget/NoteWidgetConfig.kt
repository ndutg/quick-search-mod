package com.tk.quicksearch.widgets.noteWidget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.NotesRepository
import com.tk.quicksearch.search.data.notes.NotesRoomStore
import com.tk.quicksearch.search.models.NoteInfo
import com.tk.quicksearch.search.notes.NotesTextUtils
import com.tk.quicksearch.settings.shared.SettingsCard
import com.tk.quicksearch.settings.shared.SettingsManagementSearchBar
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import com.tk.quicksearch.widgets.utils.WidgetConfigConstants
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PICKER_PREVIEW_LINES = 2

/** The note with [noteId], or null while it loads or when there is none. */
@Composable
internal fun rememberWidgetNote(noteId: Long?): NoteInfo? {
    val context = LocalContext.current
    val note by produceState<NoteInfo?>(initialValue = null, noteId) {
        value = noteId?.let { id -> withContext(Dispatchers.IO) { NotesRoomStore(context).getById(id) } }
    }
    return note
}

/** Full-screen list of the user's notes with a search bar, shown when a Note widget is placed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteWidgetPickerScreen(
    selectedNoteId: Long?,
    onSelect: (NoteInfo) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val notes by produceState<List<NoteInfo>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { NotesRepository(context).getAllNotes() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val locale = Locale.getDefault()
    val filteredNotes =
        remember(notes, query, locale) {
            val normalizedQuery = query.trim().lowercase(locale)
            notes.orEmpty().filter { note ->
                normalizedQuery.isEmpty() ||
                    note.title.lowercase(locale).contains(normalizedQuery) ||
                    note.markdownContent.lowercase(locale).contains(normalizedQuery)
            }
        }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.widget_note_picker_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.dialog_cancel),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .padding(
                        start = WidgetConfigConstants.HORIZONTAL_PADDING,
                        end = WidgetConfigConstants.HORIZONTAL_PADDING,
                        bottom = DesignTokens.SpacingLarge,
                    ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingLarge),
        ) {
            SettingsManagementSearchBar(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
                applyDefaultPadding = false,
                applyImePadding = false,
                placeholder = stringResource(R.string.search_hint_notes),
            )
            when {
                notes == null -> Unit
                filteredNotes.isEmpty() ->
                    Text(
                        text = stringResource(R.string.widget_custom_buttons_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                else ->
                    // fill = false lets a short list wrap its card instead of stretching it.
                    SettingsCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        LazyColumn {
                            itemsIndexed(items = filteredNotes, key = { _, note -> note.noteId }) { index, note ->
                                NoteRow(
                                    note = note,
                                    trailingIcon = Icons.Rounded.Check.takeIf { note.noteId == selectedNoteId },
                                    trailingIconDescription = stringResource(R.string.desc_selected),
                                    onClick = { onSelect(note) },
                                )
                                if (index < filteredNotes.lastIndex) {
                                    HorizontalDivider(color = AppColors.SettingsDivider)
                                }
                            }
                        }
                    }
            }
        }
    }
}

/** The widget's current note in the configure screen; tapping it reopens the picker. */
@Composable
fun NoteWidgetSelectedNoteSection(
    noteId: Long?,
    onChangeNote: () -> Unit,
) {
    val note = rememberWidgetNote(noteId)
    Column(verticalArrangement = Arrangement.spacedBy(WidgetConfigConstants.COLOR_SECTION_SPACING)) {
        Text(
            text = stringResource(R.string.notification_pinned_item_type_note),
            style = MaterialTheme.typography.titleSmall,
        )
        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            if (note != null) {
                NoteRow(
                    note = note,
                    trailingIcon = Icons.Rounded.ChevronRight,
                    trailingIconDescription = null,
                    onClick = onChangeNote,
                    showLeadingIcon = true,
                )
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteInfo,
    trailingIcon: ImageVector?,
    trailingIconDescription: String?,
    onClick: () -> Unit,
    showLeadingIcon: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    PaddingValues(
                        horizontal = DesignTokens.CardHorizontalPadding,
                        vertical = DesignTokens.CardVerticalPadding,
                    ),
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
    ) {
        if (showLeadingIcon) {
            Icon(
                painter = painterResource(R.drawable.ic_widget_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(DesignTokens.IconSizeSmall),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingXXSmall),
        ) {
            Text(
                text = note.title.ifBlank { stringResource(R.string.notes_untitled) },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    NotesTextUtils
                        .firstLinesPreview(note.markdownContent, PICKER_PREVIEW_LINES)
                        .ifBlank { stringResource(R.string.notes_empty_note_subtext) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = PICKER_PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = trailingIconDescription,
                tint =
                    if (it == Icons.Rounded.Check) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}
