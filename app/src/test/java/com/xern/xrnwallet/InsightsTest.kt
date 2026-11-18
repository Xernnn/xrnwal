package com.xern.xrnwallet

import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Category
import com.xern.xrnwallet.data.Insights
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.domain.DateRange
import com.xern.xrnwallet.domain.Period
import com.xern.xrnwallet.domain.PeriodRules
import com.xern.xrnwallet.ui.compact
import com.xern.xrnwallet.ui.scaleTop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The figures behind Reports' pace, day, cash flow and category charts. */
class InsightsTest {
    private val rules = PeriodRules()
    private val today = LocalDate.of(2026, 11, 19)

    private fun bill(date: LocalDate, cents: Long, type: Int = BillType.EXPENSE, category: Long? = null) = Bill(
        ledgerId = 1, type = type, categoryId = category, amountCents = cents, currency = "AUD",
        atMillis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    @Test
    fun aRunningTotalAddsUpEachStep() {
        assertEquals(listOf(5L, 5L, 12L, 20L), Insights.running(listOf(5, 0, 7, 8)))
    }

    @Test
    fun onlyTheDaysSoFarOfTheCurrentPeriodAreDrawn() {
        val november = Period.Month(YearMonth.of(2026, 11))
        assertEquals(19, Insights.elapsed(november, november.range(rules), today, 30))
        val october = Period.Month(YearMonth.of(2026, 10))
        assertEquals(31, Insights.elapsed(october, october.range(rules), today, 31))
        val december = Period.Month(YearMonth.of(2026, 12))
        assertEquals(0, Insights.elapsed(december, december.range(rules), today, 31))
        val year = Period.Year(2026)
        assertEquals(11, Insights.elapsed(year, year.range(rules), today, 12))
    }

    @Test
    fun theMonthChartsCoverAYearsOwnMonthsOrTheTwelveUpToThePeriod() {
        val year = Period.Year(2025)
        assertEquals((1..12).map { YearMonth.of(2025, it) }, Insights.window(year, year.range(rules), rules))
        val month = Period.Month(YearMonth.of(2026, 3))
        val months = Insights.window(month, month.range(rules), rules)
        assertEquals(YearMonth.of(2025, 4), months.first())
        assertEquals(YearMonth.of(2026, 3), months.last())
        assertEquals(12, months.size)
    }

    @Test
    fun cashFlowCountsAccountingMonths() {
        // With months starting on the 15th, October 20 belongs to November's accounting month.
        val late = PeriodRules(startDay = 15, belonging = com.xern.xrnwallet.domain.Belonging.END_MONTH)
        val bills = listOf(
            bill(LocalDate.of(2026, 10, 20), 4_000),
            bill(LocalDate.of(2026, 10, 10), 1_000),
            bill(LocalDate.of(2026, 10, 21), 100_000, type = BillType.INCOME),
            bill(LocalDate.of(2026, 10, 22), 5_000, type = BillType.TRANSFER),
        )
        val flows = Insights.flows(bills, listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11)), late)
        assertEquals(Insights.Flow(YearMonth.of(2026, 10), 0, 1_000), flows[0])
        assertEquals(Insights.Flow(YearMonth.of(2026, 11), 100_000, 4_000), flows[1])
        assertEquals(96_000L, flows[1].net)
    }

    @Test
    fun theCategoryMixKeepsTheLargestAndGroupsTheRest() {
        val food = Category(id = 1, type = BillType.EXPENSE, name = "Food", icon = "🍗")
        val lunch = Category(id = 2, type = BillType.EXPENSE, name = "Lunch", icon = "🍱", parentId = 1)
        val rent = Category(id = 3, type = BillType.EXPENSE, name = "Rent", icon = "🏠")
        val fun_ = Category(id = 4, type = BillType.EXPENSE, name = "Fun", icon = "🎬")
        val snapshot = Snapshot(categories = listOf(food, lunch, rent, fun_))
        val months = listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 11))
        val bills = listOf(
            bill(LocalDate.of(2026, 10, 3), 50_000, category = rent.id),
            bill(LocalDate.of(2026, 11, 3), 50_000, category = rent.id),
            bill(LocalDate.of(2026, 10, 4), 2_000, category = food.id),
            // A subcategory counts towards its parent.
            bill(LocalDate.of(2026, 11, 4), 3_000, category = lunch.id),
            bill(LocalDate.of(2026, 11, 5), 1_500, category = fun_.id),
            bill(LocalDate.of(2026, 11, 6), 700),
            // Outside the months, and income: neither counts.
            bill(LocalDate.of(2026, 9, 6), 9_999, category = fun_.id),
            bill(LocalDate.of(2026, 11, 6), 9_999, type = BillType.INCOME, category = fun_.id),
        )
        val slices = Insights.mix(snapshot, bills, months, rules, top = 2)
        assertEquals(listOf("Rent", "Food", null), slices.map { it.category?.name })
        assertEquals(listOf(50_000L, 50_000L), slices[0].perMonth)
        assertEquals(listOf(2_000L, 3_000L), slices[1].perMonth)
        assertNull(slices[2].category)
        assertEquals(listOf(0L, 2_200L), slices[2].perMonth)
        // With room for every category there is no Other.
        assertEquals(4, Insights.mix(snapshot, bills, months, rules, top = 5).size)
    }

    @Test
    fun oneBigDayDoesntPaleTheRest() {
        val days = (1..10).map { it * 1_000L } + 165_000L
        val cuts = Insights.shadeCuts(days + 0L)
        assertEquals(listOf(5_000L, 8_000L, 10_000L), cuts)
        assertEquals(0, Insights.shade(0, cuts))
        assertEquals(1, Insights.shade(1_000, cuts))
        assertEquals(2, Insights.shade(6_000, cuts))
        assertEquals(4, Insights.shade(165_000, cuts))
        assertEquals(emptyList<Long>(), Insights.shadeCuts(listOf(0L)))
    }

    @Test
    fun aWeekdayAverageCountsTheDaysItCameRoundWithNothingSpent() {
        // November 2026 up to Thursday the 19th: three Mondays (2, 9, 16), three Thursdays.
        val range = DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        val bills = listOf(
            bill(LocalDate.of(2026, 11, 2), 3_000),
            bill(LocalDate.of(2026, 11, 2), 1_500),
            bill(LocalDate.of(2026, 11, 16), 1_500),
            bill(LocalDate.of(2026, 11, 19), 900),
            // After today: not counted yet.
            bill(LocalDate.of(2026, 11, 23), 50_000),
            // Recorded by a recurring rule: a date, not a habit.
            bill(LocalDate.of(2026, 11, 9), 165_000).copy(recurringId = 3),
        )
        val averages = Insights.weekdayAverages(bills, range, today)
        assertEquals(2_000L, averages[DayOfWeek.MONDAY])
        assertEquals(300L, averages[DayOfWeek.THURSDAY])
        assertEquals(0L, averages[DayOfWeek.SUNDAY])
    }

    @Test
    fun aChartScaleEndsJustPastItsHighestFigureOnARoundOne() {
        assertEquals(500_000L, scaleTop(440_000))
        assertEquals(300_000L, scaleTop(260_000))
        assertEquals(400_000L, scaleTop(380_000))
        assertEquals(400_000L, scaleTop(400_000))
        assertEquals(10_000L, scaleTop(0))
    }

    @Test
    fun chartFiguresAreShort() {
        assertEquals("54", compact(5_420))
        assertEquals("1.2k", compact(123_456))
        assertEquals("3k", compact(300_000))
        assertEquals("-3.9k", compact(-389_000))
        assertEquals("165k", compact(16_500_000))
        assertEquals("1.5m", compact(150_000_000))
    }
}
