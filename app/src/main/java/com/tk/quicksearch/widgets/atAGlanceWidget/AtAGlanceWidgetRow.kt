package com.tk.quicksearch.widgets.atAGlanceWidget

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector

/** The icon at the start of an At a Glance widget row. */
internal sealed interface AtAGlanceWidgetIcon {
    /** A Material icon, tinted with the widget's text color. */
    class Vector(
        val vector: ImageVector,
    ) : AtAGlanceWidgetIcon

    /** A drawable of the app's own, tinted with the widget's text color. */
    class Drawable(
        @DrawableRes val resId: Int,
    ) : AtAGlanceWidgetIcon

    /** An app's own icon, shown untinted, falling back to a generic app icon. */
    class App(
        val packageName: String,
    ) : AtAGlanceWidgetIcon
}

/** What a tap on a row does; read back by [AtAGlanceWidgetActionActivity] when the row is tapped. */
internal enum class AtAGlanceWidgetTapType {
    /** Opens the notification [AtAGlanceWidgetTap.key], or launches [AtAGlanceWidgetTap.packageName]. */
    NOTIFICATION,
    OTP,
    MISSED_CALLS,
    ALARM,
    REMINDER,
    BIRTHDAY,
    CALENDAR_EVENT,
    CUSTOM_INFO,
    LOW_BATTERY,
    CHARGING,
    LOW_STORAGE,
    DO_NOT_DISTURB,
    AIRPLANE_MODE,
    HOTSPOT,
    WIFI_SIGN_IN,
    FLASHLIGHT,
}

/** What the row's close button hides, matching the rows home lets the user dismiss. */
internal enum class AtAGlanceWidgetDismissType {
    OTP,
    ALARM,
    REMINDER,
    MISSED_CALLS,
    APP_NOTIFICATION,
    WEATHER,
    FINISHED_PROGRESS,
    BIRTHDAY,
    TOMORROW_EVENT,
    CUSTOM_INFO,
    WIFI_SIGN_IN,
    CHARGING,
    LOW_STORAGE,
}

internal class AtAGlanceWidgetTap(
    val type: AtAGlanceWidgetTapType,
    val key: String? = null,
    val packageName: String? = null,
    val id: Long = 0L,
    /** Extra text the tap needs, such as a birthday contact's lookup key. */
    val extra: String? = null,
)

/** One At a Glance item as the widget draws it. */
internal class AtAGlanceWidgetRow(
    /** Stable across refreshes, so the launcher keeps the scroll position. */
    val key: String,
    val icon: AtAGlanceWidgetIcon,
    val title: String,
    val subtitle: String? = null,
    val subtitleMaxLines: Int = 2,
    val tap: AtAGlanceWidgetTap,
    /** Shows a close button that hides the item as on home; it reads the tap's key and id. */
    val dismiss: AtAGlanceWidgetDismissType? = null,
)

/** The rows to draw and when they next change on their own (an alarm entering its window, a code expiring). */
internal class AtAGlanceWidgetSnapshot(
    val rows: List<AtAGlanceWidgetRow>,
    val nextChangeMillis: Long?,
)
