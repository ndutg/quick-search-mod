package com.tk.quicksearch.widgets.noteWidget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tk.quicksearch.search.notes.NotesTextUtils
import com.tk.quicksearch.shared.ui.theme.AppColors
import com.tk.quicksearch.widgets.utils.TextIconColorOverride
import com.tk.quicksearch.widgets.utils.WidgetPreferences
import com.tk.quicksearch.widgets.utils.WidgetTheme
import kotlin.math.floor

/** Shared by the placed widget and the configure-screen preview so both render the same card. */
internal object NoteWidgetDimens {
    val CONTENT_PADDING = 16.dp
    val TITLE_BODY_SPACING = 6.dp
    val TITLE_FONT_SIZE = 16.sp
    val BODY_FONT_SIZE = 14.sp

    /** Line height of the widget text relative to its font size, rounded up so lines never clip. */
    private const val LINE_HEIGHT_MULTIPLIER = 1.3f

    /** Longer bodies are cut before they reach RemoteViews, which caps how much a widget can hold. */
    private const val MAX_BODY_CHARS = 1500

    /** Height of the preview card, roughly that of the default 2-row widget on a phone launcher. */
    val PREVIEW_HEIGHT = 170.dp

    const val DEFAULT_CORNER_RADIUS_DP = 24f
    const val DEFAULT_BACKGROUND_ALPHA = 0.6f

    /** How many body lines fit under the title in a card of [heightDp]. */
    fun bodyMaxLines(
        heightDp: Float,
        hasTitle: Boolean,
        fontScale: Float,
    ): Int {
        val titleHeight =
            if (hasTitle) {
                TITLE_FONT_SIZE.value * fontScale * LINE_HEIGHT_MULTIPLIER + TITLE_BODY_SPACING.value
            } else {
                0f
            }
        val available = heightDp - CONTENT_PADDING.value * 2 - titleHeight
        val lineHeight = BODY_FONT_SIZE.value * fontScale * LINE_HEIGHT_MULTIPLIER
        return floor(available / lineHeight).toInt().coerceAtLeast(1)
    }

    fun bodyText(
        markdownContent: String,
        maxLines: Int,
    ): String = NotesTextUtils.firstLinesPreview(markdownContent, maxLines).take(MAX_BODY_CHARS)
}

/** Note widget colors; the transparency setting applies to [background]. */
internal data class NoteWidgetColors(
    val background: Color,
    val title: Color,
    val body: Color,
    val placeholder: Color,
) {
    companion object {
        private const val BODY_ALPHA = 0.75f
        private const val PLACEHOLDER_ALPHA = 0.5f

        /**
         * Colors for [config]'s background and text choices while the system is in [isSystemDark]
         * mode. Only the System background depends on the system mode. Automatic text
         * ([TextIconColorOverride.THEME]) is white or black, whichever reads on the background.
         */
        fun forWidget(
            config: WidgetPreferences,
            isSystemDark: Boolean,
        ): NoteWidgetColors {
            val customBackground = config.backgroundColor?.let(::Color)
            val isDark =
                when {
                    customBackground != null -> customBackground.luminance() < 0.5f
                    config.theme == WidgetTheme.LIGHT -> false
                    config.theme == WidgetTheme.DARK -> true
                    else -> isSystemDark
                }
            val content =
                when (config.textIconColorOverride) {
                    TextIconColorOverride.WHITE -> AppColors.WidgetTextLight
                    TextIconColorOverride.BLACK -> AppColors.WidgetTextDark
                    TextIconColorOverride.THEME -> if (isDark) AppColors.WidgetTextLight else AppColors.WidgetTextDark
                }
            val background =
                customBackground ?: if (isDark) AppColors.WidgetBackgroundDark else AppColors.WidgetBackgroundLight
            return NoteWidgetColors(
                background = background.copy(alpha = config.backgroundAlpha),
                title = content,
                body = content.copy(alpha = BODY_ALPHA),
                placeholder = content.copy(alpha = PLACEHOLDER_ALPHA),
            )
        }
    }
}

/** Starting look for a newly placed Note widget: a system-themed, partly transparent card. */
internal fun WidgetPreferences.withNoteWidgetDefaults(): WidgetPreferences =
    copy(
        theme = WidgetTheme.SYSTEM,
        backgroundColor = null,
        textIconColorOverride = TextIconColorOverride.THEME,
        customTextIconColor = null,
        borderRadiusDp = NoteWidgetDimens.DEFAULT_CORNER_RADIUS_DP,
        backgroundAlpha = NoteWidgetDimens.DEFAULT_BACKGROUND_ALPHA,
    )
