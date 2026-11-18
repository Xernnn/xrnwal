package com.xern.xrnwallet.data

import com.xern.xrnwallet.domain.DateRange
import com.xern.xrnwallet.domain.Period
import com.xern.xrnwallet.domain.PeriodRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The figures behind the Reports charts that look past one period's totals. */
object Insights {
    /** The total so far at each step of [amounts]. */
    fun running(amounts: List<Long>): List<Long> {
        var sum = 0L
        return amounts.map { sum += it; sum }
    }

    /**
     * How many of [period]'s steps (days, or a year's months) have begun by [today]: all of them for a
     * period that's over, none for one still to come.
     */
    fun elapsed(period: Period, range: DateRange, today: LocalDate, steps: Int): Int = when {
        today < range.start -> 0
        today > range.endInclusive -> steps
        else -> Stats.averageUnits(period, range, today).toInt().coerceIn(0, steps)
    }

    /** The twelve accounting months a period's month charts cover: a year's own, else those up to the period's last month. */
    fun window(period: Period, range: DateRange, rules: PeriodRules): List<YearMonth> {
        val last = if (period is Period.Year) YearMonth.of(period.year, 12) else rules.monthOf(range.endInclusive)
        return (11 downTo 0).map { last.minusMonths(it.toLong()) }
    }

    data class Flow(val month: YearMonth, val income: Long, val expense: Long) {
        val net: Long get() = income - expense
    }

    /** Income and expenses in each of [months], counted as accounting months. */
    fun flows(bills: List<Bill>, months: List<YearMonth>, rules: PeriodRules): List<Flow> = months.map { month ->
        val totals = bills.inRange(rules.monthRange(month)).totals()
        Flow(month, totals.income, totals.expense)
    }

    /** One band of the stacked month bars: a top-level category, or everything else when [category] is null. */
    data class Slice(val category: Category?, val perMonth: List<Long>) {
        val total: Long get() = perMonth.sum()
    }

    /**
     * Expenses in each of [months], split into the [top] categories over all of them, largest first,
     * and one slice of the rest, uncategorised bills included. Subcategories count towards their parent, as Reports' ranking folds them.
     */
    fun mix(snapshot: Snapshot, bills: List<Bill>, months: List<YearMonth>, rules: PeriodRules, top: Int = 5): List<Slice> {
        val ranges = months.map { rules.monthRange(it) }
        val byCategory = HashMap<Long?, LongArray>()
        bills.forEach { bill ->
            if (bill.type != BillType.EXPENSE) return@forEach
            val index = ranges.indexOfFirst { bill.date in it }
            if (index < 0) return@forEach
            val key = snapshot.topCategory(bill.categoryId)?.id
            byCategory.getOrPut(key) { LongArray(months.size) }[index] += bill.baseCents
        }
        // Bills with no category go with the rest, so there is only ever one slice without a name.
        val ranked = byCategory.entries.filter { snapshot.categoryById[it.key] != null }.sortedByDescending { it.value.sum() }
        val kept = ranked.take(top).map { Slice(snapshot.categoryById[it.key], it.value.toList()) }
        val rest = ranked.drop(top) + byCategory.entries.filter { snapshot.categoryById[it.key] == null }
        if (rest.isEmpty()) return kept
        val other = LongArray(months.size)
        rest.forEach { entry -> entry.value.forEachIndexed { i, cents -> other[i] += cents } }
        return kept + Slice(null, other.toList())
    }

    /** What was spent (or earned, with [type]) on each day of [range] that had any. */
    fun daily(bills: List<Bill>, range: DateRange, type: Int = BillType.EXPENSE): Map<LocalDate, Long> =
        bills.filter { it.type == type && it.date in range }.groupBy { it.date }.mapValues { (_, day) -> day.sumOf { it.baseCents } }

    /**
     * Where the shades of the day chart change: above each figure a day is a shade darker. Taken from
     * the days with any spending, at 40, 70 and 90 in a hundred, so a single rent day doesn't leave
     * every other day the palest shade.
     */
    fun shadeCuts(values: Collection<Long>): List<Long> {
        val spent = values.filter { it > 0 }.sorted()
        if (spent.isEmpty()) return emptyList()
        return listOf(0.4, 0.7, 0.9).map { spent[((spent.size - 1) * it).toInt()] }
    }

    /** 0 for nothing, else 1 to 4. */
    fun shade(value: Long, cuts: List<Long>): Int = if (value <= 0) 0 else 1 + cuts.count { value > it }

    /**
     * The average spent on each weekday over the days of [range] up to [today]: a weekday's total
     * divided by how many times it has come round, days with nothing spent included. Bills a recurring
     * rule recorded are left out: rent falls on a date, not a weekday, and one would make whichever
     * weekday the 1st was look like the dearest.
     */
    fun weekdayAverages(bills: List<Bill>, range: DateRange, today: LocalDate, type: Int = BillType.EXPENSE): Map<DayOfWeek, Long> {
        val end = minOf(range.endInclusive, maxOf(today, range.start))
        val days = generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= end }.toList()
        val totals = daily(bills.filter { it.recurringId == null }, DateRange(range.start, end), type)
        return DayOfWeek.entries.associateWith { weekday ->
            val matching = days.filter { it.dayOfWeek == weekday }
            if (matching.isEmpty()) 0L else Math.round(matching.sumOf { totals[it] ?: 0L }.toDouble() / matching.size)
        }
    }
}
