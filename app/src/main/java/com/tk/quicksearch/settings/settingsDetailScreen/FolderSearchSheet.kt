package com.tk.quicksearch.settings.settingsDetailScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.FileSearchRepository
import com.tk.quicksearch.search.models.DeviceFile
import com.tk.quicksearch.shared.ui.components.AppBottomSheet
import com.tk.quicksearch.shared.ui.components.dialogTextFieldColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
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
    val focusRequester = remember { FocusRequester() }
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

    AppBottomSheet(
        onDismissRequest = onDismiss,
        swipeToDismissEnabled = false,
        dismissOnClickOutside = false,
        // Show the keyboard only after the open animation settles; bringing it up mid-animation
        // resizes the sheet and makes the slide-in stutter.
        onFullyExpanded = {
            focusRequester.requestFocus()
            keyboardController?.show()
        },
        onDismissStarted = { keyboardController?.hide() },
    ) { dismiss ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(
                        top = DesignTokens.SpacingLarge,
                        start = DesignTokens.ContentHorizontalPadding,
                        end = DesignTokens.ContentHorizontalPadding,
                        bottom = DesignTokens.SpacingLarge,
                    ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        dismiss()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                shape = RoundedCornerShape(50.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.common_search),
                    )
                },
                trailingIcon = {
                    if (query.text.isNotBlank()) {
                        IconButton(onClick = { query = TextFieldValue("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.desc_clear_search),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                colors =
                    dialogTextFieldColors().copy(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                when {
                    query.text.isBlank() -> {
                        Text(
                            text = stringResource(R.string.settings_folder_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(0.75f)
                                    .padding(top = DesignTokens.SpacingSmall),
                        )
                    }

                    results.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.settings_folder_search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        LazyColumn {
                            itemsIndexed(results) { index, folder ->
                                val pattern = normalizePathFilterPattern(folderDisplayPath(folder))
                                FolderSearchResultRow(
                                    folder = folder,
                                    alreadySelected = pattern in selectedPatterns,
                                    onSelect = {
                                        keyboardController?.hide()
                                        onSelect(folder)
                                    },
                                )
                                if (index < results.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = FOLDER_RESULT_DIVIDER_START_INSET),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    }
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
    val path = folderDisplayPath(folder)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FOLDER_RESULT_ROW_MIN_HEIGHT)
                .clip(RoundedCornerShape(DesignTokens.SpacingMedium))
                .clickable(
                    enabled = !alreadySelected,
                    onClick = onSelect,
                )
                .padding(
                    vertical = DesignTokens.SpacingMedium,
                    horizontal = DesignTokens.SpacingMedium,
                ),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingLarge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FOLDER_RESULT_ICON_SIZE),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = displayPathWithLeadingSlash(path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (alreadySelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.desc_selected),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.common_action_add),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val FOLDER_RESULT_ROW_MIN_HEIGHT = 56.dp
private val FOLDER_RESULT_ICON_SIZE = 20.dp

// Row horizontal padding + icon + icon/text spacing, so dividers line up with the labels.
private val FOLDER_RESULT_DIVIDER_START_INSET =
    DesignTokens.SpacingMedium + FOLDER_RESULT_ICON_SIZE + DesignTokens.SpacingLarge
