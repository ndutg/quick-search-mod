package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tk.quicksearch.search.searchScreen.shared.LocalSearchCardLayoutEnabled
import com.tk.quicksearch.shared.ui.theme.DesignTokens

/**
 * How far the results column reaches past the search screen's side padding with the card layout
 * off, so flat section headers can paint their background and divider to the screen edges. Matches
 * the horizontal padding in `SearchScreenContent`.
 */
@Composable
internal fun compactEdgeBleed(isOverlayPresentation: Boolean): Dp =
    when {
        LocalSearchCardLayoutEnabled.current -> 0.dp
        isOverlayPresentation -> DesignTokens.SpacingXLarge
        else -> DesignTokens.SpacingLarge
    }

/**
 * Widens this node by [bleed] on both sides without changing the space it takes in its parent.
 * Pair with [Modifier.padding] of the same [bleed] further in so content stays where it was.
 */
internal fun Modifier.edgeBleed(bleed: Dp): Modifier =
    if (bleed == 0.dp) {
        this
    } else {
        layout { measurable, constraints ->
            val extra = bleed.roundToPx() * 2
            val placeable =
                measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minWidth + extra,
                        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
                    ),
                )
            layout(placeable.width - extra, placeable.height) { placeable.placeRelative(-extra / 2, 0) }
        }
    }
