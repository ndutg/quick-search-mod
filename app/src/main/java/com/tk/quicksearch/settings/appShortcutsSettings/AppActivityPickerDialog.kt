package com.tk.quicksearch.settings.appShortcutsSettings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.tk.quicksearch.R
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerLeadingSize
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerIconBadge
import com.tk.quicksearch.shared.ui.components.AppPickerDrawer
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerRowSpacing
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerMessage
import com.tk.quicksearch.shared.ui.components.AppPickerDrawerRow

@Composable
fun AppActivityPickerDialog(
    activities: List<AppActivitySource>,
    onDismiss: () -> Unit,
    onActivitySelected: (AppActivitySource) -> Unit,
) {
    AppPickerDrawer(
        title = stringResource(R.string.settings_app_shortcuts_activity_dialog_title),
        onDismiss = onDismiss,
        header =
            if (activities.isNotEmpty()) {
                {
                    Text(
                        text = stringResource(R.string.settings_app_shortcuts_activity_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
    ) {
        if (activities.isEmpty()) {
            AppPickerDrawerMessage(text = stringResource(R.string.settings_app_shortcuts_activity_empty))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AppPickerDrawerRowSpacing)) {
                itemsIndexed(items = activities, key = { _, activity -> activity.className }) { _, activity ->
                    AppActivityRow(
                        activity = activity,
                        onClick = { onActivitySelected(activity) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppActivityRow(
    activity: AppActivitySource,
    onClick: () -> Unit,
) {
    AppPickerDrawerRow(
        title = activity.label,
        subtitle = activity.details,
        titleMaxLines = 1,
        onClick = onClick,
        leading = {
            if (activity.icon != null) {
                Image(
                    bitmap = activity.icon,
                    contentDescription = activity.label,
                    modifier = Modifier.size(AppPickerDrawerLeadingSize),
                    contentScale = ContentScale.Fit,
                )
            } else {
                AppPickerDrawerIconBadge(icon = Icons.Rounded.Android)
            }
        },
    )
}
