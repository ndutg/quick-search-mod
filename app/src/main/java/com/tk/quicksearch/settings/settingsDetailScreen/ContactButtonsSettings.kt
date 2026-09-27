package com.tk.quicksearch.settings.settingsDetailScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tk.quicksearch.R
import com.tk.quicksearch.search.contacts.components.ContactAvatar
import com.tk.quicksearch.search.contacts.components.ContactButtonActionIcon
import com.tk.quicksearch.search.contacts.components.ContactUiConstants
import com.tk.quicksearch.search.contacts.models.ContactButtonAction
import com.tk.quicksearch.settings.shared.SettingsCard
import com.tk.quicksearch.shared.ui.components.AppAlertDialog
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens

/**
 * First/second contact card button settings: a contact card preview, a picker for each button,
 * and a note on the Call/SMS fallback.
 *
 * @param availableActions Actions whose app is installed, in display order
 */
@Composable
internal fun ContactButtonsSettings(
    primaryContactButton: ContactButtonAction,
    secondaryContactButton: ContactButtonAction,
    availableActions: List<ContactButtonAction>,
    onPrimaryContactButtonSelected: (ContactButtonAction) -> Unit,
    onSecondaryContactButtonSelected: (ContactButtonAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // true while picking the first button, false for the second, null when no picker is open
    var pickingPrimaryButton by remember { mutableStateOf<Boolean?>(null) }

    SettingsCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = DesignTokens.CardHorizontalPadding,
                    vertical = DesignTokens.CardVerticalPadding,
                ),
        ) {
            ContactCardPreview(
                primaryContactButton = primaryContactButton,
                secondaryContactButton = secondaryContactButton,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = DesignTokens.SpacingSmall),
                color = AppColors.SettingsDivider,
            )
            ContactButtonPickerRow(
                label = stringResource(R.string.settings_contact_first_button_title),
                action = primaryContactButton,
                onClick = { pickingPrimaryButton = true },
            )
            ContactButtonPickerRow(
                label = stringResource(R.string.settings_contact_second_button_title),
                action = secondaryContactButton,
                onClick = { pickingPrimaryButton = false },
            )
            Text(
                text = stringResource(R.string.settings_contact_buttons_fallback_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    pickingPrimaryButton?.let { isPrimary ->
        ContactButtonPickerDialog(
            title =
                stringResource(
                    if (isPrimary) {
                        R.string.settings_contact_first_button_title
                    } else {
                        R.string.settings_contact_second_button_title
                    },
                ),
            selectedAction = if (isPrimary) primaryContactButton else secondaryContactButton,
            actions = availableActions,
            onActionSelected =
                if (isPrimary) onPrimaryContactButtonSelected else onSecondaryContactButtonSelected,
            onDismiss = { pickingPrimaryButton = null },
        )
    }
}

/** A sample contact row showing the default actions of the two buttons. */
@Composable
private fun ContactCardPreview(
    primaryContactButton: ContactButtonAction,
    secondaryContactButton: ContactButtonAction,
) {
    val displayName = stringResource(R.string.settings_contact_buttons_preview_name)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ContactUiConstants.CONTACT_ROW_MIN_HEIGHT.dp)
                .padding(start = DesignTokens.SpacingXSmall, top = DesignTokens.SpacingXSmall),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            photoUri = null,
            displayName = displayName,
            textStyle = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = displayName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        listOf(primaryContactButton, secondaryContactButton).forEach { action ->
            Box(
                modifier = Modifier.size(ContactUiConstants.ACTION_BUTTON_SIZE.dp),
                contentAlignment = Alignment.Center,
            ) {
                ContactButtonActionIcon(action = action)
            }
        }
    }
}

/** Fixed-size slot so action icons of different sizes line up. */
@Composable
private fun ActionIconSlot(action: ContactButtonAction) {
    Box(
        modifier = Modifier.size(ContactUiConstants.ACTION_ICON_SIZE.dp),
        contentAlignment = Alignment.Center,
    ) {
        ContactButtonActionIcon(action = action)
    }
}

@Composable
private fun ContactButtonPickerRow(
    label: String,
    action: ContactButtonAction,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
    ) {
        ActionIconSlot(action = action)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactButtonPickerDialog(
    title: String,
    selectedAction: ContactButtonAction,
    actions: List<ContactButtonAction>,
    onActionSelected: (ContactButtonAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedBackgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(text = title) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(actions) { action ->
                    val isSelected = action == selectedAction
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(if (isSelected) selectedBackgroundColor else Color.Transparent)
                                .clickable {
                                    onActionSelected(action)
                                    onDismiss()
                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingMedium),
                    ) {
                        ActionIconSlot(action = action)
                        Text(
                            text = stringResource(action.labelRes),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.desc_selected),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_close))
            }
        },
    )
}
