package com.tk.quicksearch.search.apps

internal fun <T> appsInVisualGridOrder(
        apps: List<T>,
        columns: Int,
        oneHandedMode: Boolean,
): List<T> {
    if (!oneHandedMode || apps.isEmpty()) return apps
    return apps.chunked(columns.coerceAtLeast(1)).asReversed().flatten()
}

internal fun <T> appsInPersistedGridOrder(
        visualApps: List<T>,
        columns: Int,
        oneHandedMode: Boolean,
): List<T> {
    if (!oneHandedMode || visualApps.isEmpty()) return visualApps

    val rowSizes =
            visualApps
                    .indices
                    .chunked(columns.coerceAtLeast(1))
                    .map { it.size }
                    .asReversed()
    var visualIndex = 0
    val rowsInVisualOrder =
            rowSizes.map { rowSize ->
                visualApps.subList(visualIndex, visualIndex + rowSize).also {
                    visualIndex += rowSize
                }
            }
    return rowsInVisualOrder.asReversed().flatten()
}

private const val PinnedGridGapKeyPrefix = "grid_gap:"

/** Key of an empty cell in the Pinned tab's grid order; unique by [index]. */
internal fun pinnedGridGapKey(index: Int): String = "$PinnedGridGapKeyPrefix$index"

internal fun isPinnedGridGapKey(key: String): Boolean = key.startsWith(PinnedGridGapKeyPrefix)

/**
 * [items] without trailing gaps, then padded with new gaps to fill its last row, so every empty
 * cell of the grid is an item that can be dropped on. [gap] creates the gap numbered by its index
 * among the gaps.
 */
internal fun <T> itemsFilledToGridRows(
        items: List<T>,
        columns: Int,
        isGap: (T) -> Boolean,
        gap: (Int) -> T,
): List<T> {
    val trimmed = items.dropLastWhile(isGap)
    val rowSize = columns.coerceAtLeast(1)
    val paddingCount = (rowSize - trimmed.size % rowSize) % rowSize
    val gapCount = trimmed.count(isGap)
    return trimmed + List(paddingCount) { gap(gapCount + it) }
}

/**
 * The gap keys in [order] that come before its last key in [presentKeys]. Gaps after every present
 * item are dropped: they would push newly added items, which have no place in [order], after them.
 */
internal fun gapKeysBeforeLastItem(
        order: List<String>,
        presentKeys: Set<String>,
): List<String> {
    val lastItemIndex = order.indexOfLast { it in presentKeys }
    return order.take(lastItemIndex.coerceAtLeast(0)).filter(::isPinnedGridGapKey)
}

/** [ordered] with each of [added] taking the first remaining gap, or appended once none is left. */
internal fun <T> itemsFillingGaps(
        ordered: List<T>,
        added: List<T>,
        isGap: (T) -> Boolean,
): List<T> {
    val result = ordered.toMutableList()
    var searchFrom = 0
    added.forEach { item ->
        val gapIndex = (searchFrom until result.size).firstOrNull { isGap(result[it]) }
        if (gapIndex == null) {
            result.add(item)
        } else {
            result[gapIndex] = item
            searchFrom = gapIndex + 1
        }
    }
    return result
}
