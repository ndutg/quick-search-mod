package com.tk.quicksearch.settings.settingsDetailScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.tk.quicksearch.R
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerIconBadge
import com.tk.quicksearch.search.data.FileSearchRepository
import com.tk.quicksearch.search.models.DeviceFile
import com.tk.quicksearch.shared.ui.components.AppPickerDrawer
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerRowSpacing
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerMessage
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerRow
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bottom sheet for searching device folders to add to the whitelist or blacklist. */
@Composable
internal fun FolderSearchSheet(
    title: String,
    selectedPatterns: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (DeviceFile) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { FileSearchRepository(context) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var results by remember { mutableStateOf<List<DeviceFile>>(emptyList()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(query.text) {
        val trimmedQuery = query.text.trim()
        results =
            if (trimmedQuery.isBlank()) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    repository.searchFolders(trimmedQuery, limit = 30)
                        .distinctBy { folder ->
                            normalizePathFilterPattern(folderDisplayPath(folder))
                        }
                }
            }
    }

    AppPickerDrawer(
        title = title,
        onDismiss = onDismiss,
        search = AppPickerDrawerSearch(query = query, onQueryChange = { query = it }, autoFocus = true),
        dismissOnClickOutside = false,
    ) {
        when {
            query.text.isBlank() ->
                AppPickerDrawerMessage(
                    text = stringResource(R.string.settings_folder_search_hint),
                    centered = true,
                )

            results.isEmpty() ->
                AppPickerDrawerMessage(text = stringResource(R.string.settings_folder_search_no_results))

            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AppPickerDrawerRowSpacing)) {
                    itemsIndexed(results) { _, folder ->
                        val pattern = normalizePathFilterPattern(folderDisplayPath(folder))
                        FolderSearchResultRow(
                            folder = folder,
                            alreadySelected = pattern in selectedPatterns,
                            onSelect = {
                                keyboardController?.hide()
                                onSelect(folder)
                            },
                        )
                    }
                }
        }
    }
}

@Composable
private fun FolderSearchResultRow(
    folder: DeviceFile,
    alreadySelected: Boolean,
    onSelect: () -> Unit,
) {
    AppPickerDrawerRow(
        title = folder.displayName,
        subtitle = displayPathWithLeadingSlash(folderDisplayPath(folder)),
        titleMaxLines = 1,
        enabled = !alreadySelected,
        selected = alreadySelected,
        onClick = onSelect,
        leading = { AppPickerDrawerIconBadge(icon = Icons.Rounded.Folder) },
    )
}
