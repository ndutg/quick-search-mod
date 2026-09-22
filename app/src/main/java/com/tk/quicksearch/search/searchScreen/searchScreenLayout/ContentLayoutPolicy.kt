package com.tk.quicksearch.search.searchScreen.searchScreenLayout

import com.tk.quicksearch.search.core.SearchSection
import com.tk.quicksearch.search.core.ItemPriorityConfig
import com.tk.quicksearch.search.core.SearchSectionRegistry

internal fun homeLayoutOrder(
    baseLayoutOrder: List<ItemPriorityConfig.ItemType>,
    isReversed: Boolean,
    pinnedSectionOrder: List<SearchSection> = emptyList(),
): List<ItemPriorityConfig.ItemType> {
    val pinnedSectionRank = pinnedSectionOrder.withIndex().associate { (index, section) -> section to index }
    val logicalOrder =
        buildList {
            add(ItemPriorityConfig.ItemType.ERROR_BANNER)
            add(ItemPriorityConfig.ItemType.APPS_SECTION)
            add(ItemPriorityConfig.ItemType.UPCOMING_ALARM)
            add(ItemPriorityConfig.ItemType.RECENT_QUERIES)
            addAll(baseLayoutOrder.filter { it == ItemPriorityConfig.ItemType.OTHER_RESULTS })
            addAll(
                baseLayoutOrder
                    .filter { itemType ->
                        SearchSectionRegistry.sectionForItemType(itemType)
                            ?.let { it != SearchSection.APPS } == true
                    }.sortedBy { itemType ->
                        pinnedSectionRank[SearchSectionRegistry.sectionForItemType(itemType)]
                            ?: Int.MAX_VALUE
                    },
            )
        }
    return if (isReversed) logicalOrder.reversed() else logicalOrder
}

internal fun shouldRenderStandaloneTodayAgendaBeforeApps(isReversed: Boolean): Boolean = isReversed

internal fun shouldShowSearchHistoryTitle(hasAtAGlanceSection: Boolean): Boolean =
    hasAtAGlanceSection

internal fun shouldSkipRegularCalendarSectionForStandaloneTodayEvents(
    section: SearchSection,
    todayCalendarEventsCount: Int,
    pinnedCalendarEventsCount: Int,
): Boolean =
    section == SearchSection.CALENDAR &&
        todayCalendarEventsCount > 0 &&
        pinnedCalendarEventsCount == 0
