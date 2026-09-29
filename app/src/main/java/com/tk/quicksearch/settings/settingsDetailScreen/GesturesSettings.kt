package com.tk.quicksearch.settings.settingsDetailScreen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tk.quicksearch.R
import com.tk.quicksearch.search.core.SearchViewModel
import com.tk.quicksearch.search.core.SearchTarget
import com.tk.quicksearch.searchEngines.getId
import com.tk.quicksearch.searchEngines.getDisplayName
import com.tk.quicksearch.searchEngines.AliasHandler
import com.tk.quicksearch.searchEngines.shared.SearchTargetIcon
import com.tk.quicksearch.search.data.userAppPreferences.UserAppPreferences
import com.tk.quicksearch.search.data.preferences.SwipeGestureAction
import com.tk.quicksearch.search.data.preferences.HomeSwipeGestureAction
import com.tk.quicksearch.search.searchScreen.LockScreenAccessibilityService
import com.tk.quicksearch.shared.permissions.LockScreenAccessibilityDisclosureDialog
import com.tk.quicksearch.shared.permissions.shouldShowAccessibilityDisclosure
import com.tk.quicksearch.shared.util.isDefaultHomeApp
import com.tk.quicksearch.settings.shared.SettingsCard
import com.tk.quicksearch.settings.shared.SettingsCardItem
import com.tk.quicksearch.settings.shared.SettingsNavigationRow
import com.tk.quicksearch.shared.ui.components.AppAlertDialog
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.shared.ui.theme.DesignTokens
import com.tk.quicksearch.widgets.customButtonsWidget.CustomWidgetButtonAction
import com.tk.quicksearch.widgets.customButtonsWidget.CustomWidgetButtonPickerDialog

internal enum class SwipeDirection(val titleResId: Int, val defaultAction: SwipeGestureAction) {
    RIGHT(R.string.settings_gesture_swipe_right, SwipeGestureAction.WIDGETS_PANEL),
    LEFT(R.string.settings_gesture_swipe_left, SwipeGestureAction.SETTINGS),
    UP(R.string.settings_gesture_swipe_up, SwipeGestureAction.OPEN_KEYBOARD),
    DOWN(R.string.settings_gesture_swipe_down, SwipeGestureAction.CLOSE_KEYBOARD_OR_NOTIFICATIONS),
}

internal enum class HomeGesture(val titleResId: Int, val defaultAction: HomeSwipeGestureAction) {
    SWIPE_UP(R.string.settings_gesture_swipe_up_home, HomeSwipeGestureAction.NONE),
    SWIPE_DOWN(R.string.settings_gesture_swipe_down_home, HomeSwipeGestureAction.NOTIFICATION_PANEL),
    DOUBLE_TAP(R.string.settings_gesture_double_tap_home, HomeSwipeGestureAction.NONE),
}

internal enum class AliasPickerKind { SEARCH_ENGINE, TOOL }

