package com.xern.xrnwal.work

import com.xern.xrnwal.data.Bill
import com.xern.xrnwal.data.BillType
import com.xern.xrnwal.data.Settings
import com.xern.xrnwal.data.Snapshot
import com.xern.xrnwal.data.Stats
import com.xern.xrnwal.data.inRange
import com.xern.xrnwal.data.totals
import com.xern.xrnwal.domain.DateRange
import com.xern.xrnwal.domain.Period
import com.xern.xrnwal.ui.activeLedger
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** One day's spending, for a widget's bar chart. */
data class DaySpend(val date: LocalDate, val cents: Long)

/** A bar's height as a share of the chart's, and whether its day spent more than the chart has room for. */
data class Bar(val height: Float, val clipped: Boolean)

/**
 * This month's spending against what it may spend: the overall budget when one is set and budgets are
 * on, else the month's income. [limit] is 0 when there is neither.
 */
data class Pace(
    val spent: Long,
    val limit: Long,
    val fromBudget: Boolean,
    val day: Int,
    val days: Int,
    val spentToday: Long,
) {
    /** Days from today to the month's end, today included. */
    val daysLeft: Int get() = (days - day + 1).coerceAtLeast(1)

    /** Share of the limit spent, which may pass 1. */
    val share: Float get() = if (limit <= 0) 0f else spent.toFloat() / limit

    /** Share of the month's days gone, today included. */
    val elapsed: Float get() = if (days <= 0) 0f else day.toFloat() / days

    /** Spending faster than the days pass. */
    val ahead: Boolean get() = limit > 0 && share > elapsed

    val left: Long get() = limit - spent

    /** What each day from today on may spend and still stay within the limit. */
    val perDay: Long get() = left.coerceAtLeast(0) / daysLeft

    /** What today may spend: what the limit had left this morning, shared over the days still to come. */
    val allowanceToday: Long get() = (limit - (spent - spentToday)).coerceAtLeast(0) / daysLeft
}

/** A formatted amount split so the whole number can be set larger than its symbol and decimals. */
data class MoneyParts(val lead: String, val whole: String, val fraction: String) {
    companion object {
        /** "-$1.234,5" with a decimal comma is "-$", "1.234" and ",5". Text with no digits stays whole. */
        fun of(text: String, decimalChar: Char): MoneyParts {
            val first = text.indexOfFirst { it.isDigit() }
            if (first < 0) return MoneyParts("", text, "")
            val mark = text.lastIndexOf(decimalChar).takeIf { it > first } ?: text.length
            return MoneyParts(text.substring(0, first), text.substring(first, mark), text.substring(mark))
        }
    }
}

/**
 * What the Pace and Rhythm widgets draw beyond [WidgetFigures]: the last seven days' and this month's
 * spending day by day, and the month's pace. [month] is the accounting month today falls in.
 */
data class WidgetSeries(
    val week: List<DaySpend>,
    val days: List<DaySpend>,
    val today: LocalDate,
    val month: YearMonth,
    val pace: Pace,
) {
    companion object {
        fun of(s: Snapshot, settings: Settings, today: LocalDate): WidgetSeries {
            val rules = settings.periodRules
            val month = rules.monthOf(today)
            val range = Period.Month(month).range(rules)
            val ledgerBills = s.inLedger(s.activeLedger(settings))
            val counted = if (settings.includeFuture) ledgerBills else ledgerBills.filter { it.date <= today }
            return WidgetSeries(
                week = daily(counted, DateRange(today.minusDays(6), today)),
                days = daily(counted, range),
                today = today,
                month = month,
                pace = pace(s, settings, counted, range, today),
            )
        }

        /** Each day's expenses in [range], every day listed, oldest first. */
        fun daily(bills: List<Bill>, range: DateRange): List<DaySpend> {
            val byDay = bills.filter { it.type == BillType.EXPENSE && it.date in range }
                .groupBy { it.date }
                .mapValues { (_, group) -> group.sumOf { it.baseCents } }
            return generateSequence(range.start) { it.plusDays(1) }
                .takeWhile { it <= range.endInclusive }
                .map { DaySpend(it, byDay[it] ?: 0) }
                .toList()
        }

        /**
         * Bar heights for [values]. One rent day would flatten every other bar, so with four or more days
         * of spending the chart tops out at three times the middle day's, and a day above that is drawn
         * full height and marked as clipped.
         */
        fun bars(values: List<Long>): List<Bar> {
            val spending = values.filter { it > 0 }.sorted()
            if (spending.isEmpty()) return values.map { Bar(0f, false) }
            val top = spending.last()
            val cap = if (spending.size >= 4) minOf(top, spending[spending.size / 2] * 3) else top
            return values.map { Bar(it.coerceIn(0, cap).toFloat() / cap, it > cap) }
        }

        /** The month's pace, counted as the Home page's budget card counts it when there is a budget. */
        fun pace(s: Snapshot, settings: Settings, counted: List<Bill>, range: DateRange, today: LocalDate): Pace {
            val day = (ChronoUnit.DAYS.between(range.start, today) + 1).toInt().coerceIn(1, range.days.toInt())
            val budget = s.budgets.firstOrNull { it.categoryId == null }?.amountCents ?: 0
            if (settings.budgetEnabled && budget > 0) {
                val status = Stats.budget(s, settings.periodRules.monthOf(today), settings.periodRules, today, budget, settings.includeFuture)
                val spentToday = s.bills.filter { it.type == BillType.EXPENSE && it.inBudget && it.date == today }.sumOf { it.baseCents }
                return Pace(status.spentCents, budget, true, day, range.days.toInt(), spentToday)
            }
            val month = counted.inRange(range).totals()
            val spentToday = counted.filter { it.date == today }.totals().expense
            return Pace(month.expense, month.income, false, day, range.days.toInt(), spentToday)
        }
    }
}
