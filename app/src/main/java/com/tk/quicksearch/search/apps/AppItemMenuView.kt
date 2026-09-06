package com.tk.quicksearch.search.apps

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HorizontalSplit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image as IconImage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PinEnd
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.R
import com.tk.quicksearch.search.core.AppIconShape
import com.tk.quicksearch.search.data.AppShortcutRepository.StaticShortcut
import com.tk.quicksearch.search.data.AppsRepository
import com.tk.quicksearch.search.data.TodayAppUsage
import com.tk.quicksearch.search.data.AppShortcutRepository.rememberShortcutIcon
import com.tk.quicksearch.search.data.AppShortcutRepository.shortcutDisplayName
import com.tk.quicksearch.search.models.AppInfo
import com.tk.quicksearch.pinnedNotifications.PinnedNotifications
import com.tk.quicksearch.search.apps.speedBump.SpeedBump
import com.tk.quicksearch.search.apps.speedBump.SpeedBumpExplainerDialog
import com.tk.quicksearch.shared.ui.components.AppBottomPopup
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import com.tk.quicksearch.shared.util.hapticConfirm
import com.tk.quicksearch.widgets.customButtonsWidget.CustomWidgetButtonAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class AppMenuItemKey {
    PIN,
    NICKNAME,
    ICON,
    TRIGGER,
    SPLIT_SCREEN,
    ADD_TO_HOME,
    APP_INFO,
    SPEED_BUMP,
    EXCLUDE,
    NOTIFICATIONS,
    UNINSTALL,
}

internal data class AppMenuItem(
    val key: AppMenuItemKey,
    val textResId: Int,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    /** Columns this item occupies in the 3-column action grid. */
    val span: Int = 1,
)

private val ShortcutGridIconSize = 32.dp

internal const val ActionGridColumns = 3

/** Items that must never be split across rows, in the order they should appear. */
internal val InseparableActionKeys = listOf(AppMenuItemKey.SPEED_BUMP, AppMenuItemKey.EXCLUDE)

/**
 * Packs action items into 3-column rows.
 *
 * Items keep their declared order unless one does not fit the remaining space, in which case a
 * later item that does fit is pulled forward. That keeps rows full so empty slots only ever
 * appear at the end of the last row. The Speed Bump / Exclude pair is packed as a single unit so
 * Speed Bump always sits directly to the left of Exclude.
 */
