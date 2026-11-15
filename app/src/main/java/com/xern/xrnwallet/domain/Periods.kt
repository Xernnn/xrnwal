package com.xern.xrnwallet.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class WeekendRule(val label: String) {
    NO_CHANGE("No change"),
    EARLIER("Forward"),
    LATER("Backward"),
}

enum class Belonging(val label: String) {
    START_MONTH("Start date this month"),
    END_MONTH("Start date next month"),
}

/** The settings that decide where a "month" starts. */
data class PeriodRules(
    val startDay: Int = 1,
    val weekend: WeekendRule = WeekendRule.NO_CHANGE,
    val belonging: Belonging = Belonging.START_MONTH,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    private fun anchor(month: YearMonth): LocalDate {
        val date = month.atDay(minOf(startDay, month.lengthOfMonth()))
        return when (weekend) {
            WeekendRule.NO_CHANGE -> date
            WeekendRule.EARLIER -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))
                .takeIf { date.dayOfWeek in WEEKEND } ?: date
            WeekendRule.LATER -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .takeIf { date.dayOfWeek in WEEKEND } ?: date
        }
    }

    /** The first and last day of the accounting month labelled [month]. */
    fun monthRange(month: YearMonth): DateRange {
        val first = if (belonging == Belonging.START_MONTH) month else month.minusMonths(1)
        return DateRange(anchor(first), anchor(first.plusMonths(1)).minusDays(1))
    }

    /**
     * The accounting month that contains [date]. Months start in order and each ends where the next
     * begins, so there is always exactly one. It walks to it, because a weekend can move a start into
     * the calendar month before or after, putting a date up to two months from its own.
     */
    fun monthOf(date: LocalDate): YearMonth {
        var first = YearMonth.from(date)
        while (anchor(first) > date) first = first.minusMonths(1)
        while (anchor(first.plusMonths(1)) <= date) first = first.plusMonths(1)
        return if (belonging == Belonging.START_MONTH) first else first.plusMonths(1)
    }

    fun weekRange(date: LocalDate): DateRange {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return DateRange(start, start.plusDays(6))
    }

    private companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate) = date >= start && date <= endInclusive

    /** Calendar days, so a month with a daylight-saving change is still its full length. */
    val days: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1
}

sealed interface Period {
    fun range(rules: PeriodRules): DateRange
    fun label(rules: PeriodRules, today: LocalDate): String

    /** The same kind of period, [steps] away. A custom range moves by its own length. */
    fun shifted(steps: Int): Period

    data class Week(val anyDay: LocalDate) : Period {
        override fun range(rules: PeriodRules) = rules.weekRange(anyDay)
        /** Counted from the week's fourth day, so a week starting on Sunday keeps its usual number. */
        fun number(rules: PeriodRules): Int = middle(rules).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

        override fun label(rules: PeriodRules, today: LocalDate): String {
            val year = middle(rules).get(IsoFields.WEEK_BASED_YEAR)
            return if (year == today.year) "Week ${number(rules)}" else "Week ${number(rules)} $year"
        }

        private fun middle(rules: PeriodRules) = range(rules).start.plusDays(3)

        override fun shifted(steps: Int) = Week(anyDay.plusWeeks(steps.toLong()))
    }

    data class Month(val month: YearMonth) : Period {
        override fun range(rules: PeriodRules) = rules.monthRange(month)
        override fun label(rules: PeriodRules, today: LocalDate): String {
            val name = month.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))
            return if (month.year == today.year) name else "$name ${month.year}"
        }

        override fun shifted(steps: Int) = Month(month.plusMonths(steps.toLong()))
    }

    data class Year(val year: Int) : Period {
        override fun range(rules: PeriodRules) = DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        override fun label(rules: PeriodRules, today: LocalDate) = year.toString()

        override fun shifted(steps: Int) = Year(year + steps)
    }

    data class Custom(val start: LocalDate, val end: LocalDate) : Period {
        override fun range(rules: PeriodRules) = DateRange(start, end)
        override fun label(rules: PeriodRules, today: LocalDate) = Dates.span(start, end, today)

        override fun shifted(steps: Int): Period {
            val days = (ChronoUnit.DAYS.between(start, end) + 1) * steps
            return Custom(start.plusDays(days), end.plusDays(days))
        }
    }
}

object Dates {
    private val dayHeader = DateTimeFormatter.ofPattern("MMM d EEE", Locale.ENGLISH)
    private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    private val time12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val time24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /** Whether times read 16:00 and 09:00 rather than 4:00 PM and 9:00 AM: the phone's own setting, read as the app starts and resumes. */
    @Volatile var use24Hour: Boolean = false

    private val time: DateTimeFormatter get() = if (use24Hour) time24 else time12
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun dayHeader(date: LocalDate): String = date.format(dayHeader)
    fun short(date: LocalDate): String = date.format(shortDate)

    /** "Nov 2 - 8", or "Oct 26 - Nov 1" across two months, with the year when it isn't this year's. */
    fun span(start: LocalDate, end: LocalDate, today: LocalDate): String {
        val last = if (start.month == end.month) end.dayOfMonth.toString() else short(end)
        val year = if (start.year == today.year && end.year == today.year) "" else ", ${end.year}"
        return "${short(start)} - $last$year"
    }

    fun iso(date: LocalDate): String = date.format(iso)
    fun time(value: java.time.LocalDateTime): String = value.format(time)
    fun time(value: java.time.LocalTime): String = value.format(time)
    fun full(value: java.time.LocalDateTime): String = "${short(value.toLocalDate())} ${time(value)}"
}
