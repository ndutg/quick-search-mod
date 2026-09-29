package com.tk.quicksearch.widgets.countdownWidget

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import com.tk.quicksearch.R
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class CountdownUnit { DAY, WEEK, MONTH, YEAR }

internal data class CountdownAmount(
    val value: Double,
    val unit: CountdownUnit,
)

/** What the widget shows for a date, before localization. */
internal sealed interface CountdownValue {
    data object Today : CountdownValue

    /** [amounts] from largest unit to smallest; [isFuture] picks "in …" over "… ago". */
    data class Relative(
        val amounts: List<CountdownAmount>,
        val isFuture: Boolean,
    ) : CountdownValue

    /** [fraction] of the time from the start until the date that has passed, from 0 to 1. */
    data class Progress(
        val fraction: Double,
    ) : CountdownValue
}

internal object CountdownCalculator {
    private const val DAYS_PER_WEEK = 7.0

    fun compute(
        format: CountdownFormat,
        today: LocalDate,
        target: LocalDate,
        nowMillis: Long,
        startMillis: Long,
        zone: ZoneId,
    ): CountdownValue {
        if (format == CountdownFormat.PROGRESS) {
            val endMillis = target.atStartOfDay(zone).toInstant().toEpochMilli()
            val span = endMillis - startMillis
            val fraction = if (span <= 0L) 1.0 else ((nowMillis - startMillis).toDouble() / span).coerceIn(0.0, 1.0)
            return CountdownValue.Progress(fraction)
        }
        if (target == today) return CountdownValue.Today
        val isFuture = target.isAfter(today)
        val from = if (isFuture) today else target
        val to = if (isFuture) target else today
        val amounts =
            when (format) {
                CountdownFormat.DAYS ->
                    listOf(CountdownAmount(ChronoUnit.DAYS.between(from, to).toDouble(), CountdownUnit.DAY))
                CountdownFormat.WEEKS ->
                    listOf(
                        CountdownAmount(
                            roundToTenth(ChronoUnit.DAYS.between(from, to) / DAYS_PER_WEEK),
                            CountdownUnit.WEEK,
                        ),
                    )
                CountdownFormat.MONTHS -> listOf(CountdownAmount(fractionalMonths(from, to), CountdownUnit.MONTH))
                CountdownFormat.YEARS -> listOf(CountdownAmount(fractionalYears(from, to), CountdownUnit.YEAR))
                CountdownFormat.DETAILED, CountdownFormat.PROGRESS -> detailed(from, to)
            }
        return CountdownValue.Relative(amounts, isFuture)
    }

    /** The largest non-zero calendar unit and the one after it, skipping it when zero. */
    private fun detailed(
        from: LocalDate,
        to: LocalDate,
    ): List<CountdownAmount> {
        val period = Period.between(from, to)
        val parts =
            listOf(
                CountdownAmount(period.years.toDouble(), CountdownUnit.YEAR),
                CountdownAmount(period.months.toDouble(), CountdownUnit.MONTH),
                CountdownAmount(period.days.toDouble(), CountdownUnit.DAY),
            )
        val first = parts.indexOfFirst { it.value > 0 }
        return parts.drop(first).take(2).filter { it.value > 0 }
    }

    /** Whole months plus the leftover days as a share of the month they fall in. */
    private fun fractionalMonths(
        from: LocalDate,
        to: LocalDate,
    ): Double {
        val months = ChronoUnit.MONTHS.between(from, to)
        val anchor = from.plusMonths(months)
        val leftoverDays = ChronoUnit.DAYS.between(anchor, to)
        val monthLength = ChronoUnit.DAYS.between(anchor, anchor.plusMonths(1))
        return roundToTenth(months + leftoverDays.toDouble() / monthLength)
    }

    /** Whole years plus the leftover days as a share of the year they fall in. */
    private fun fractionalYears(
        from: LocalDate,
        to: LocalDate,
    ): Double {
        val years = ChronoUnit.YEARS.between(from, to)
        val anchor = from.plusYears(years)
        val leftoverDays = ChronoUnit.DAYS.between(anchor, to)
        val yearLength = ChronoUnit.DAYS.between(anchor, anchor.plusYears(1))
        return roundToTenth(years + leftoverDays.toDouble() / yearLength)
    }

    /** One decimal, never rounding a few days down to "0 months". */
    private fun roundToTenth(value: Double): Double = ((value * 10).roundToLong() / 10.0).coerceAtLeast(0.1)
}

/** Localized widget text for a [CountdownValue]. */
internal object CountdownTextFormatter {
    fun format(
        context: Context,
        value: CountdownValue,
    ): String {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        return when (value) {
            CountdownValue.Today -> context.getString(R.string.calendar_relative_today)
            is CountdownValue.Progress ->
                NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(value.fraction)
            is CountdownValue.Relative -> {
                val numberFormat = NumberFormat.getInstance(locale).apply { maximumFractionDigits = 1 }
                val measureFormat = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE, numberFormat)
                val amount =
                    value.amounts.joinToString(" ") { measureFormat.format(Measure(it.value.asNumber(), it.unit.icu)) }
                context.getString(
                    if (value.isFuture) R.string.calendar_relative_in_format else R.string.calendar_relative_ago_format,
                    amount,
                )
            }
        }
    }

    /** Whole values as integers, so plural rules pick "1 year" rather than "1.0 years". */
    private fun Double.asNumber(): Number = if (abs(this % 1.0) < 1e-9) toLong() else this

    private val CountdownUnit.icu: MeasureUnit
        get() =
            when (this) {
                CountdownUnit.DAY -> MeasureUnit.DAY
                CountdownUnit.WEEK -> MeasureUnit.WEEK
                CountdownUnit.MONTH -> MeasureUnit.MONTH
                CountdownUnit.YEAR -> MeasureUnit.YEAR
            }
}
