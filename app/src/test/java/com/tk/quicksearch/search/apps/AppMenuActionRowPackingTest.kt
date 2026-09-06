package com.tk.quicksearch.search.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [packActionRows]: SpeedBump must sit directly left of Exclude, and empty grid slots
 * must appear only at the end of the final row.
 */
class AppMenuActionRowPackingTest {
    private fun item(
        key: AppMenuItemKey,
        span: Int = 1,
    ) = AppMenuItem(key = key, textResId = 0, icon = {}, onClick = {}, span = span)

    private fun keys(rows: List<List<AppMenuItem>>) = rows.map { row -> row.map { it.key } }

    private fun assertNoInteriorGaps(rows: List<List<AppMenuItem>>) {
        rows.dropLast(1).forEach { row ->
            assertEquals(ActionGridColumns, row.sumOf { it.span })
        }
        assertTrue(rows.last().sumOf { it.span } <= ActionGridColumns)
    }

    private fun assertSpeedBumpLeftOfExclude(rows: List<List<AppMenuItem>>) {
        val row = rows.single { row -> row.any { it.key == AppMenuItemKey.EXCLUDE } }
        val speedBumpIndex = row.indexOfFirst { it.key == AppMenuItemKey.SPEED_BUMP }
        val excludeIndex = row.indexOfFirst { it.key == AppMenuItemKey.EXCLUDE }
        assertEquals(excludeIndex - 1, speedBumpIndex)
    }

    /** Pin, nickname, icon, trigger, split screen, add to home, app info, bump, exclude, notif, uninstall. */
    private fun launchableAppItems() =
        listOf(
            item(AppMenuItemKey.PIN),
            item(AppMenuItemKey.NICKNAME),
            item(AppMenuItemKey.ICON),
            item(AppMenuItemKey.TRIGGER),
            item(AppMenuItemKey.SPLIT_SCREEN),
            item(AppMenuItemKey.ADD_TO_HOME),
            item(AppMenuItemKey.APP_INFO),
            item(AppMenuItemKey.SPEED_BUMP),
            item(AppMenuItemKey.EXCLUDE),
            item(AppMenuItemKey.NOTIFICATIONS, span = 2),
            item(AppMenuItemKey.UNINSTALL),
        )

    @Test
    fun `normal launchable app packs into full rows`() {
        val rows = packActionRows(launchableAppItems())

        assertEquals(
            listOf(
                listOf(AppMenuItemKey.PIN, AppMenuItemKey.NICKNAME, AppMenuItemKey.ICON),
                listOf(AppMenuItemKey.TRIGGER, AppMenuItemKey.SPLIT_SCREEN, AppMenuItemKey.ADD_TO_HOME),
                listOf(AppMenuItemKey.APP_INFO, AppMenuItemKey.SPEED_BUMP, AppMenuItemKey.EXCLUDE),
                listOf(AppMenuItemKey.NOTIFICATIONS, AppMenuItemKey.UNINSTALL),
            ),
            keys(rows),
        )
        assertNoInteriorGaps(rows)
        assertSpeedBumpLeftOfExclude(rows)
    }

    @Test
    fun `system app without uninstall leaves the only gap on the last row`() {
        val rows = packActionRows(launchableAppItems().filterNot { it.key == AppMenuItemKey.UNINSTALL })

        assertNoInteriorGaps(rows)
        assertSpeedBumpLeftOfExclude(rows)
        assertEquals(listOf(AppMenuItemKey.NOTIFICATIONS), keys(rows).last())
    }

    @Test
    fun `work profile app without split screen keeps the pair together`() {
        val rows = packActionRows(launchableAppItems().filterNot { it.key == AppMenuItemKey.SPLIT_SCREEN })

        assertNoInteriorGaps(rows)
        assertSpeedBumpLeftOfExclude(rows)
    }

    @Test
    fun `non launchable app without SpeedBump still packs exclude`() {
        val rows =
            packActionRows(
                listOf(
                    item(AppMenuItemKey.PIN),
                    item(AppMenuItemKey.NICKNAME),
                    item(AppMenuItemKey.ICON),
                    item(AppMenuItemKey.TRIGGER),
                    item(AppMenuItemKey.APP_INFO),
                    item(AppMenuItemKey.EXCLUDE),
                ),
            )

        assertEquals(
            listOf(
                listOf(AppMenuItemKey.PIN, AppMenuItemKey.NICKNAME, AppMenuItemKey.ICON),
                listOf(AppMenuItemKey.TRIGGER, AppMenuItemKey.APP_INFO, AppMenuItemKey.EXCLUDE),
            ),
            keys(rows),
        )
        assertNoInteriorGaps(rows)
    }

    @Test
    fun `quick search itself only offers app info bump and exclude`() {
        val rows =
            packActionRows(
                listOf(
                    item(AppMenuItemKey.ADD_TO_HOME),
                    item(AppMenuItemKey.APP_INFO),
                    item(AppMenuItemKey.EXCLUDE),
                    item(AppMenuItemKey.NOTIFICATIONS, span = 2),
                ),
            )

        assertNoInteriorGaps(rows)
        assertEquals(
            listOf(
                listOf(AppMenuItemKey.ADD_TO_HOME, AppMenuItemKey.APP_INFO, AppMenuItemKey.EXCLUDE),
                listOf(AppMenuItemKey.NOTIFICATIONS),
            ),
            keys(rows),
        )
    }
}
