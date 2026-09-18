package com.tk.quicksearch.widgetsPanel

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.tk.quicksearch.search.core.ItemPriorityConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

private const val HOME_WIDGET_HOST_ID = 8291

// Taps this close to the widget being edited still count as touching it, so the edit badges that
// overhang its edges stay usable.
private val HomeWidgetEditTouchMargin = 24.dp

/** Process-wide source of the widgets pinned to Home, shared by the widgets panel and Home. */
internal object HomePinnedWidgetsStore {
    private val state = MutableStateFlow<List<PanelWidgetInfo>>(emptyList())
    private val loaded = AtomicBoolean(false)

    // Single-threaded so placement writes land in the order they were made.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    val widgets: StateFlow<List<PanelWidgetInfo>> = state.asStateFlow()

    suspend fun load(context: Context) {
        if (loaded.get()) return
        val appContext = context.applicationContext
        val stored = withContext(Dispatchers.IO) { WidgetsPanelPreferences(appContext).getWidgets() }
        // A publish that landed while reading is newer than what was read.
        if (loaded.compareAndSet(false, true)) state.value = stored.homePinned()
    }

    /** Publishes the full panel widget list after it was persisted. */
    fun publish(widgets: List<PanelWidgetInfo>) {
        loaded.set(true)
        state.value = widgets.homePinned()
    }

    /** Applies the placements in memory right away and persists them off the main thread. */
    fun updatePlacements(
        context: Context,
        placements: Map<Int, HomeWidgetPlacement?>,
    ) {
        if (placements.isEmpty()) return
        state.value =
            state.value.mapNotNull { widget ->
                if (widget.appWidgetId in placements) {
                    placements[widget.appWidgetId]?.let { widget.copy(home = it) }
                } else {
                    widget
                }
            }
        val appContext = context.applicationContext
        persistScope.launch {
            WidgetsPanelPreferences(appContext).setHomePlacements(placements)
        }
    }

    private fun List<PanelWidgetInfo>.homePinned(): List<PanelWidgetInfo> =
        filter { it.home != null && !it.isQuickNoteWidget() }
}

/** New Home widgets land right below the app grid (above it in one-handed mode). */
internal fun defaultHomePlacement(
    widget: PanelWidgetInfo,
    allWidgets: List<PanelWidgetInfo>,
): HomeWidgetPlacement {
    val anchor = ItemPriorityConfig.ItemType.UPCOMING_ALARM.name
    val firstOrderAtAnchor =
        allWidgets.mapNotNull { it.home?.takeIf { home -> home.anchor == anchor }?.order }.minOrNull()
    val columnSpan =
        (widget.columnSpan ?: WIDGET_PANEL_DEFAULT_COLUMN_SPAN).coerceIn(1, WIDGET_PANEL_GRID_COLUMNS)
    return HomeWidgetPlacement(
        anchor = anchor,
        order = (firstOrderAtAnchor ?: 1) - 1,
        column = (widget.column ?: 0).coerceIn(0, WIDGET_PANEL_GRID_COLUMNS - columnSpan),
        columnSpan = columnSpan,
        rowSpan = (widget.rowSpan ?: WIDGET_PANEL_DEFAULT_ROW_SPAN).coerceIn(1, WIDGET_PANEL_MAX_ROW_SPAN),
    )
}

internal sealed interface HomeLayoutEntry {
    val key: Any

    data class Item(
        val itemType: ItemPriorityConfig.ItemType,
    ) : HomeLayoutEntry {
        override val key: Any get() = itemType
    }

    data class Widget(
        val widget: PanelWidgetInfo,
    ) : HomeLayoutEntry {
        override val key: Any get() = widget.appWidgetId
    }
}

/**
 * Interleaves pinned widgets with the Home layout. [layoutOrder] is the rendered (visual) order;
 * widget anchors refer to logical order, so each widget sits before its anchor item and the whole
 * sequence flips with the layout in one-handed mode. Unknown anchors fall back to the end.
 */
