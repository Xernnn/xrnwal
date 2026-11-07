package com.xern.xrnwallet.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class RepeatKind(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    EVERY_N_DAYS("Every N Days"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
}

/** The unit on the "Every N" wheel. */
enum class RepeatUnit(val label: String) {
    DAYS("Days"),
    WEEKS("Weeks"),
    MONTHS("Months"),
    YEARS("Years"),
}

/**
 * [value] is the weekday (1-7) for WEEKLY, the day count for EVERY_N_DAYS, the day of month for MONTHLY.
 * [every] skips periods for WEEKLY, MONTHLY and YEARLY: 2 means every second one.
 */
data class RepeatRule(val kind: RepeatKind, val value: Int = 1, val every: Int = 1) {
    private val step: Long get() = every.coerceAtLeast(1).toLong()

    /** The n-th occurrence, counting from zero at [start]. */
    fun occurrence(start: LocalDate, index: Int): LocalDate = when (kind) {
        RepeatKind.DAILY -> start.plusDays(index * step)
        RepeatKind.WEEKLY -> {
            val first = start.plusDays(((value - start.dayOfWeek.value + 7) % 7).toLong())
            first.plusWeeks(index * step)
        }
        RepeatKind.EVERY_N_DAYS -> start.plusDays(index.toLong() * value.coerceAtLeast(1))
        RepeatKind.MONTHLY -> {
            val base = YearMonth.from(start)
            val firstMonth = if (value < start.dayOfMonth) base.plusMonths(1) else base
            val month = firstMonth.plusMonths(index * step)
            month.atDay(minOf(value, month.lengthOfMonth()))
        }
        RepeatKind.YEARLY -> start.plusYears(index * step)
    }

    fun occurrences(start: LocalDate, count: Int): List<LocalDate> = List(count) { occurrence(start, it) }

    /** How this rule reads on the "Every N" wheel. */
    val wheelCount: Int
        get() = if (kind == RepeatKind.EVERY_N_DAYS) value else every

    val wheelUnit: RepeatUnit
        get() = when (kind) {
            RepeatKind.DAILY, RepeatKind.EVERY_N_DAYS -> RepeatUnit.DAYS
            RepeatKind.WEEKLY -> RepeatUnit.WEEKS
            RepeatKind.MONTHLY -> RepeatUnit.MONTHS
            RepeatKind.YEARLY -> RepeatUnit.YEARS
        }

    val shortLabel: String
        get() = when {
            kind == RepeatKind.EVERY_N_DAYS -> "Every $value Days"
            every > 1 -> "Every $every ${wheelUnit.label}"
            else -> kind.label
        }

    val longLabel: String
        get() = when (kind) {
            RepeatKind.WEEKLY -> {
                val day = DayOfWeek.of(value).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                if (every > 1) "$shortLabel on $day" else "Weekly $day"
            }
            RepeatKind.MONTHLY -> "${if (every > 1) shortLabel else "Monthly"} on day $value"
            else -> shortLabel
        }

    companion object {
        /** The rule for "Every [count] [unit]", taking its weekday or day of month from [start]. */
        fun ofWheel(count: Int, unit: RepeatUnit, start: LocalDate): RepeatRule = when (unit) {
            RepeatUnit.DAYS ->
                if (count == 1) RepeatRule(RepeatKind.DAILY) else RepeatRule(RepeatKind.EVERY_N_DAYS, count)
            RepeatUnit.WEEKS -> RepeatRule(RepeatKind.WEEKLY, start.dayOfWeek.value, count)
            RepeatUnit.MONTHS -> RepeatRule(RepeatKind.MONTHLY, start.dayOfMonth, count)
            RepeatUnit.YEARS -> RepeatRule(RepeatKind.YEARLY, every = count)
        }
    }
}

enum class SavingsType(val label: String) {
    FIXED("Fixed"),
    INCREMENT("Increment"),
    FLEXIBLE("Flexible"),
}

object Savings {
    /** Evenly split, rounded up so the plan reaches the target. */
    fun fixedDeposit(targetCents: Long, repetitions: Int): Long {
        if (repetitions <= 0) return 0
        return BigDecimal(targetCents).divide(BigDecimal(repetitions), 0, RoundingMode.UP).toLong()
    }

    /** Deposits of start, 2 x start, 3 x start and so on total start x n(n+1)/2. */
    fun incrementTarget(startCents: Long, repetitions: Int): Long =
        startCents * repetitions * (repetitions + 1) / 2

    fun incrementDeposit(startCents: Long, index: Int): Long = startCents * (index + 1)
}
