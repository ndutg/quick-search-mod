package com.tk.quicksearch.customInfo

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class CustomInfoRepeatUnit(val storageValue: String, val chronoUnit: ChronoUnit) {
    DAY("day", ChronoUnit.DAYS),
    WEEK("week", ChronoUnit.WEEKS),
    MONTH("month", ChronoUnit.MONTHS),
    YEAR("year", ChronoUnit.YEARS),
    ;

    companion object {
        fun fromStorageValue(value: String?): CustomInfoRepeatUnit? = entries.firstOrNull { it.storageValue == value }
    }
}

/** Runs every [interval] [unit]s, counted from the first scheduled run. */
data class CustomInfoRepeat(val unit: CustomInfoRepeatUnit, val interval: Int) {
    init {
        require(interval >= 1)
    }

    /**
     * The first run strictly after [afterMillis]. Every run is [anchorMillis] plus a whole number of
     * intervals, so a monthly run anchored on the 31st lands on the last day of shorter months and
     * returns to the 31st afterwards, and the local time of day holds across DST changes.
     */
    fun nextRunAfter(
        anchorMillis: Long,
        afterMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val anchor = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchorMillis), zone)
        val after = LocalDateTime.ofInstant(Instant.ofEpochMilli(afterMillis), zone)
        fun occurrence(index: Long): Long =
            anchor.plus(index * interval, unit.chronoUnit).atZone(zone).toInstant().toEpochMilli()
        // Month and year arithmetic clamps day-of-month, so start one step early and walk forward.
        var index = (unit.chronoUnit.between(anchor, after) / interval - 1).coerceAtLeast(0L)
        while (occurrence(index) <= afterMillis) index++
        return occurrence(index)
    }

    companion object {
        val DAILY = CustomInfoRepeat(CustomInfoRepeatUnit.DAY, 1)
        val WEEKLY = CustomInfoRepeat(CustomInfoRepeatUnit.WEEK, 1)
        val MONTHLY = CustomInfoRepeat(CustomInfoRepeatUnit.MONTH, 1)
        val YEARLY = CustomInfoRepeat(CustomInfoRepeatUnit.YEAR, 1)
    }
}