internal fun homeLayoutEntries(
    layoutOrder: List<ItemPriorityConfig.ItemType>,
    isReversed: Boolean,
    widgets: List<PanelWidgetInfo>,
): List<HomeLayoutEntry> {
    val logicalOrder = if (isReversed) layoutOrder.reversed() else layoutOrder
    val anchors = logicalOrder.mapTo(HashSet()) { it.name }
    val widgetsByAnchor =
        widgets
            .filter { it.home != null }
            .sortedBy { it.home?.order ?: 0 }
            .groupBy { widget ->
                widget.home?.anchor?.takeIf { it in anchors } ?: HOME_WIDGET_ANCHOR_END
            }
    val logicalEntries =
        buildList {
            logicalOrder.forEach { itemType ->
                widgetsByAnchor[itemType.name]?.forEach { add(HomeLayoutEntry.Widget(it)) }
                add(HomeLayoutEntry.Item(itemType))
            }
            widgetsByAnchor[HOME_WIDGET_ANCHOR_END]?.forEach { add(HomeLayoutEntry.Widget(it)) }
        }
    return if (isReversed) logicalEntries.reversed() else logicalEntries
}

/**
 * Moves [widgetId] to [targetIndex] of the visual entries that remain once it is taken out, and
 * returns every Home widget with anchors and orders rewritten to match the new sequence.
 */
internal fun moveHomeWidget(
    visualEntries: List<HomeLayoutEntry>,
    widgetId: Int,
    targetIndex: Int,
    isReversed: Boolean,
): List<PanelWidgetInfo> {
    val dragged =
        visualEntries.firstOrNull { it is HomeLayoutEntry.Widget && it.widget.appWidgetId == widgetId }
            ?: return visualEntries.filterIsInstance<HomeLayoutEntry.Widget>().map { it.widget }
    val nextVisual =
        visualEntries.filterNot { it === dragged }.toMutableList().apply {
            add(targetIndex.coerceIn(0, size), dragged)
        }
    val logical = if (isReversed) nextVisual.reversed() else nextVisual
    val result = mutableListOf<PanelWidgetInfo>()
    val pending = mutableListOf<PanelWidgetInfo>()

    fun flush(anchor: String) {
        pending.forEachIndexed { index, widget ->
            val home = widget.home ?: return@forEachIndexed
            result += widget.copy(home = home.copy(anchor = anchor, order = index))
        }
        pending.clear()
    }

    logical.forEach { entry ->
        when (entry) {
            is HomeLayoutEntry.Widget -> pending += entry.widget
            is HomeLayoutEntry.Item -> flush(entry.itemType.name)
        }
    }
    flush(HOME_WIDGET_ANCHOR_END)
    return result
}

@Composable
internal fun rememberHomePinnedWidgets(enabled: Boolean): List<PanelWidgetInfo> {
    val context = LocalContext.current
    LaunchedEffect(Unit) { HomePinnedWidgetsStore.load(context) }
    val widgets by HomePinnedWidgetsStore.widgets.collectAsState()
    return if (enabled) widgets else emptyList()
}

/** Widget host for Home; only composed while Home has pinned widgets. */
@Composable
internal fun rememberHomeWidgetHost(): WidgetPanelHost {
    val appContext = LocalContext.current.applicationContext
    val host = remember(appContext) { WidgetPanelHost(appContext, HOME_WIDGET_HOST_ID) }
    DisposableEffect(host) {
        host.startListeningShared()
        onDispose { host.release() }
    }
    return host
}

private data class HomeEntryBounds(
    val top: Int,
    val height: Int,
)

private data class HomeWidgetDrag(
    val appWidgetId: Int,
    val startTop: Int,
    val startColumn: Int,
)

/**
 * Home content with pinned widgets interleaved between the regular Home items.
 *
 * Long-press a widget (or drag it while selected) to move it above or below any Home item;
 * while selected it also offers remove (Home only), settings, and resize handles.
 */
