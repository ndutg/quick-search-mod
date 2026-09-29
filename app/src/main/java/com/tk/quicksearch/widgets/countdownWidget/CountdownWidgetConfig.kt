package com.tk.quicksearch.widgets.countdownWidget

import android.content.Context
import android.os.Parcelable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tk.quicksearch.widgets.noteWidget.NoteWidgetColors
import com.tk.quicksearch.widgets.noteWidget.withNoteWidgetDefaults
import com.tk.quicksearch.widgets.utils.WidgetPreferences
import com.tk.quicksearch.widgets.utils.applyWidgetPreferences
import com.tk.quicksearch.widgets.utils.toWidgetPreferences
import kotlinx.parcelize.Parcelize

/** How the Countdown widget describes the time to or since its date. */
enum class CountdownFormat(
    val value: String,
) {
    /** Whole days, e.g. "in 45 days". */
    DAYS("days"),

    /** Weeks to one decimal, e.g. "in 6.4 weeks". */
    WEEKS("weeks"),

    /** Months to one decimal, e.g. "in 1.5 months". */
    MONTHS("months"),

    /** Years to one decimal, e.g. "1.5 years ago". */
    YEARS("years"),

    /** The two largest calendar units, e.g. "in 6 months 5 days" or "2 years 6 months ago". */
    DETAILED("detailed"),

    /** Share of the time from when the date was set until it, for future dates only. */
    PROGRESS("progress"),
}

private object CountdownKeys {
    val TARGET_EPOCH_DAY = longPreferencesKey("countdown_widget_target_epoch_day")
    val START_MILLIS = longPreferencesKey("countdown_widget_start_millis")
    val FORMAT = stringPreferencesKey("countdown_widget_format")
    val TITLE = stringPreferencesKey("countdown_widget_title")
    val TEXT_SIZE = floatPreferencesKey("countdown_widget_text_size")
}

internal object CountdownWidgetDefaults {
    const val TEXT_SIZE_SP = 28f
    const val TEXT_SIZE_MIN_SP = 12f
    const val TEXT_SIZE_MAX_SP = 72f
    const val TITLE_MAX_LENGTH = 60
    val FORMAT = CountdownFormat.DAYS
}

/**
 * A Countdown widget's settings. [style] holds the background, text color, corner radius and
 * transparency in the same keys the other widgets use; the rest are countdown-only keys.
 */
@Parcelize
data class CountdownWidgetConfig(
    val style: WidgetPreferences = WidgetPreferences.Default.withNoteWidgetDefaults(),
    /** The chosen date as a [java.time.LocalDate] epoch day; null until one is picked. */
    val targetEpochDay: Long? = null,
    /** When [targetEpochDay] was chosen; the start of [CountdownFormat.PROGRESS]. */
    val startMillis: Long = 0L,
    val format: CountdownFormat = CountdownWidgetDefaults.FORMAT,
    val title: String = "",
    val textSizeSp: Float = CountdownWidgetDefaults.TEXT_SIZE_SP,
) : Parcelable {
    fun coerceToValidRanges(): CountdownWidgetConfig =
        copy(
            style = style.coerceToValidRanges(),
            textSizeSp =
                if (textSizeSp.isFinite()) {
                    textSizeSp.coerceIn(CountdownWidgetDefaults.TEXT_SIZE_MIN_SP, CountdownWidgetDefaults.TEXT_SIZE_MAX_SP)
                } else {
                    CountdownWidgetDefaults.TEXT_SIZE_SP
                },
            title = title.take(CountdownWidgetDefaults.TITLE_MAX_LENGTH),
        )
}

/** Reads a Countdown widget's settings; a widget that was never saved gets the defaults. */
fun Preferences.toCountdownWidgetConfig(context: Context): CountdownWidgetConfig {
    val targetEpochDay = this[CountdownKeys.TARGET_EPOCH_DAY]
    return CountdownWidgetConfig(
        // The shared defaults suit the search widget, so an unsaved widget starts from the Note look.
        style = if (targetEpochDay == null) CountdownWidgetConfig().style else toWidgetPreferences(context),
        targetEpochDay = targetEpochDay,
        startMillis = this[CountdownKeys.START_MILLIS] ?: 0L,
        format =
            this[CountdownKeys.FORMAT]?.let { value -> CountdownFormat.entries.find { it.value == value } }
                ?: CountdownWidgetDefaults.FORMAT,
        title = this[CountdownKeys.TITLE].orEmpty(),
        textSizeSp = this[CountdownKeys.TEXT_SIZE] ?: CountdownWidgetDefaults.TEXT_SIZE_SP,
    ).coerceToValidRanges()
}

fun MutablePreferences.applyCountdownWidgetConfig(
    config: CountdownWidgetConfig,
    context: Context,
) {
    val validated = config.coerceToValidRanges()
    applyWidgetPreferences(validated.style, context)
    validated.targetEpochDay?.let { this[CountdownKeys.TARGET_EPOCH_DAY] = it }
        ?: remove(CountdownKeys.TARGET_EPOCH_DAY)
    this[CountdownKeys.START_MILLIS] = validated.startMillis
    this[CountdownKeys.FORMAT] = validated.format.value
    this[CountdownKeys.TITLE] = validated.title
    this[CountdownKeys.TEXT_SIZE] = validated.textSizeSp
}

/** Countdown widget colors; the transparency setting applies to [background]. */
internal data class CountdownWidgetColors(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val progressTrack: Color,
) {
    companion object {
        private const val SECONDARY_ALPHA = 0.75f
        private const val TRACK_ALPHA = 0.25f

        /** Like the Note widget's colors, plus an optional custom text color. */
        fun forWidget(
            config: CountdownWidgetConfig,
            isSystemDark: Boolean,
        ): CountdownWidgetColors {
            val base = NoteWidgetColors.forWidget(config.style, isSystemDark)
            val text = config.style.customTextIconColor?.let(::Color) ?: base.title
            return CountdownWidgetColors(
                background = base.background,
                text = text,
                secondaryText = text.copy(alpha = SECONDARY_ALPHA),
                progressTrack = text.copy(alpha = TRACK_ALPHA),
            )
        }
    }
}
