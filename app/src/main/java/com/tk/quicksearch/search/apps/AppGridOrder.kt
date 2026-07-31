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