@Composable
internal fun HomeWidgetStack(
    layoutOrder: List<ItemPriorityConfig.ItemType>,
    isReversed: Boolean,
    widgets: List<PanelWidgetInfo>,
    showWidgets: Boolean,
    host: WidgetPanelHost,
    spacing: Dp,
    isScrollInProgress: () -> Boolean,
    modifier: Modifier = Modifier,
    leadingContent: @Composable () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
    itemContent: @Composable (ItemPriorityConfig.ItemType) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context.applicationContext) }
    val packageManager = context.packageManager

    var editingWidgetId by remember { mutableStateOf<Int?>(null) }
    // Preview of placements while dragging or resizing; persisted when the gesture ends.
    var liveWidgets by remember { mutableStateOf<List<PanelWidgetInfo>?>(null) }
    var isInteracting by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<HomeWidgetDrag?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var draggedCurrentTop by remember { mutableIntStateOf(0) }
    // Written during measure and read only from gesture callbacks, so it isn't snapshot state.
    val gridUnitWidthPx = remember { floatArrayOf(0f) }
    // Let Home draw before third-party RemoteViews are inflated.
    var showHostedWidgets by remember { mutableStateOf(false) }
    val bounds = remember { mutableMapOf<Any, HomeEntryBounds>() }

    val displayWidgets = liveWidgets ?: widgets
    val entries = homeLayoutEntries(layoutOrder, isReversed, if (showWidgets) displayWidgets else emptyList())

    val currentWidgets by rememberUpdatedState(widgets)
    val currentDisplayWidgets by rememberUpdatedState(displayWidgets)
    val currentLayoutOrder by rememberUpdatedState(layoutOrder)
    val currentIsReversed by rememberUpdatedState(isReversed)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        showHostedWidgets = true
    }

    // Drop the preview once the store reflects the committed placements.
    LaunchedEffect(widgets) {
        if (!isInteracting) liveWidgets = null
    }

    LaunchedEffect(showWidgets) {
        if (!showWidgets) editingWidgetId = null
    }

    // Home scrolls under the widgets: a touch that stops a fling, or one the scroll takes over,
    // must not turn into a long-press edit.
    val currentIsScrollInProgress by rememberUpdatedState(isScrollInProgress)
    DisposableEffect(host) {
        host.isScrollInProgressProvider = { currentIsScrollInProgress() }
        onDispose { host.isScrollInProgressProvider = { false } }
    }
    LaunchedEffect(host) {
        snapshotFlow { currentIsScrollInProgress() }
            .collect { inProgress -> if (inProgress) host.cancelAllPendingLongPresses() }
    }

    LaunchedEffect(widgets) {
        val editing = editingWidgetId ?: return@LaunchedEffect
        if (widgets.none { it.appWidgetId == editing }) editingWidgetId = null
    }

    BackHandler(enabled = editingWidgetId != null) { editingWidgetId = null }

    fun commit() {
        val final = liveWidgets
        isInteracting = false
        drag = null
        dragOffsetY = 0f
        if (final == null) return
        val committed = currentWidgets.associateBy { it.appWidgetId }
        val changes =
            final
                .filter { committed[it.appWidgetId]?.home != it.home }
                .associate { it.appWidgetId to it.home }
        if (changes.isEmpty()) {
            liveWidgets = null
        } else {
            HomePinnedWidgetsStore.updatePlacements(context, changes)
        }
    }

    fun startDrag(appWidgetId: Int) {
        val widget = currentDisplayWidgets.firstOrNull { it.appWidgetId == appWidgetId } ?: return
        val home = widget.home ?: return
        val startTop = bounds[appWidgetId]?.top ?: 0
        drag = HomeWidgetDrag(appWidgetId, startTop, home.column)
        draggedCurrentTop = startTop
        dragOffsetY = 0f
        isInteracting = true
        liveWidgets = currentDisplayWidgets
        editingWidgetId = appWidgetId
    }

    fun dragTo(
        totalDx: Float,
        totalDy: Float,
    ) {
        val active = drag ?: return
        dragOffsetY = totalDy
        val current = liveWidgets ?: currentWidgets
        val visual = homeLayoutEntries(currentLayoutOrder, currentIsReversed, current)
        val draggedHeight = bounds[active.appWidgetId]?.height ?: 0
        val draggedCenter = active.startTop + totalDy + draggedHeight / 2f
        val others =
            visual.filterNot {
                it is HomeLayoutEntry.Widget && it.widget.appWidgetId == active.appWidgetId
            }
        var targetIndex = others.size
        for ((index, entry) in others.withIndex()) {
            val entryBounds = bounds[entry.key] ?: continue
            if (entryBounds.height <= 0) continue
            if (entryBounds.top + entryBounds.height / 2f > draggedCenter) {
                targetIndex = index
                break
            }
        }
        val reordered = moveHomeWidget(visual, active.appWidgetId, targetIndex, currentIsReversed)
        val unitWidth = gridUnitWidthPx[0]
        liveWidgets =
            reordered.map { widget ->
                val home = widget.home
                if (widget.appWidgetId != active.appWidgetId || home == null || unitWidth <= 0f) {
                    widget
                } else {
                    val column =
                        (active.startColumn + (totalDx / unitWidth).roundToInt())
                            .coerceIn(0, WIDGET_PANEL_GRID_COLUMNS - home.columnSpan)
                    widget.copy(home = home.copy(column = column))
                }
            }
    }

    fun resizePreview(
        appWidgetId: Int,
        resize: WidgetGridResize,
    ) {
        isInteracting = true
        liveWidgets =
            (liveWidgets ?: currentWidgets).map { widget ->
                val home = widget.home
                if (widget.appWidgetId != appWidgetId || home == null) {
                    widget
                } else {
                    widget.copy(
                        home =
                            home.copy(
                                column = resize.column,
                                columnSpan = resize.columnSpan,
                                rowSpan = resize.rowSpan.coerceIn(1, WIDGET_PANEL_MAX_ROW_SPAN),
                            ),
                    )
                }
            }
    }

    val configureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    SideEffect {
        host.onWidgetLongPress = { id -> startDrag(id) }
        host.onWidgetDragMove = { id, dx, dy -> if (drag?.appWidgetId == id) dragTo(dx, dy) }
        host.onWidgetDragEnd = { _ -> commit() }
        host.onWidgetTouch = { id ->
            // Touching another widget while one is selected only leaves edit mode.
            val editing = editingWidgetId
            if (editing != null && editing != id) {
                editingWidgetId = null
                true
            } else {
                false
            }
        }
    }

    Layout(
        content = {
            key("home-stack-leading") {
                HomeStackColumn(spacing = spacing) { leadingContent() }
            }
            entries.forEach { entry ->
                key(entry.key) {
                    when (entry) {
                        is HomeLayoutEntry.Item ->
                            HomeStackColumn(
                                spacing = spacing,
                                modifier =
                                    Modifier.onPlaced { coordinates ->
                                        bounds[entry.key] =
                                            HomeEntryBounds(
                                                top = coordinates.positionInParent().y.roundToInt(),
                                                height = coordinates.size.height,
                                            )
                                    },
                            ) {
                                itemContent(entry.itemType)
                            }

                        is HomeLayoutEntry.Widget -> {
                            val widget = entry.widget
                            val appWidgetId = widget.appWidgetId
                            val isDragged = drag?.appWidgetId == appWidgetId
                            HomePinnedWidgetItem(
                                widget = widget,
                                showHostedWidget = showHostedWidgets,
                                appWidgetManager = appWidgetManager,
                                appWidgetHost = host,
                                packageManager = packageManager,
                                isEditing = editingWidgetId == appWidgetId,
                                modifier =
                                    Modifier
                                        .onPlaced { coordinates ->
                                            val top = coordinates.positionInParent().y.roundToInt()
                                            bounds[appWidgetId] =
                                                HomeEntryBounds(top = top, height = coordinates.size.height)
                                            if (drag?.appWidgetId == appWidgetId) draggedCurrentTop = top
                                        }.zIndex(if (isDragged) 2f else if (editingWidgetId == appWidgetId) 1f else 0f)
                                        .graphicsLayer {
                                            val active = drag
                                            translationY =
                                                if (active?.appWidgetId == appWidgetId) {
                                                    active.startTop + dragOffsetY - draggedCurrentTop
                                                } else {
                                                    0f
                                                }
                                        },
                                onMoveStart = { startDrag(appWidgetId) },
                                onMove = { dx, dy -> dragTo(dx, dy) },
                                onInteractionEnd = { commit() },
                                onResizePreview = { resize -> resizePreview(appWidgetId, resize) },
                                onRemove = {
                                    editingWidgetId = null
                                    HomePinnedWidgetsStore.updatePlacements(context, mapOf(appWidgetId to null))
                                },
                                onConfigure = { intent ->
                                    editingWidgetId = null
                                    runCatching { configureLauncher.launch(intent) }
                                },
                            )
                        }
                    }
                }
            }
            key("home-stack-trailing") {
                HomeStackColumn(spacing = spacing) { trailingContent() }
            }
        },
        modifier =
            modifier.pointerInput(Unit) {
                val marginPx = HomeWidgetEditTouchMargin.toPx()
                awaitEachGesture {
                    val down =
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val editing = editingWidgetId ?: return@awaitEachGesture
                    val editingBounds = bounds[editing]
                    val y = down.position.y
                    if (
                        editingBounds == null ||
                        y < editingBounds.top - marginPx ||
                        y > editingBounds.top + editingBounds.height + marginPx
                    ) {
                        editingWidgetId = null
                    }
                }
            },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val spacingPx = spacing.roundToPx()
        val positions = IntArray(placeables.size)
        var y = 0
        var hasVisibleChild = false
        placeables.forEachIndexed { index, placeable ->
            // Empty slots (sections with nothing to show) take no spacing. Growing children ease
            // into the gap so height animations don't jump by the full spacing on their first frame.
            if (placeable.height > 0 && hasVisibleChild) y += min(placeable.height, spacingPx)
            positions[index] = y
            y += placeable.height
            if (placeable.height > 0) hasVisibleChild = true
        }
        val width =
            (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = y.coerceIn(constraints.minHeight, constraints.maxHeight)
        val cellWidthPx =
            (width - WidgetPanelGridGap.toPx() * (WIDGET_PANEL_GRID_COLUMNS - 1)) / WIDGET_PANEL_GRID_COLUMNS
        gridUnitWidthPx[0] = cellWidthPx + WidgetPanelGridGap.toPx()
        layout(width, height) {
            placeables.forEachIndexed { index, placeable -> placeable.place(0, positions[index]) }
        }
    }
}