internal fun packActionRows(items: List<AppMenuItem>): List<List<AppMenuItem>> {
    val groups: List<List<AppMenuItem>> =
        buildList {
            val pair = InseparableActionKeys.mapNotNull { key -> items.firstOrNull { it.key == key } }
            var pairEmitted = false
            items.forEach { item ->
                if (item.key in InseparableActionKeys) {
                    if (!pairEmitted) {
                        add(pair)
                        pairEmitted = true
                    }
                } else {
                    add(listOf(item))
                }
            }
        }

    val remaining = groups.toMutableList()
    val rows = mutableListOf<List<AppMenuItem>>()
    while (remaining.isNotEmpty()) {
        val row = mutableListOf<AppMenuItem>()
        var used = 0
        while (used < ActionGridColumns) {
            val index =
                remaining.indexOfFirst { group ->
                    group.sumOf { it.span } <= ActionGridColumns - used
                }
            if (index < 0) break
            val group = remaining.removeAt(index)
            row += group
            used += group.sumOf { it.span }
        }
        if (row.isEmpty()) break
        rows += row
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppItemDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isPinned: Boolean,
    showUninstall: Boolean,
    hasNickname: Boolean,
    hasTrigger: Boolean,
    shortcuts: List<StaticShortcut>,
    appInfo: AppInfo,
    iconPackPackage: String?,
    appIconShape: AppIconShape,
    onShortcutClick: (StaticShortcut) -> Unit,
    onAppInfoClick: () -> Unit,
    onHideApp: () -> Unit,
    onPinApp: () -> Unit,
    onUnpinApp: () -> Unit,
    onUninstallClick: () -> Unit,
    onNicknameClick: () -> Unit,
    onTriggerClick: () -> Unit,
    onAddToHome: () -> Unit,
    onOpenInSplitScreen: () -> Unit,
) {
    val context = LocalContext.current
    val todayUsage by produceState<TodayAppUsage?>(
        initialValue = null,
        key1 = expanded,
        key2 = appInfo.packageName,
    ) {
        if (expanded) {
            value = withContext(Dispatchers.IO) {
                AppsRepository(context.applicationContext).getTodayAppUsage(appInfo.packageName)
            }
        }
    }
    val isCurrentApp = appInfo.packageName == context.packageName
    val isLaunchableApp = appInfo.hasLaunchIntent
    val notificationAction =
        CustomWidgetButtonAction.App(
            packageName = appInfo.packageName,
            appName = appInfo.appName,
            userHandleId = appInfo.userHandleId,
        )
    val isPinnedToNotifications = PinnedNotifications.isPinned(context, notificationAction)
    val showIconPicker = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var speedBumpEnabled by remember(appInfo.packageName, expanded) {
        mutableStateOf(SpeedBump.isEnabled(context, appInfo.packageName))
    }
    // Non-null while the explainer is up; true when it followed the user first turning it on.
    var speedBumpExplainerJustEnabled by remember { mutableStateOf<Boolean?>(null) }
    val menuItems = buildList {
        if (!isCurrentApp && isLaunchableApp) {
            add(AppMenuItem(
                key = AppMenuItemKey.PIN,
                textResId = if (isPinned) R.string.action_unpin_app else R.string.action_pin_app,
                icon = {
                    Icon(
                        painter = painterResource(if (isPinned) R.drawable.ic_unpin else R.drawable.ic_pin),
                        contentDescription = null,
                    )
                },
                onClick = { onDismiss(); if (isPinned) onUnpinApp() else onPinApp() },
            ))
        }
        if (!isCurrentApp) {
            add(AppMenuItem(
                key = AppMenuItemKey.NICKNAME,
                textResId = if (hasNickname) R.string.action_edit_nickname else R.string.common_nickname,
                icon = { Icon(imageVector = Icons.Rounded.Edit, contentDescription = null) },
                onClick = { onDismiss(); onNicknameClick() },
            ))
            add(AppMenuItem(
                key = AppMenuItemKey.ICON,
                textResId = R.string.action_change_icon,
                icon = { Icon(imageVector = Icons.Rounded.IconImage, contentDescription = null) },
                onClick = { onDismiss(); showIconPicker.value = true },
            ))
            add(AppMenuItem(
                key = AppMenuItemKey.TRIGGER,
                textResId = if (hasTrigger) R.string.action_edit_trigger else R.string.action_add_trigger,
                icon = { Icon(imageVector = Icons.Rounded.Bolt, contentDescription = null) },
                onClick = { onDismiss(); onTriggerClick() },
            ))
        }
        if (isLaunchableApp) {
            if (appInfo.userHandleId == null) {
                add(AppMenuItem(
                    key = AppMenuItemKey.SPLIT_SCREEN,
                    textResId = R.string.action_open_in_split_screen,
                    icon = { Icon(imageVector = Icons.Rounded.HorizontalSplit, contentDescription = null) },
                    onClick = { onDismiss(); onOpenInSplitScreen() },
                ))
            }
            add(AppMenuItem(
                key = AppMenuItemKey.ADD_TO_HOME,
                textResId = R.string.action_add_to_home,
                icon = { Icon(imageVector = Icons.Rounded.Home, contentDescription = null) },
                onClick = { onDismiss(); onAddToHome() },
            ))
        }
        add(AppMenuItem(
            key = AppMenuItemKey.APP_INFO,
            textResId = R.string.action_app_info,
            icon = { Icon(imageVector = Icons.Rounded.Info, contentDescription = null) },
            onClick = { onDismiss(); onAppInfoClick() },
        ))
        if (!isCurrentApp && isLaunchableApp) {
            add(AppMenuItem(
                key = AppMenuItemKey.SPEED_BUMP,
                textResId = R.string.speed_bump_title,
                // The green tint is the only affordance for the on/off state.
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Spa,
                        contentDescription = null,
                        tint = if (speedBumpEnabled) AppColors.ActionPhone else LocalContentColor.current,
                    )
                },
                onClick = {
                    speedBumpEnabled = SpeedBump.toggle(context, appInfo.packageName)
                    if (!SpeedBump.hasSeenExplainer(context)) {
                        SpeedBump.markExplainerSeen(context)
                        onDismiss()
                        speedBumpExplainerJustEnabled = true
                    }
                    // Otherwise the menu stays open so the icon can be seen turning green.
                },
                onLongClick = { onDismiss(); speedBumpExplainerJustEnabled = false },
            ))
        }
        add(AppMenuItem(
            key = AppMenuItemKey.EXCLUDE,
            textResId = R.string.action_exclude_generic,
            icon = { Icon(imageVector = Icons.Rounded.VisibilityOff, contentDescription = null) },
            onClick = { onDismiss(); onHideApp() },
        ))
        if (isLaunchableApp) {
            add(AppMenuItem(
                key = AppMenuItemKey.NOTIFICATIONS,
                textResId = if (isPinnedToNotifications) R.string.action_unpin_from_notifications else R.string.action_pin_to_notifications,
                icon = {
                    if (isPinnedToNotifications) {
                        Icon(painter = painterResource(R.drawable.ic_unpin), contentDescription = null)
                    } else {
                        Icon(imageVector = Icons.Rounded.PinEnd, contentDescription = null)
                    }
                },
                onClick = { onDismiss(); PinnedNotifications.toggle(context, notificationAction) },
                span = 2,
            ))
        }
        if (showUninstall) {
            add(AppMenuItem(
                key = AppMenuItemKey.UNINSTALL,
                textResId = R.string.action_uninstall_app,
                icon = { Icon(imageVector = Icons.Rounded.Delete, contentDescription = null) },
                onClick = { onDismiss(); onUninstallClick() },
            ))
        }
    }

    val density = LocalDensity.current
    val shortcutIconSizePx = remember(density) {
        with(density) { ShortcutGridIconSize.roundToPx().coerceAtLeast(1) }
    }
    val iconResult = rememberAppIcon(
        packageName = appInfo.packageName,
        iconPackPackage = iconPackPackage,
        userHandleId = appInfo.userHandleId,
        forceCircularMask = appIconShape == AppIconShape.CIRCLE,
    )

    if (expanded) {
        AppBottomPopup(
            onDismiss = onDismiss,
            leadingContent = {
                iconResult.bitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            },
            title = {
                Column {
                    Text(
                        text = appInfo.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    todayUsage
                        ?.takeIf { it.openedCount > 0 }
                        ?.let { usage ->
                            Text(
                                text = stringResource(
                                    R.string.app_menu_usage_today,
                                    formatUsageDuration(usage.foregroundTimeMillis),
                                    pluralStringResource(
                                        R.plurals.app_menu_opened_count,
                                        usage.openedCount,
                                        usage.openedCount,
                                    ),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                }
            },
        ) {
            if (shortcuts.isNotEmpty()) {
                // Shortcuts section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
                ) {
                    Text(
                        text = stringResource(R.string.app_menu_section_shortcuts),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    shortcuts.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
                        ) {
                            row.forEach { shortcut ->
                                val displayName = shortcutDisplayName(shortcut)
                                val iconBitmap = rememberShortcutIcon(shortcut, shortcutIconSizePx)
                                AppMenuGridButton(
                                    label = displayName,
                                    icon = {
                                        if (iconBitmap != null) {
                                            Image(
                                                bitmap = iconBitmap,
                                                contentDescription = displayName,
                                                modifier = Modifier.size(ShortcutGridIconSize),
                                                contentScale = ContentScale.Fit,
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.size(ShortcutGridIconSize),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = displayName.trim().take(1).uppercase(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                            }
                                        }
                                    },
                                    onClick = { onShortcutClick(shortcut); onDismiss() },
                                    enableMarquee = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // Actions section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
            ) {
                Text(
                    text = stringResource(R.string.app_menu_section_actions),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Speed Bump and Exclude are kept side by side, so they are packed as one
                // inseparable pair. Later single-column items may move ahead of an item that
                // does not fit, which keeps empty slots at the end of the final row only.
                val actionRows = packActionRows(menuItems)
                actionRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.SpacingSmall),
                    ) {
                        row.forEach { item ->
                            if (item.key == AppMenuItemKey.UNINSTALL) {
                                UninstallMenuGridButton(
                                    item = item,
                                    modifier = Modifier.weight(item.span.toFloat()),
                                )
                            } else {
                                AppMenuGridButton(
                                    label = stringResource(item.textResId),
                                    icon = { item.icon() },
                                    onClick = item.onClick,
                                    onLongClick = item.onLongClick,
                                    modifier = Modifier.weight(item.span.toFloat()),
                                )
                            }
                        }
                        repeat(ActionGridColumns - row.sumOf { it.span }) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

            }
        }
    }

    speedBumpExplainerJustEnabled?.let { justEnabled ->
        SpeedBumpExplainerDialog(
            justEnabledForAppName = appInfo.appName.takeIf { justEnabled },
            onDismiss = { speedBumpExplainerJustEnabled = null },
        )
    }

    if (showIconPicker.value) {
        AppIconOverrideDrawer(
            packageName = appInfo.packageName,
            appName = appInfo.appName,
            onDismiss = { showIconPicker.value = false },
        )
    }
}

@Composable
private fun UninstallMenuGridButton(
    item: AppMenuItem,
    modifier: Modifier = Modifier,
) {
    AppMenuGridButton(
        label = stringResource(item.textResId),
        icon = {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.72f),
            )
        },
        onClick = item.onClick,
        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
        contentColor = Color.White,
        showBorder = false,
        modifier = modifier,
    )
}

@Composable
private fun formatUsageDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis / 60_000L).toInt().coerceAtLeast(1)
    val halfHours = totalMinutes / 30
    val hours = halfHours / 2
    return when {
        totalMinutes >= 60 && halfHours % 2 == 1 ->
            stringResource(R.string.app_menu_usage_hours_decimal, "$hours.5")
        totalMinutes >= 60 -> pluralStringResource(R.plurals.app_menu_usage_hours, hours, hours)
        else -> pluralStringResource(R.plurals.app_menu_usage_minutes, totalMinutes, totalMinutes)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppMenuGridButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enableMarquee: Boolean = false,
    containerColor: Color = Color.Transparent,
    contentColor: Color = AppColors.DialogText,
    showBorder: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val borderColor = AppColors.OnboardingBubbleBorder
    val view = LocalView.current
    val borderModifier =
        if (showBorder) {
            Modifier.border(
                width = DesignTokens.BorderWidth,
                color = borderColor,
                shape = DesignTokens.ShapeSmall,
            )
        } else {
            Modifier
        }
    // Surface's own onClick cannot carry a long-press, so items that need one opt into
    // combinedClickable instead.
    val surfaceModifier =
        if (onLongClick != null) {
            modifier
                .clip(DesignTokens.ShapeSmall)
                .then(borderModifier)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        hapticConfirm(view)()
                        onLongClick()
                    },
                )
        } else {
            modifier.then(borderModifier)
        }
    val surfaceContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.SpacingXSmall),
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = if (enableMarquee) Modifier.basicMarquee() else Modifier,
            )
        }
    }

    if (onLongClick != null) {
        Surface(
            modifier = surfaceModifier,
            shape = DesignTokens.ShapeSmall,
            color = containerColor,
        ) { surfaceContent() }
    } else {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = DesignTokens.ShapeSmall,
            color = containerColor,
        ) { surfaceContent() }
    }
}
