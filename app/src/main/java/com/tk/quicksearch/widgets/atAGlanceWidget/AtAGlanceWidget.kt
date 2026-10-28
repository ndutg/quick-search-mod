package com.tk.quicksearch.widgets.atAGlanceWidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.runComposition
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider as DayNightColorProvider
import com.tk.quicksearch.R
import com.tk.quicksearch.search.data.preferences.AppGlanceItemSettings
import com.tk.quicksearch.search.data.preferences.WidgetGlanceItemSettings
import com.tk.quicksearch.shared.ui.icons.MaterialSymbols
import com.tk.quicksearch.widgets.noteWidget.NoteWidgetColors
import com.tk.quicksearch.widgets.noteWidget.withNoteWidgetDefaults
import com.tk.quicksearch.widgets.utils.WidgetBitmapUtils
import com.tk.quicksearch.widgets.utils.WidgetLayoutUtils
import com.tk.quicksearch.widgets.utils.WidgetVariant
import com.tk.quicksearch.widgets.utils.enforceVariantConstraints
import com.tk.quicksearch.widgets.utils.hasStoredWidgetConfig
import com.tk.quicksearch.widgets.utils.toWidgetPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Default size (3 columns by 2 rows) used until the host reports the placed widget's size. */
private const val DEFAULT_WIDTH_DP = 180f
private const val DEFAULT_HEIGHT_DP = 110f

/** Shared by the placed widget and the configure-screen preview so both render the same rows. */
internal object AtAGlanceWidgetDimens {
    val ICON_SIZE = 24.dp
    val ICON_TEXT_SPACING = 12.dp
    val HORIZONTAL_PADDING = 16.dp
    val LIST_VERTICAL_PADDING = 4.dp
    val ROW_VERTICAL_PADDING = 10.dp
    val DIVIDER_HEIGHT = 1.dp
    val DISMISS_BUTTON_SIZE = 28.dp
    val DISMISS_ICON_SIZE = 16.dp
    val TITLE_FONT_SIZE = 14.sp
    val SUBTITLE_FONT_SIZE = 12.sp
    val EMPTY_DATE_FONT_SIZE = 16.sp
    const val DIVIDER_ALPHA = 0.15f
}

/**
 * The home At a Glance items as a scrollable list on a card, styled like the Note widget. Rows are
 * read fresh on every redraw; [AtAGlanceWidgetUpdater] and [AtAGlanceWidgetReceiver] decide when.
 */
class AtAGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        AtAGlanceWidgetUpdater.start(context)
        provideContent { WidgetBody() }
    }

    /**
     * Draws [appWidgetId] and hands it to the host right away. [update] alone waits for Glance's
     * session worker, which WorkManager can hold back for many seconds, so a saved setting or a
     * changed row would otherwise show up late. The session still redraws the same state after.
     */
    @OptIn(ExperimentalGlanceApi::class)
    suspend fun drawNow(
        context: Context,
        appWidgetId: Int,
        glanceId: GlanceId,
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        // If this fails, the session's update still draws the widget, only later.
        runCatching {
            val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            val remoteViews =
                runComposition(context, glanceId, options, listOf(placedSize(options)), state).first()
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    /** The placed size the host reported, or the default size before it reports one. */
    private fun placedSize(options: Bundle): DpSize {
        val reported =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)?.firstOrNull()
            } else {
                null
            }
        if (reported != null && reported.width > 0f && reported.height > 0f) {
            return DpSize(reported.width.dp, reported.height.dp)
        }
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) {
            DpSize(width.dp, height.dp)
        } else {
            DpSize(DEFAULT_WIDTH_DP.dp, DEFAULT_HEIGHT_DP.dp)
        }
    }

    @Composable
    private fun WidgetBody() {
        // Reading the state keeps this composition subscribed to refreshAtAGlanceWidgets' revision
        // bump, so the rows below are re-read on every refresh.
        val prefs = currentState<Preferences>()
        val context = LocalContext.current
        val storedConfig = prefs.toWidgetPreferences(context).enforceVariantConstraints(WidgetVariant.AT_A_GLANCE)
        // Until the configure screen saves, show the look it starts from.
        val config = if (prefs.hasStoredWidgetConfig()) storedConfig else storedConfig.withNoteWidgetDefaults()
        val items = WidgetGlanceItemSettings(config.glanceItems, AppGlanceItemSettings(context))
        val snapshot = loadAtAGlanceWidgetSnapshot(context, items)
        AtAGlanceWidgetReceiver.scheduleRefresh(context, snapshot.nextChangeMillis)

        val size = LocalSize.current
        val widthDp = WidgetLayoutUtils.resolveOr(size.width, DEFAULT_WIDTH_DP.dp)
        val heightDp = WidgetLayoutUtils.resolveOr(size.height, DEFAULT_HEIGHT_DP.dp)
        val resources = context.resources
        val density = resources.displayMetrics.density
        val isDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // Identical unless the background follows the system theme.
        val dayColors = NoteWidgetColors.forWidget(config, isSystemDark = false)
        val nightColors = NoteWidgetColors.forWidget(config, isSystemDark = true)

        val backgroundModifier =
            when {
                config.backgroundAlpha <= 0f -> GlanceModifier
                // Day/night colors let the launcher switch the card with the system theme.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    GlanceModifier
                        .background(DayNightColorProvider(day = dayColors.background, night = nightColors.background))
                        .cornerRadius(config.borderRadiusDp.dp)
                // Corner radii need Android 12+ in Glance, so older versions draw the card as a bitmap.
                else ->
                    GlanceModifier.background(
                        ImageProvider(
                            WidgetBitmapUtils.createWidgetBitmap(
                                widthPx = (widthDp.value * density).roundToInt().coerceAtLeast(1),
                                heightPx = (heightDp.value * density).roundToInt().coerceAtLeast(1),
                                backgroundColor = if (isDark) nightColors.background else dayColors.background,
                                borderColor = null,
                                borderWidthPx = 0,
                                cornerRadiusPx = config.borderRadiusDp * density,
                            ),
                        ),
                    )
            }

        Column(modifier = GlanceModifier.fillMaxSize().then(backgroundModifier)) {
            if (snapshot.rows.isEmpty()) {
                EmptyContent(context, dayColors, nightColors, config.textScale, config.showEmptyDate)
            } else {
                RowList(context, snapshot.rows, dayColors, nightColors, config.textScale)
            }
        }
    }

    @Composable
    private fun RowList(
        context: Context,
        rows: List<AtAGlanceWidgetRow>,
        dayColors: NoteWidgetColors,
        nightColors: NoteWidgetColors,
        textScale: Float,
    ) {
        val titleColor = DayNightColorProvider(day = dayColors.title, night = nightColors.title)
        val subtitleColor = DayNightColorProvider(day = dayColors.body, night = nightColors.body)
        val dividerColor =
            DayNightColorProvider(
                day = dayColors.title.copy(alpha = AtAGlanceWidgetDimens.DIVIDER_ALPHA),
                night = nightColors.title.copy(alpha = AtAGlanceWidgetDimens.DIVIDER_ALPHA),
            )
        val lastKey = rows.last().key
        LazyColumn(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(
                        horizontal = AtAGlanceWidgetDimens.HORIZONTAL_PADDING,
                        vertical = AtAGlanceWidgetDimens.LIST_VERTICAL_PADDING,
                    ),
        ) {
            items(rows, itemId = { it.key.hashCode().toLong() }) { row ->
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    GlanceRow(context, row, titleColor, subtitleColor, textScale)
                    if (row.key != lastKey) {
                        Box(
                            modifier =
                                GlanceModifier
                                    .fillMaxWidth()
                                    .height(AtAGlanceWidgetDimens.DIVIDER_HEIGHT)
                                    .background(dividerColor),
                        ) {}
                    }
                }
            }
        }
    }

    @Composable
    private fun GlanceRow(
        context: Context,
        row: AtAGlanceWidgetRow,
        titleColor: androidx.glance.unit.ColorProvider,
        subtitleColor: androidx.glance.unit.ColorProvider,
        textScale: Float,
    ) {
        Row(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = AtAGlanceWidgetDimens.ROW_VERTICAL_PADDING)
                    .clickable(
                        onClick = actionStartActivity(AtAGlanceWidgetActionActivity.createIntent(context, row)),
                        rippleOverride = android.R.color.transparent,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIcon(context, row.icon, titleColor)
            Spacer(modifier = GlanceModifier.width(AtAGlanceWidgetDimens.ICON_TEXT_SPACING))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = row.title,
                    style =
                        TextStyle(
                            color = titleColor,
                            fontSize = AtAGlanceWidgetDimens.TITLE_FONT_SIZE * textScale,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 2,
                )
                row.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = TextStyle(color = subtitleColor, fontSize = AtAGlanceWidgetDimens.SUBTITLE_FONT_SIZE * textScale),
                        maxLines = row.subtitleMaxLines,
                    )
                }
            }
            if (row.dismiss != null) DismissButton(context, row, subtitleColor)
        }
    }

    /** Home's close button; the nested clickable keeps its tap from also opening the row. */
    @Composable
    private fun DismissButton(
        context: Context,
        row: AtAGlanceWidgetRow,
        tint: androidx.glance.unit.ColorProvider,
    ) {
        Box(
            modifier =
                GlanceModifier
                    .size(AtAGlanceWidgetDimens.DISMISS_BUTTON_SIZE)
                    .cornerRadius(AtAGlanceWidgetDimens.DISMISS_BUTTON_SIZE / 2)
                    .clickable(
                        onClick =
                            actionRunCallback<AtAGlanceWidgetDismissAction>(AtAGlanceWidgetDismissAction.parameters(row)),
                        rippleOverride = android.R.color.transparent,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(AtAGlanceWidgetIcons.vector(context, MaterialSymbols.Close)),
                contentDescription = context.getString(R.string.common_close),
                modifier = GlanceModifier.size(AtAGlanceWidgetDimens.DISMISS_ICON_SIZE),
                colorFilter = ColorFilter.tint(tint),
            )
        }
    }

    @Composable
    private fun RowIcon(
        context: Context,
        icon: AtAGlanceWidgetIcon,
        tint: androidx.glance.unit.ColorProvider,
    ) {
        val modifier = GlanceModifier.size(AtAGlanceWidgetDimens.ICON_SIZE)
        when (icon) {
            is AtAGlanceWidgetIcon.Vector ->
                Image(
                    provider = ImageProvider(AtAGlanceWidgetIcons.vector(context, icon.vector)),
                    contentDescription = null,
                    modifier = modifier,
                    colorFilter = ColorFilter.tint(tint),
                )
            is AtAGlanceWidgetIcon.Drawable ->
                Image(
                    provider = ImageProvider(icon.resId),
                    contentDescription = null,
                    modifier = modifier,
                    colorFilter = ColorFilter.tint(tint),
                )
            is AtAGlanceWidgetIcon.App -> {
                val appIcon = AtAGlanceWidgetIcons.app(context, icon.packageName)
                Image(
                    provider = ImageProvider(appIcon ?: AtAGlanceWidgetIcons.fallbackAppIcon(context)),
                    contentDescription = null,
                    modifier = modifier,
                    colorFilter = if (appIcon == null) ColorFilter.tint(tint) else null,
                )
            }
        }
    }

    /** Nothing to show, so today's day and date, or nothing when [showDate] is off; a tap opens Quick Search. */
    @Composable
    private fun EmptyContent(
        context: Context,
        dayColors: NoteWidgetColors,
        nightColors: NoteWidgetColors,
        textScale: Float,
        showDate: Boolean,
    ) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(AtAGlanceWidgetDimens.HORIZONTAL_PADDING)
                    .let { modifier ->
                        launchIntent?.let {
                            modifier.clickable(onClick = actionStartActivity(it), rippleOverride = android.R.color.transparent)
                        } ?: modifier
                    },
            contentAlignment = Alignment.Center,
        ) {
            if (!showDate) return@Box
            Text(
                text = atAGlanceWidgetEmptyDate(),
                style =
                    TextStyle(
                        color = DayNightColorProvider(day = dayColors.title, night = nightColors.title),
                        fontSize = AtAGlanceWidgetDimens.EMPTY_DATE_FONT_SIZE * textScale,
                        fontWeight = FontWeight.Medium,
                    ),
                maxLines = 1,
            )
        }
    }
}

/** Today's day and date on one line, like "Friday, Oct 23", shown when there are no items. */
private fun atAGlanceWidgetEmptyDate(): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEMMMd")
    return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, locale))
}
