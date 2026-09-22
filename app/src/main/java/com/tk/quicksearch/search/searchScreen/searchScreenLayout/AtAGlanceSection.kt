package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.reminders.ReminderEditorRequests
import com.tk.quicksearch.search.searchScreen.LocalOverlayDividerColor
import com.tk.quicksearch.search.searchScreen.shared.SearchResultCard
import com.tk.quicksearch.shared.ui.theme.homeTextColor
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens

/**
 * One row of the home At a Glance card. Today's calendar events are hosted by the calendar card
 * itself; every other glanceable source (low battery, media, alarm, reminders, and future ones) contributes rows here.
 * Rows sit inside the card's inset and follow CalendarEventRow: 7dp before a 24dp icon, then 12dp
 * to the text.
 */
internal class AtAGlanceItem(
    val key: String,
    val content: @Composable () -> Unit,
)

/**
 * Collects the non-calendar At a Glance rows, top to bottom. In the [reversed] (bottom search bar)
 * layout the source groups swap places but rows within a group keep their order, matching how the
 * calendar card never reverses its own events.
 */
@Composable
internal fun rememberAtAGlanceItems(
    enabled: Boolean,
    reversed: Boolean,
): List<AtAGlanceItem> {
    val lowBattery = rememberLowBatteryGlance(enabled)
    val alarm = rememberUpcomingAlarmGlance(enabled)
    val reminders = rememberUpcomingRemindersGlance(enabled)
    val groups =
        listOf(
            listOfNotNull(lowBattery?.let { AtAGlanceItem(key = "low-battery") { LowBatteryRow(it) } }),
            listOfNotNull(alarm?.let { AtAGlanceItem(key = "alarm") { UpcomingAlarmRow(it) } }),
            reminders.reminders.map { reminder ->
                AtAGlanceItem(key = "reminder-${reminder.reminderId}") {
                    UpcomingReminderRow(
                        reminder = reminder,
                        nowMillis = reminders.nowMillis,
                        onClick = { ReminderEditorRequests.openEdit(reminder) },
                        onDone = { reminders.markDone(reminder) },
                        onDismiss = { reminders.dismiss(reminder) },
                        onDelete = { reminders.delete(reminder) },
                    )
                }
            },
        )
    return (if (reversed) groups.asReversed() else groups).flatten()
}

@Composable
internal fun atAGlanceDividerColor(showWallpaperBackground: Boolean) =
    LocalOverlayDividerColor.current
        ?: if (showWallpaperBackground) AppColors.WallpaperDivider else MaterialTheme.colorScheme.outlineVariant

/** Renders [items] separated by dividers, optionally with a divider before or after the group. */
@Composable
internal fun AtAGlanceRows(
    items: List<AtAGlanceItem>,
    showWallpaperBackground: Boolean,
    dividerBefore: Boolean = false,
    dividerAfter: Boolean = false,
) {
    if (items.isEmpty()) return
    val dividerColor = atAGlanceDividerColor(showWallpaperBackground)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (dividerBefore) HorizontalDivider(color = dividerColor)
        items.forEachIndexed { index, item ->
            key(item.key) { item.content() }
            if (index < items.lastIndex) HorizontalDivider(color = dividerColor)
        }
        if (dividerAfter) HorizontalDivider(color = dividerColor)
    }
}

@Composable
internal fun AtAGlanceTitle() {
    Text(
        text = stringResource(R.string.settings_at_a_glance_title),
        style = MaterialTheme.typography.titleSmall,
        color = homeTextColor(),
        modifier = Modifier.padding(horizontal = DesignTokens.SpacingLarge),
    )
}

/**
 * Card chrome for At a Glance rows outside the calendar card, with the same inset the calendar card
 * gives its event rows so the rows look the same wherever they are hosted.
 */
@Composable
internal fun AtAGlanceCardShell(
    showWallpaperBackground: Boolean,
    content: @Composable () -> Unit,
) {
    SearchResultCard(
        modifier = Modifier.fillMaxWidth(),
        showWallpaperBackground = showWallpaperBackground,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = DesignTokens.SpacingMedium, vertical = 4.dp),
        ) {
            content()
        }
    }
}

/**
 * The At a Glance card when there are no today's events to host the rows. Like the calendar card,
 * the title and card are emitted into the caller's column so they share its section spacing.
 */
@Composable
internal fun AtAGlanceCard(
    items: List<AtAGlanceItem>,
    showWallpaperBackground: Boolean,
    showTitle: Boolean,
) {
    if (items.isEmpty()) return
    if (showTitle) AtAGlanceTitle()
    AtAGlanceCardShell(showWallpaperBackground = showWallpaperBackground) {
        AtAGlanceRows(items = items, showWallpaperBackground = showWallpaperBackground)
    }
}

/** Media controls sit in their own card under the At a Glance title, apart from the other rows. */
@Composable
internal fun NowPlayingCard(
    glance: NowPlayingGlance,
    showWallpaperBackground: Boolean,
) {
    AtAGlanceCardShell(showWallpaperBackground = showWallpaperBackground) {
        NowPlayingRow(glance)
    }
}