@Composable
fun GesturesSettingsSection(
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel = viewModel(),
) {
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val state = rememberGestureSettingsState(searchViewModel)
    GestureSettingsDialogs(state, searchViewModel)

    Column(modifier = modifier) {
        SettingsCard(modifier = Modifier.fillMaxWidth().padding(bottom = DesignTokens.SectionTopPadding)) {
            Column {
                val gestureDirections = SwipeDirection.entries
                gestureDirections.forEachIndexed { index, direction ->
                    SettingsNavigationRow(
                        item =
                            SettingsCardItem(
                                title =
                                    stringResource(
                                        when (direction) {
                                            SwipeDirection.UP -> R.string.settings_gesture_swipe_up_home
                                            SwipeDirection.DOWN -> R.string.settings_gesture_swipe_down_home
                                            else -> direction.titleResId
                                        },
                                    ),
                                icon = direction.gestureIcon(),
                                description =
                                    if (direction == SwipeDirection.RIGHT && state.isDefaultLauncher) {
                                        stringResource(
                                            if (state.isLauncherSwipeRightEnabled) R.string.settings_gesture_widget_panel
                                            else R.string.settings_gesture_none,
                                        )
                                    } else if (direction == SwipeDirection.UP || direction == SwipeDirection.DOWN) {
                                        val gesture = if (direction == SwipeDirection.UP) HomeGesture.SWIPE_UP else HomeGesture.SWIPE_DOWN
                                        homeGestureDescription(state.homeActions.getValue(gesture), state.homeCustomActions[gesture], state.homeAliasTargets[gesture], searchState)
                                    } else {
                                        gestureDescription(state.actions.getValue(direction), state.customActions[direction], state.aliasTargets[direction], searchState)
                                    },
                                actionOnPress = {
                                    when (direction) {
                                        SwipeDirection.UP -> state.selectedHomeGesture = HomeGesture.SWIPE_UP
                                        SwipeDirection.DOWN -> state.selectedHomeGesture = HomeGesture.SWIPE_DOWN
                                        else -> state.selectedDirection = direction
                                    }
                                },
                            ),
                        contentPadding = PaddingValues(
                            horizontal = DesignTokens.CardHorizontalPadding,
                            vertical = DesignTokens.CardVerticalPadding,
                        ),
                    )
                    HorizontalDivider(color = AppColors.SettingsDivider)
                }
                SettingsNavigationRow(
                    item =
                        SettingsCardItem(
                            title = stringResource(HomeGesture.DOUBLE_TAP.titleResId),
                            icon = HomeGesture.DOUBLE_TAP.icon(),
                            description = homeGestureDescription(
                                state.homeActions.getValue(HomeGesture.DOUBLE_TAP),
                                state.homeCustomActions[HomeGesture.DOUBLE_TAP],
                                state.homeAliasTargets[HomeGesture.DOUBLE_TAP],
                                searchState,
                            ),
                            actionOnPress = { state.selectedHomeGesture = HomeGesture.DOUBLE_TAP },
                        ),
                    contentPadding = PaddingValues(
                        horizontal = DesignTokens.CardHorizontalPadding,
                        vertical = DesignTokens.CardVerticalPadding,
                    ),
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_keyboard_gestures_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = DesignTokens.SectionTitleBottomPadding),
        )
        Text(
            text = stringResource(R.string.settings_keyboard_gestures_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = DesignTokens.SectionDescriptionBottomPadding),
        )
        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsNavigationRow(
                    item = SettingsCardItem(
                        title = stringResource(R.string.action_open_keyboard),
                        description = stringResource(state.keyboardGestureDirection(SwipeGestureAction.OPEN_KEYBOARD)?.titleResId ?: R.string.settings_gesture_none),
                        icon = Icons.Rounded.Keyboard,
                        actionOnPress = { state.selectedKeyboardAction = SwipeGestureAction.OPEN_KEYBOARD },
                    ),
                    contentPadding = PaddingValues(horizontal = DesignTokens.CardHorizontalPadding, vertical = DesignTokens.CardVerticalPadding),
                )
                HorizontalDivider(color = AppColors.SettingsDivider)
                SettingsNavigationRow(
                    item = SettingsCardItem(
                        title = stringResource(R.string.settings_gesture_close_keyboard),
                        description = stringResource(state.keyboardGestureDirection(SwipeGestureAction.CLOSE_KEYBOARD_OR_NOTIFICATIONS)?.titleResId ?: R.string.settings_gesture_none),
                        icon = Icons.Rounded.KeyboardHide,
                        actionOnPress = { state.selectedKeyboardAction = SwipeGestureAction.CLOSE_KEYBOARD_OR_NOTIFICATIONS },
                    ),
                    contentPadding = PaddingValues(horizontal = DesignTokens.CardHorizontalPadding, vertical = DesignTokens.CardVerticalPadding),
                )
            }
        }
    }
}
