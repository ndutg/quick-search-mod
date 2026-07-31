package com.tk.quicksearch.search.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppGridOrderTest {
    @Test
    fun `one handed visual order reverses grid rows`() {
        val apps = (0 until 10).toList()

        val visualOrder = appsInVisualGridOrder(apps, columns = 5, oneHandedMode = true)

        assertEquals(listOf(5, 6, 7, 8, 9, 0, 1, 2, 3, 4), visualOrder)
    }

    @Test
    fun `persisted order restores one handed visual order with partial row`() {
        val apps = (0 until 12).toList()
        val visualOrder = appsInVisualGridOrder(apps, columns = 5, oneHandedMode = true)

        val persistedOrder =
                appsInPersistedGridOrder(visualOrder, columns = 5, oneHandedMode = true)

        assertEquals(apps, persistedOrder)
    }

    @Test
    fun `visual reorder converts back without changing dragged position`() {
        val apps = (0 until 12).toList()
        val reorderedVisualApps =
                appsInVisualGridOrder(apps, columns = 5, oneHandedMode = true)
                        .toMutableList()
                        .apply { add(2, removeAt(7)) }

        val persistedOrder =
                appsInPersistedGridOrder(reorderedVisualApps, columns = 5, oneHandedMode = true)

        assertEquals(
                reorderedVisualApps,
                appsInVisualGridOrder(persistedOrder, columns = 5, oneHandedMode = true),
        )
        assertEquals(0, reorderedVisualApps[2])
    }

    @Test
    fun `normal mode preserves the same order`() {
        val apps = (0 until 8).toList()

        assertEquals(apps, appsInVisualGridOrder(apps, columns = 5, oneHandedMode = false))
        assertEquals(apps, appsInPersistedGridOrder(apps, columns = 5, oneHandedMode = false))
    }
}