/** Column that stacks one Home item's blocks with the same spacing Home uses between items. */
@Composable
private fun HomeStackColumn(
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}

@Composable
private fun HomePinnedWidgetItem(
    widget: PanelWidgetInfo,
    showHostedWidget: Boolean,
    appWidgetManager: AppWidgetManager,
    appWidgetHost: WidgetPanelHost,
    packageManager: PackageManager,
    isEditing: Boolean,
    modifier: Modifier,
    onMoveStart: () -> Unit,
    onMove: (totalDx: Float, totalDy: Float) -> Unit,
    onInteractionEnd: () -> Unit,
    onResizePreview: (WidgetGridResize) -> Unit,
    onRemove: () -> Unit,
    onConfigure: (Intent) -> Unit,
) {
    val home = widget.home ?: return
    val providerInfo =
        remember(widget.appWidgetId, appWidgetManager) {
            appWidgetManager.getAppWidgetInfo(widget.appWidgetId)
        } ?: return
    val configureIntent =
        remember(providerInfo, widget.appWidgetId, packageManager) {
            providerInfo.configure
                ?.takeIf { isWidgetConfigureActivityAccessible(packageManager, it) }
                ?.let { configure ->
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget.appWidgetId)
                }
        }
    val density = LocalDensity.current
    val rowHeight = WidgetPanelGridRowHeight
    val gap = WidgetPanelGridGap

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - gap * (WIDGET_PANEL_GRID_COLUMNS - 1)) / WIDGET_PANEL_GRID_COLUMNS
        val minColumnSpan =
            remember(providerInfo, cellWidth) {
                val minWidthPx = providerInfo.minResizeWidth.takeIf { it > 0 } ?: providerInfo.minWidth
                calculateGridColumnSpan(
                    minWidthDp = with(density) { minWidthPx.toDp().value },
                    cellWidthDp = cellWidth.value,
                    gapDp = gap.value,
                )
            }
        val minRowSpan =
            remember(providerInfo) {
                val minHeightPx = providerInfo.minResizeHeight.takeIf { it > 0 } ?: providerInfo.minHeight
                calculateGridRowSpan(
                    minHeightDp = with(density) { minHeightPx.toDp().value },
                    rowHeightDp = rowHeight.value,
                    gapDp = gap.value,
                )
            }
        val width = cellWidth * home.columnSpan + gap * (home.columnSpan - 1)
        val height = rowHeight * home.rowSpan + gap * (home.rowSpan - 1)
        val x = (cellWidth + gap) * home.column
        val animatedX by animateDpAsState(x, WidgetLayoutMotion, label = "homeWidgetX")
        val animatedWidth by animateDpAsState(width, WidgetLayoutMotion, label = "homeWidgetWidth")
        val animatedHeight by animateDpAsState(height, WidgetLayoutMotion, label = "homeWidgetHeight")
        val editScale by animateFloatAsState(
            targetValue = if (isEditing) 1.02f else 1f,
            label = "homeWidgetEditScale",
        )

        Box(
            modifier =
                Modifier
                    .offset { IntOffset(x = animatedX.roundToPx(), y = 0) }
                    .size(width = animatedWidth, height = animatedHeight)
                    .graphicsLayer {
                        scaleX = editScale
                        scaleY = editScale
                    },
        ) {
            if (showHostedWidget) {
                HostedWidget(
                    appWidgetId = widget.appWidgetId,
                    providerInfo = providerInfo,
                    appWidgetManager = appWidgetManager,
                    appWidgetHost = appWidgetHost,
                    width = animatedWidth,
                    height = animatedHeight,
                    columnSpan = home.columnSpan,
                    rowSpan = home.rowSpan,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isEditing) {
                HomeWidgetEditOverlay(
                    column = home.column,
                    columnSpan = home.columnSpan,
                    rowSpan = home.rowSpan,
                    gridUnitWidthPx = with(density) { (cellWidth + gap).toPx() },
                    gridUnitHeightPx = with(density) { (rowHeight + gap).toPx() },
                    minColumnSpan = minColumnSpan,
                    minRowSpan = minRowSpan,
                    onMoveStart = onMoveStart,
                    onMove = onMove,
                    onInteractionEnd = onInteractionEnd,
                    onResizePreview = onResizePreview,
                    onRemove = onRemove,
                    onConfigure = configureIntent?.let { intent -> { onConfigure(intent) } },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.HomeWidgetEditOverlay(
    column: Int,
    columnSpan: Int,
    rowSpan: Int,
    gridUnitWidthPx: Float,
    gridUnitHeightPx: Float,
    minColumnSpan: Int,
    minRowSpan: Int,
    onMoveStart: () -> Unit,
    onMove: (totalDx: Float, totalDy: Float) -> Unit,
    onInteractionEnd: () -> Unit,
    onResizePreview: (WidgetGridResize) -> Unit,
    onRemove: () -> Unit,
    onConfigure: (() -> Unit)?,
) {
    val currentOnMoveStart by rememberUpdatedState(onMoveStart)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnInteractionEnd by rememberUpdatedState(onInteractionEnd)

    Box(
        modifier =
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    // Absorb taps on the selected widget so they don't reach the widget content.
                    detectTapGestures(onTap = {})
                },
    ) {
        WidgetEditBorder()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var totalDragX = 0f
                        var totalDragY = 0f
                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                totalDragY = 0f
                                currentOnMoveStart()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                                currentOnMove(totalDragX, totalDragY)
                            },
                            onDragEnd = { currentOnInteractionEnd() },
                            onDragCancel = { currentOnInteractionEnd() },
                        )
                    },
        )
        // Home is a vertical list, so height grows from the bottom edge only.
        listOf(ResizeEdge.Bottom, ResizeEdge.Start, ResizeEdge.End).forEach { edge ->
            EdgeResizeHandle(
                edge = edge,
                startColumn = column,
                startRow = 0,
                startColumnSpan = columnSpan,
                startRowSpan = rowSpan,
                gridUnitWidthPx = gridUnitWidthPx,
                gridUnitHeightPx = gridUnitHeightPx,
                minColumnSpan = minColumnSpan,
                minRowSpan = minRowSpan,
                onResizePreview = onResizePreview,
                onInteractionEnd = onInteractionEnd,
                modifier = Modifier.align(edge.alignment),
            )
        }
        WidgetEditActionButtons(onRemove = onRemove, onConfigure = onConfigure)
    }
}
