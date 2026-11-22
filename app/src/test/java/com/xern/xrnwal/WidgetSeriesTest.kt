package com.xern.xrnwal

import com.xern.xrnwal.data.Bill
import com.xern.xrnwal.data.BillType
import com.xern.xrnwal.data.Budget
import com.xern.xrnwal.data.Ledger
import com.xern.xrnwal.data.Settings
import com.xern.xrnwal.data.Snapshot
import com.xern.xrnwal.domain.DateRange
import com.xern.xrnwal.work.Bar
import com.xern.xrnwal.work.MoneyParts
import com.xern.xrnwal.work.WidgetSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetSeriesTest {
    private val today = LocalDate.of(2026, 11, 22)
    private val zone = ZoneId.systemDefault()

    private fun millis(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun bill(
        id: Long,
        date: LocalDate,
        cents: Long,
        type: Int = BillType.EXPENSE,
        hour: Int = 12,
        ledger: Long = 1,
        category: Long? = null,
        note: String = "",
        inBudget: Boolean = true,
    ) = Bill(
        id = id, ledgerId = ledger, type = type, categoryId = category, amountCents = cents, currency = "AUD",
        note = note, inBudget = inBudget, atMillis = millis(date, hour),
    )

    private val ledgers = listOf(Ledger(id = 1, name = "General"), Ledger(id = 2, name = "Trip"))
    private val settings = Settings(activeLedger = 1, includeFuture = false)

    @Test
    fun dailyListsEveryDayWithItsExpensesOnly() {
        val bills = listOf(
            bill(1, today, 1200),
            bill(2, today, 300),
            bill(3, today.minusDays(2), 500),
            bill(4, today.minusDays(2), 9000, type = BillType.INCOME),
            bill(5, today.minusDays(1), 700, type = BillType.TRANSFER),
            bill(6, today.minusDays(9), 4000),
        )
        val days = WidgetSeries.daily(bills, DateRange(today.minusDays(3), today))
        assertEquals(listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1), today), days.map { it.date })
        assertEquals(listOf(0L, 500L, 0L, 1500L), days.map { it.cents })
    }

    @Test
    fun oneRentDayIsClippedSoTheOtherBarsKeepTheirHeight() {
        val bars = WidgetSeries.bars(listOf(165_000, 2_000, 4_000, 0, 3_000, 6_000, 5_000))
        // The middle of the six spending days is 5.000, so the chart tops out at 15.000.
        assertEquals(Bar(1f, true), bars[0])
        assertEquals(2_000f / 15_000, bars[1].height, 0.0001f)
        assertEquals(Bar(0f, false), bars[3])
        assertFalse(bars.drop(1).any { it.clipped })
    }

    @Test
    fun fewSpendingDaysAreScaledToTheLargest() {
        assertEquals(listOf(Bar(1f, false), Bar(0.25f, false), Bar(0f, false)), WidgetSeries.bars(listOf(8_000, 2_000, 0)))
        assertEquals(listOf(Bar(0f, false), Bar(0f, false)), WidgetSeries.bars(listOf(0, 0)))
    }

    @Test
    fun paceFollowsTheOverallBudgetAcrossLedgersWhenOneIsSet() {
        val s = Snapshot(
            ledgers = ledgers,
            budgets = listOf(Budget(id = 1, amountCents = 300_000), Budget(id = 2, categoryId = 5, amountCents = 10_000)),
            bills = listOf(
                bill(1, today.withDayOfMonth(1), 150_000),
                bill(2, today.minusDays(1), 20_000, ledger = 2),
                bill(3, today, 6_000),
                bill(4, today, 4_000, inBudget = false),
                bill(5, today, 500_000, type = BillType.INCOME),
            ),
        )
        val series = WidgetSeries.of(s, settings, today)
        val pace = series.pace
        assertTrue(pace.fromBudget)
        assertEquals(176_000L, pace.spent)
        assertEquals(300_000L, pace.limit)
        assertEquals(22, pace.day)
        assertEquals(30, pace.days)
        assertEquals(9, pace.daysLeft)
        // 3.000 less the 1.700 spent before today, over today and the eight days after it.
        assertEquals((300_000L - 170_000) / 9, pace.allowanceToday)
        assertEquals((300_000L - 176_000) / 9, pace.perDay)
        assertFalse(pace.ahead)
        assertTrue(pace.copy(spent = 240_000).ahead)
    }

    @Test
    fun paceFallsBackToTheMonthsIncomeInTheActiveLedger() {
        val s = Snapshot(
            ledgers = ledgers,
            bills = listOf(
                bill(1, today.minusDays(3), 40_000),
                bill(2, today, 5_000),
                bill(3, today.minusDays(10), 200_000, type = BillType.INCOME),
                bill(4, today, 90_000, ledger = 2),
                bill(5, today.minusMonths(1), 70_000),
            ),
        )
        val pace = WidgetSeries.of(s, settings, today).pace
        assertFalse(pace.fromBudget)
        assertEquals(45_000L, pace.spent)
        assertEquals(200_000L, pace.limit)
        assertEquals(5_000L, pace.spentToday)
        assertEquals((200_000L - 40_000) / 9, pace.allowanceToday)

        val nothing = WidgetSeries.of(Snapshot(ledgers = ledgers), settings, today).pace
        assertEquals(0L, nothing.limit)
        assertEquals(0f, nothing.share)
        assertFalse(nothing.ahead)
        assertEquals(0L, nothing.allowanceToday)
    }

    @Test
    fun weekEndsTodayAndMonthFollowsTheStartDay() {
        val s = Snapshot(ledgers = ledgers, bills = listOf(bill(1, today, 1_000), bill(2, today.minusDays(6), 2_000)))
        val series = WidgetSeries.of(s, settings.copy(startDay = 25), today)
        assertEquals(7, series.week.size)
        assertEquals(today, series.week.last().date)
        assertEquals(listOf(2_000L, 0L, 0L, 0L, 0L, 0L, 1_000L), series.week.map { it.cents })
        assertEquals(LocalDate.of(2026, 10, 25), series.days.first().date)
        assertEquals(LocalDate.of(2026, 11, 24), series.days.last().date)
        assertEquals(29, series.pace.day)
        assertEquals(java.time.YearMonth.of(2026, 10), series.month)
    }

    @Test
    fun anEmptyMonthIsAllZeroDaysAndNoLimit() {
        val series = WidgetSeries.of(Snapshot(ledgers = ledgers), settings, today)
        assertEquals(7, series.week.size)
        assertTrue(series.week.all { it.cents == 0L })
        assertEquals(30, series.days.size)
        assertTrue(series.days.all { it.cents == 0L })
        assertEquals(listOf(Bar(0f, false)), WidgetSeries.bars(listOf(0)))
        assertEquals(0L, series.pace.spent)
        assertEquals(0L, series.pace.left)
        assertEquals(0L, series.pace.perDay)
    }

    @Test
    fun theFirstOfTheMonthSharesTheWholeBudgetOverEveryDay() {
        val first = today.withDayOfMonth(1)
        val s = Snapshot(
            ledgers = ledgers,
            budgets = listOf(Budget(id = 1, amountCents = 300_000)),
            bills = listOf(bill(1, first, 165_000), bill(2, first.minusDays(1), 9_000)),
        )
        val series = WidgetSeries.of(s, settings, first)
        val pace = series.pace
        assertEquals(1, pace.day)
        assertEquals(30, pace.daysLeft)
        assertEquals(1f / 30, pace.elapsed, 0.0001f)
        // This morning nothing of the month was spent, so today may spend a thirtieth of it.
        assertEquals(300_000L / 30, pace.allowanceToday)
        assertEquals(165_000L, pace.spentToday)
        assertTrue(pace.ahead)
        // Last month's last day is in the week but not the month.
        assertEquals(9_000L, series.week[5].cents)
        assertEquals(165_000L, series.days.first().cents)
    }

    @Test
    fun spendingWithNoIncomeOrBudgetHasNoLimitToRunAheadOf() {
        val s = Snapshot(ledgers = ledgers, bills = listOf(bill(1, today, 2_000), bill(2, today.minusDays(4), 50_000)))
        val pace = WidgetSeries.of(s, settings, today).pace
        assertFalse(pace.fromBudget)
        assertEquals(52_000L, pace.spent)
        assertEquals(0L, pace.limit)
        assertEquals(0f, pace.share)
        assertFalse(pace.ahead)
        assertEquals(0L, pace.allowanceToday)
        assertEquals(0L, pace.perDay)
    }

    @Test
    fun aMonthOverItsBudgetLeavesNothingADay() {
        val s = Snapshot(ledgers = ledgers, budgets = listOf(Budget(id = 1, amountCents = 100_000)), bills = listOf(bill(1, today.minusDays(1), 120_000)))
        val pace = WidgetSeries.of(s, settings, today).pace
        assertEquals(-20_000L, pace.left)
        assertEquals(0L, pace.perDay)
        assertEquals(0L, pace.allowanceToday)
        assertTrue(pace.ahead)
    }

    @Test
    fun aBudgetTurnedOffFallsBackToIncome() {
        val s = Snapshot(
            ledgers = ledgers,
            budgets = listOf(Budget(id = 1, amountCents = 100_000)),
            bills = listOf(bill(1, today, 1_000), bill(2, today.minusDays(3), 80_000, type = BillType.INCOME)),
        )
        val pace = WidgetSeries.of(s, settings.copy(budgetEnabled = false), today).pace
        assertFalse(pace.fromBudget)
        assertEquals(80_000L, pace.limit)
    }

    @Test
    fun moneyPartsSplitTheSymbolAndDecimalsFromTheWholeNumber() {
        assertEquals(MoneyParts("$", "1.234", ",5"), MoneyParts.of("$1.234,5", ','))
        assertEquals(MoneyParts("-$", "12", ""), MoneyParts.of("-$12", ','))
        assertEquals(MoneyParts("$", "1,000", ".10"), MoneyParts.of("$1,000.10", '.'))
        assertEquals(MoneyParts("", "6.700.265", ""), MoneyParts.of("6.700.265", ','))
        assertEquals(MoneyParts("", "••••", ""), MoneyParts.of("••••", ','))
    }
}
