package com.xern.xrnwallet

import com.xern.xrnwallet.data.Account
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillCsv
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Budget
import com.xern.xrnwallet.data.Category
import com.xern.xrnwallet.data.CurrencyRate
import com.xern.xrnwallet.data.Ledger
import com.xern.xrnwallet.data.PhotoFiles
import com.xern.xrnwallet.data.Rebased
import com.xern.xrnwallet.data.Recurring
import com.xern.xrnwallet.data.SavingsDeposit
import com.xern.xrnwallet.data.SavingsPlan
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.data.Stats
import com.xern.xrnwallet.data.totals
import com.xern.xrnwallet.domain.Belonging
import com.xern.xrnwallet.domain.Calculator
import com.xern.xrnwallet.domain.Dates
import com.xern.xrnwallet.domain.DecimalMode
import com.xern.xrnwallet.domain.Money
import com.xern.xrnwallet.domain.MoneyFormat
import com.xern.xrnwallet.domain.Period
import com.xern.xrnwallet.domain.PeriodRules
import com.xern.xrnwallet.domain.RepeatKind
import com.xern.xrnwallet.domain.RepeatRule
import com.xern.xrnwallet.domain.RepeatUnit
import com.xern.xrnwallet.domain.Savings
import com.xern.xrnwallet.domain.SeparatorStyle
import com.xern.xrnwallet.domain.WeekendRule
import com.xern.xrnwallet.ui.AppUi
import com.xern.xrnwallet.ui.BillStart
import com.xern.xrnwallet.ui.Fmt
import com.xern.xrnwallet.ui.accountLogo
import com.xern.xrnwallet.ui.billStart
import com.xern.xrnwallet.ui.currencyChoices
import com.xern.xrnwallet.ui.firstSteps
import com.xern.xrnwallet.work.WidgetFigures
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DomainTest {
    @get:Rule val folder = TemporaryFolder()

    private val format = MoneyFormat(SeparatorStyle.DOT_COMMA, DecimalMode.ADAPTIVE)

    private fun bill(
        date: LocalDate,
        cents: Long,
        type: Int = BillType.EXPENSE,
        inBudget: Boolean = true,
        account: Long? = null,
        currency: String = "AUD",
        rate: Double = 1.0,
    ) = Bill(
        ledgerId = 1,
        type = type,
        amountCents = cents,
        currency = currency,
        rate = rate,
        accountId = account,
        inBudget = inBudget,
        atMillis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )

    @Test
    fun adaptiveFormattingTrimsTrailingZeros() {
        assertEquals("$12.345,67", format.money(1234567, "$"))
        assertEquals("$9.876,5", format.money(987650, "$"))
        assertEquals("$1.000", format.money(100000, "$"))
        assertEquals("-$321,45", format.money(-32145, "$"))
        assertEquals("$0", format.money(0, "$"))
        assertEquals("₫6.700.265", format.money(670026500, "₫"))
        assertEquals("+$650", format.signed(65000, "$"))
    }

    @Test
    fun fixedDecimalsAndSeparators() {
        val two = MoneyFormat(SeparatorStyle.COMMA_DOT, DecimalMode.TWO)
        assertEquals("$1,000.10", two.money(100010, "$"))
        val one = MoneyFormat(SeparatorStyle.COMMA_DOT, DecimalMode.ONE)
        assertEquals("$3.8", one.money(380, "$"))
        assertEquals("$3.99", one.money(399, "$"))
        assertEquals("$4.0", one.money(400, "$"))
    }

    @Test
    fun parsesEitherDecimalConvention() {
        assertEquals(123450L, Money.parse("1.234,50"))
        assertEquals(123450L, Money.parse("1,234.50"))
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(1250L, Money.parse("12.5"))
        assertNull(Money.parse("abc"))
    }

    @Test
    fun thousandsMarkedTheWayAmountsAreShownAreRead() {
        assertEquals(150000000L, Money.parse("1.500.000"))
        assertEquals(150000000L, Money.parse("1,500,000"))
        assertEquals(-150000000L, Money.parse("-1.500.000"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("1.500.00"))
    }

    @Test
    fun calculatorRespectsPrecedence() {
        assertEquals("15.00", Calculator.evaluate("12+3").toString())
        assertEquals("14.00", Calculator.evaluate("2+3×4").toString())
        assertEquals("2.50", Calculator.evaluate("10÷4").toString())
        assertEquals("7.00", Calculator.evaluate("10-3").toString())
        assertEquals("12.00", Calculator.evaluate("12+").toString())
        assertNull(Calculator.evaluate("5÷0"))
    }

    @Test
    fun keypadBehaviour() {
        assertEquals("5", Calculator.press("0", '5'))
        assertEquals("12+", Calculator.press("12-", '+'))
        assertEquals("12.34", Calculator.press("12.34", '5'))
        assertEquals("12.5", Calculator.press("12.", '5'))
        assertEquals("0.", Calculator.press("0", '.'))
        assertEquals("0", Calculator.backspace("5"))
        assertTrue(Calculator.hasPendingSum("12+3"))
        assertFalse(Calculator.hasPendingSum("12"))
    }

    @Test
    fun octoberHasThirtyOneDaysDespiteDaylightSaving() {
        val rules = PeriodRules()
        val range = rules.monthRange(YearMonth.of(2026, 10))
        assertEquals(31L, range.days)
        val period = Period.Month(YearMonth.of(2026, 10))
        assertEquals(31L, Stats.averageUnits(period, range, LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun averageUnitsMatchReference() {
        val rules = PeriodRules()
        val today = LocalDate.of(2026, 11, 6)
        val sep = Period.Month(YearMonth.of(2026, 9))
        assertEquals(30L, Stats.averageUnits(sep, sep.range(rules), today))
        val aug = Period.Month(YearMonth.of(2026, 8))
        assertEquals(31L, Stats.averageUnits(aug, aug.range(rules), today))
        val year = Period.Year(2026)
        assertEquals(11L, Stats.averageUnits(year, year.range(rules), today))
        assertEquals(89787L, Stats.average(987654L, Stats.averageUnits(year, year.range(rules), today)))
    }

    @Test
    fun customStartDayAndBelonging() {
        val rules = PeriodRules(startDay = 15)
        val range = rules.monthRange(YearMonth.of(2026, 3))
        assertEquals(LocalDate.of(2026, 3, 15), range.start)
        assertEquals(LocalDate.of(2026, 4, 14), range.endInclusive)
        assertEquals(YearMonth.of(2026, 3), rules.monthOf(LocalDate.of(2026, 4, 2)))
        val ending = PeriodRules(startDay = 15, belonging = Belonging.END_MONTH)
        assertEquals(LocalDate.of(2026, 2, 15), ending.monthRange(YearMonth.of(2026, 3)).start)
    }

    @Test
    fun aWeekendCanMoveADateTwoMonthsFromItsCalendarMonth() {
        // 2026-02-28 is a Saturday, so with Backward February starts on Mar 2 and March 1 is still January's.
        val backward = PeriodRules(startDay = 28, weekend = WeekendRule.LATER)
        assertEquals(YearMonth.of(2026, 1), backward.monthOf(LocalDate.of(2026, 3, 1)))
        // 2026-11-01 is a Sunday, so with Forward November starts on Oct 30, labelled December.
        val forward = PeriodRules(startDay = 1, weekend = WeekendRule.EARLIER, belonging = Belonging.END_MONTH)
        assertEquals(YearMonth.of(2026, 12), forward.monthOf(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun everyDayBelongsToExactlyOneMonthWhateverTheSettings() {
        val first = LocalDate.of(2024, 1, 1)
        val last = LocalDate.of(2032, 12, 31)
        for (startDay in 1..28) for (weekend in WeekendRule.entries) for (belonging in Belonging.entries) {
            val rules = PeriodRules(startDay = startDay, weekend = weekend, belonging = belonging)
            var date = first
            while (date <= last) {
                val calendar = YearMonth.from(date)
                val holding = (-3L..3L).map { calendar.plusMonths(it) }.filter { date in rules.monthRange(it) }
                assertEquals("$date in $rules", listOf(rules.monthOf(date)), holding)
                date = date.plusDays(1)
            }
        }
    }

    @Test
    fun homeAndReportsOpenOnTheAccountingMonthOfToday() {
        // With Start of Month 15, November 9 is still in October's month, as the widget and Budget say.
        val ui = AppUi(LocalDate.of(2026, 11, 9), PeriodRules(startDay = 15))
        assertEquals(Period.Month(YearMonth.of(2026, 10)), ui.homePeriod)
        assertEquals(Period.Month(YearMonth.of(2026, 10)), ui.reportPeriod)
    }

    @Test
    fun weekendRuleMovesStart() {
        // 2026-11-01 is a Sunday.
        val earlier = PeriodRules(weekend = WeekendRule.EARLIER)
        assertEquals(LocalDate.of(2026, 10, 30), earlier.monthRange(YearMonth.of(2026, 11)).start)
        val later = PeriodRules(weekend = WeekendRule.LATER)
        assertEquals(LocalDate.of(2026, 11, 2), later.monthRange(YearMonth.of(2026, 11)).start)
    }

    @Test
    fun weekRangeFollowsFirstDay() {
        val range = PeriodRules().weekRange(LocalDate.of(2026, 11, 6))
        assertEquals(LocalDate.of(2026, 11, 2), range.start)
        assertEquals(LocalDate.of(2026, 11, 8), range.endInclusive)
    }

    @Test
    fun recurrenceDates() {
        val sunday = RepeatRule(RepeatKind.WEEKLY, 7)
        assertEquals(LocalDate.of(2026, 5, 24), sunday.occurrence(LocalDate.of(2026, 5, 24), 0))
        assertEquals(LocalDate.of(2026, 5, 31), sunday.occurrence(LocalDate.of(2026, 5, 24), 1))
        val thirty = RepeatRule(RepeatKind.EVERY_N_DAYS, 30)
        assertEquals(LocalDate.of(2026, 3, 31), thirty.occurrence(LocalDate.of(2026, 3, 1), 1))
        val monthly = RepeatRule(RepeatKind.MONTHLY, 31)
        assertEquals(LocalDate.of(2026, 2, 28), monthly.occurrence(LocalDate.of(2026, 1, 31), 1))
    }

    @Test
    fun savingsMaths() {
        assertEquals(1378000L, Savings.incrementTarget(1000, 52))
        assertEquals(2000L, Savings.incrementDeposit(1000, 1))
        assertEquals(10000L, Savings.fixedDeposit(120000, 12))
        assertEquals(3334L, Savings.fixedDeposit(10000, 3))
    }

    @Test
    fun budgetSkipsBillsMarkedOutside() {
        val today = LocalDate.of(2026, 11, 6)
        val oct = LocalDate.of(2026, 10, 4)
        val snapshot = Snapshot(
            bills = listOf(
                bill(oct, 39202),
                bill(oct, 21000, inBudget = false),
            ),
        )
        val status = Stats.budget(snapshot, YearMonth.of(2026, 10), PeriodRules(), today, 80000, true)
        assertEquals(40798L, status.remainingCents)
        assertEquals(50, status.remainingPercent)
        assertEquals(0L, status.dailyAverageCents)
    }

    @Test
    fun dailyAverageCountsToday() {
        val today = LocalDate.of(2026, 11, 6)
        val status = Stats.budget(Snapshot(), YearMonth.of(2026, 11), PeriodRules(), today, 80000, true)
        assertEquals(3200L, status.dailyAverageCents)
    }

    @Test
    fun firstStepsTickThemselvesOffFromTheData() {
        val cash = Account(id = 1, name = "Cash", currency = "AUD")
        val fresh = Snapshot(accounts = listOf(cash))
        assertEquals(listOf(false, false, false), firstSteps.map { it.done(fresh) })
        val started = Snapshot(
            accounts = listOf(cash, Account(id = 2, name = "Bank", currency = "AUD")),
            bills = listOf(bill(LocalDate.of(2026, 10, 1), 1000)),
            budgets = listOf(Budget(amountCents = 50000)),
        )
        assertEquals(listOf(true, true, true), firstSteps.map { it.done(started) })
    }

    @Test
    fun carriedBalanceIsLifetimeNet() {
        val bills = listOf(
            bill(LocalDate.of(2026, 5, 1), 1234567),
            bill(LocalDate.of(2026, 6, 1), 1202422, BillType.INCOME),
        )
        assertEquals(-32145L, Stats.carriedBalance(bills, LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun foreignAccountsKeepNativeBalance() {
        val abroad = Account(id = 3, name = "Abroad", currency = "VND", openingCents = 620_000_000)
        val snapshot = Snapshot(
            accounts = listOf(abroad),
            rates = listOf(CurrencyRate("VND", 0.00005569)),
            bills = listOf(bill(LocalDate.of(2026, 10, 1), 130_000_000, account = 3, currency = "VND", rate = 0.00005548)),
        )
        assertEquals(490_000_000L, snapshot.balance(abroad))
        assertEquals(27288L, snapshot.balanceInBase(abroad))
        assertEquals(7212L, snapshot.bills.totals().expense)
    }

    @Test
    fun aNewBillStartsInTheCurrencyOfItsAccount() {
        val cash = Account(id = 1, name = "Cash", currency = "AUD")
        val card = Account(id = 2, name = "US card", currency = "USD")
        val s = Snapshot(
            accounts = listOf(cash, card),
            rates = listOf(CurrencyRate("USD", 1.53)),
            bills = listOf(bill(LocalDate.of(2026, 11, 8), 1500, account = 2, currency = "USD", rate = 1.53)),
        )
        // Typing 20 on the US card must record US$20, not A$20.
        assertEquals(BillStart(2, "USD"), s.billStart("AUD"))
        assertEquals(BillStart(1, "AUD"), s.copy(bills = emptyList()).billStart("AUD"))
        assertEquals(BillStart(null, "EUR"), Snapshot().billStart("EUR"))
    }

    @Test
    fun moneyIsOnlyKeptInCurrenciesWithARate() {
        // With no VND rate, a VND account holding 25,000,000 would add A$25M to net assets.
        val s = Snapshot(rates = listOf(CurrencyRate("USD", 1.53), CurrencyRate("EUR", 1.78)))
        assertEquals(listOf("AUD", "USD", "EUR"), s.currencyChoices("AUD"))
    }

    @Test
    fun aCategoryIsInUseWhileAnythingUsesItOrItsSubcategories() {
        val food = Category(id = 1, type = BillType.EXPENSE, name = "Food", icon = "🍔")
        val groceries = Category(id = 2, type = BillType.EXPENSE, name = "Groceries", icon = "🛒", parentId = 1)
        val travel = Category(id = 3, type = BillType.EXPENSE, name = "Travel", icon = "✈️")
        val s = Snapshot(
            categories = listOf(food, groceries, travel),
            bills = listOf(bill(LocalDate.of(2026, 11, 2), 4500).copy(categoryId = 2)),
        )
        // Deleting Food takes Groceries with it, which would leave that bill Uncategorised for good.
        assertTrue(s.categoryInUse(food))
        assertTrue(s.categoryInUse(groceries))
        assertFalse(s.categoryInUse(travel))
        val rule = Recurring(ledgerId = 1, type = BillType.EXPENSE, categoryId = 3, amountCents = 900, currency = "AUD", startEpochDay = 0)
        assertTrue(s.copy(recurring = listOf(rule)).categoryInUse(travel))
    }

    @Test
    fun changingTheBaseConvertsBudgetsAndKeepsBillsAlreadyInItAsTheyAre() {
        // A euro is A$1.65 today. It was A$1.50 when the euro bill was recorded.
        val day = LocalDate.of(2026, 10, 5)
        val rebased = Rebased.of(
            previous = "AUD",
            code = "EUR",
            bills = listOf(
                bill(day, 10000, currency = "EUR", rate = 1.50),
                bill(day, 16500),
                bill(day, 1000, currency = "USD", rate = 1.53),
            ),
            rates = listOf(CurrencyRate("EUR", 1.65), CurrencyRate("USD", 1.53, 1)),
            budgets = listOf(Budget(id = 1, amountCents = 82500), Budget(id = 2, categoryId = 5, amountCents = 16500)),
        )!!
        // €100 counts as €100, not €91. The others keep their rate, rescaled as before.
        assertEquals(listOf(10000L, 10000L, 927L), rebased.bills.map { it.baseCents })
        assertEquals(listOf(50000L, 10000L), rebased.budgets.map { it.amountCents })
        assertEquals(listOf("USD", "AUD"), rebased.rates.map { it.code })
        assertEquals(1 / 1.65, rebased.rates.last().rate, 1e-12)
        assertNull(Rebased.of("AUD", "JPY", rebased.bills, rebased.rates, rebased.budgets))
    }

    @Test
    fun rankingSharesAddUp() {
        val snapshot = Snapshot(
            bills = listOf(bill(LocalDate.of(2026, 10, 1), 21000), bill(LocalDate.of(2026, 10, 2), 18975)),
        )
        val ranks = Stats.ranking(snapshot, snapshot.bills, BillType.EXPENSE, true)
        assertEquals(1, ranks.size)
        assertEquals(2, ranks[0].count)
        assertEquals(100.0, ranks[0].share, 0.001)
    }

    @Test
    fun everyNWeeksSkipsWeeks() {
        val rule = RepeatRule.ofWheel(2, RepeatUnit.WEEKS, LocalDate.of(2026, 11, 6))
        assertEquals(RepeatKind.WEEKLY, rule.kind)
        assertEquals(
            listOf(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 4)),
            rule.occurrences(LocalDate.of(2026, 11, 6), 3),
        )
        assertEquals("Every 2 Weeks", rule.shortLabel)
    }

    @Test
    fun everyNMonthsKeepsTheDayAndClampsShortMonths() {
        val start = LocalDate.of(2026, 12, 31)
        val rule = RepeatRule.ofWheel(2, RepeatUnit.MONTHS, start)
        assertEquals(
            listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 4, 30), LocalDate.of(2027, 6, 30)),
            rule.occurrences(start, 4),
        )
        assertEquals("Every 2 Months on day 31", rule.longLabel)
    }

    @Test
    fun everyNYearsAndTheWheelRoundTrip() {
        val start = LocalDate.of(2024, 2, 29)
        val rule = RepeatRule.ofWheel(3, RepeatUnit.YEARS, start)
        assertEquals(LocalDate.of(2027, 2, 28), rule.occurrence(start, 1))
        assertEquals(3, rule.wheelCount)
        assertEquals(RepeatUnit.YEARS, rule.wheelUnit)
        // Rules saved before the wheel existed keep their meaning and labels.
        assertEquals("Every 30 Days", RepeatRule(RepeatKind.EVERY_N_DAYS, 30).shortLabel)
        assertEquals(30, RepeatRule(RepeatKind.EVERY_N_DAYS, 30).wheelCount)
        assertEquals(RepeatUnit.DAYS, RepeatRule(RepeatKind.EVERY_N_DAYS, 30).wheelUnit)
        assertEquals("Weekly", RepeatRule(RepeatKind.WEEKLY, 1).shortLabel)
        assertEquals("Monthly on day 5", RepeatRule(RepeatKind.MONTHLY, 5).longLabel)
        assertEquals(RepeatKind.DAILY, RepeatRule.ofWheel(1, RepeatUnit.DAYS, start).kind)
    }

    @Test
    fun aDepositRaisesItsReceiveAccountOnly() {
        val into = Account(id = 1, name = "Savings", currency = "AUD")
        val other = Account(id = 2, name = "Cash", currency = "AUD")
        val foreign = Account(id = 3, name = "Abroad", currency = "USD", openingCents = 100)
        val plan = SavingsPlan(id = 7, name = "Trip", targetCents = 100000, currency = "AUD", startEpochDay = 0, accountId = 1)
        val abroad = plan.copy(id = 8, accountId = 3)
        val loose = plan.copy(id = 9, accountId = null)
        val s = Snapshot(
            accounts = listOf(into, other, foreign),
            plans = listOf(plan, abroad, loose),
            deposits = listOf(
                SavingsDeposit(planId = 7, amountCents = 2500, atMillis = 0),
                SavingsDeposit(planId = 7, amountCents = 500, atMillis = 0),
                SavingsDeposit(planId = 8, amountCents = 2000, atMillis = 0),
                SavingsDeposit(planId = 9, amountCents = 9900, atMillis = 0),
            ),
            rates = listOf(CurrencyRate("USD", 2.0, 0)),
        )
        assertEquals(3000L, s.balance(into))
        assertEquals(0L, s.balance(other))
        assertEquals(1100L, s.balance(foreign))
    }

    @Test
    fun widgetFiguresFollowTheHomeHeader() {
        val today = LocalDate.of(2026, 11, 6)
        fun bill(id: Long, date: LocalDate, type: Int, cents: Long) = Bill(
            id = id, ledgerId = 1, type = type, amountCents = cents, currency = "AUD",
            atMillis = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        val s = Snapshot(
            ledgers = listOf(Ledger(id = 1, name = "General", currency = "AUD")),
            bills = listOf(
                bill(1, today, BillType.EXPENSE, 1000),
                bill(2, today.minusDays(1), BillType.EXPENSE, 2000),
                bill(3, today.minusMonths(1), BillType.INCOME, 50000),
                bill(4, today.plusDays(1), BillType.EXPENSE, 700),
            ),
        )
        val settings = Settings(activeLedger = 1, baseCurrency = "AUD", includeFuture = false, carryover = true)
        val figures = WidgetFigures.of(s, settings, today)
        assertEquals(1000L, figures.today.expense)
        assertEquals(3000L, figures.month.expense)
        assertEquals(47000L, figures.balance)
        assertEquals("Nov 6", figures.dayName)
        val withFuture = WidgetFigures.of(s, settings.copy(includeFuture = true, carryover = false), today)
        assertEquals(3700L, withFuture.month.expense)
        assertEquals(-3700L, withFuture.balance)
    }

    @Test
    fun startDateNextMonthLabelsThePeriodAfterItsStart() {
        val rules = PeriodRules(startDay = 25, belonging = Belonging.END_MONTH)
        assertEquals("Start date next month", Belonging.END_MONTH.label)
        val range = rules.monthRange(YearMonth.of(2026, 11))
        assertEquals(LocalDate.of(2026, 10, 25), range.start)
        assertEquals(LocalDate.of(2026, 11, 24), range.endInclusive)
        assertEquals(YearMonth.of(2026, 12), rules.monthOf(LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun widgetsShowMoneyOnlyWithoutAnAppLock() {
        val open = Settings()
        assertEquals(Fmt(open).money(123_456), WidgetFigures.moneyText(open)(123_456))

        val locks = listOf(open.copy(pinHash = "a"), open.copy(patternHash = "b"), open.copy(biometric = true))
        locks.forEach { assertEquals(WidgetFigures.HIDDEN, WidgetFigures.moneyText(it)(123_456)) }
    }

    @Test
    fun weeksAreNumberedWithTheirYearWhenItIsNotThisOne() {
        val rules = PeriodRules()
        val today = LocalDate.of(2026, 11, 6)
        assertEquals("Week 45", Period.Week(today).label(rules, today))
        assertEquals("Week 43", Period.Week(today.minusWeeks(2)).label(rules, today))
        assertEquals("Week 1", Period.Week(LocalDate.of(2025, 12, 30)).label(rules, today))
        assertEquals("Week 49 2025", Period.Week(LocalDate.of(2025, 12, 3)).label(rules, today))
    }

    @Test
    fun aWeekStartingOnSundayKeepsItsUsualNumber() {
        val sunday = PeriodRules(firstDayOfWeek = DayOfWeek.SUNDAY)
        assertEquals(45, Period.Week(LocalDate.of(2026, 11, 3)).number(sunday))
    }

    @Test
    fun dateSpansStayShort() {
        val today = LocalDate.of(2026, 11, 6)
        assertEquals("Oct 19 - 25", Dates.span(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), today))
        assertEquals("Sep 28 - Oct 4", Dates.span(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4), today))
        assertEquals("Dec 29 - Jan 4, 2026", Dates.span(LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 4), today))
        assertEquals("Dec 1 - 7, 2025", Dates.span(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 7), today))
    }

    @Test
    fun accountsFindTheirLogoByName() {
        assertEquals(R.drawable.logo_commbank, accountLogo("CommonWealth"))
        assertEquals(R.drawable.logo_vietcombank, accountLogo("  Vietcombank "))
        assertEquals(R.drawable.logo_investment, accountLogo("Investment"))
        assertEquals(null, accountLogo("Rainy day"))
    }

    @Test
    fun periodsStepByTheirOwnSize() {
        assertEquals(Period.Week(LocalDate.of(2026, 10, 30)), Period.Week(LocalDate.of(2026, 11, 6)).shifted(-1))
        assertEquals(Period.Month(YearMonth.of(2026, 12)), Period.Month(YearMonth.of(2026, 11)).shifted(1))
        assertEquals(Period.Month(YearMonth.of(2025, 12)), Period.Month(YearMonth.of(2026, 1)).shifted(-1))
        assertEquals(Period.Year(2025), Period.Year(2026).shifted(-1))
        val tenDays = Period.Custom(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10))
        assertEquals(Period.Custom(LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 20)), tenDays.shifted(1))
        assertEquals(Period.Custom(LocalDate.of(2026, 2, 19), LocalDate.of(2026, 2, 28)), tenDays.shifted(-1))
    }

    @Test
    fun aRuleRecordsWhatIsDueButNothingOnceItsLedgerIsGone() {
        // 2026-11-01 is a Sunday.
        val weekly = Recurring(
            ledgerId = 2,
            type = BillType.EXPENSE,
            amountCents = 5000,
            currency = "AUD",
            startEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
            repeatKind = RepeatKind.WEEKLY.name,
            repeatValue = 7,
            times = 0,
        )
        val now = LocalDate.of(2026, 11, 16).atTime(9, 0)
        val sundays = listOf(1, 8, 15).map { LocalDate.of(2026, 11, it).atTime(8, 0) }
        assertEquals(sundays, weekly.due(now, setOf(1L, 2L)))
        assertEquals(sundays.drop(1), weekly.copy(generated = 1).due(now, setOf(2L)))
        assertEquals(sundays.take(2), weekly.copy(times = 2).due(now, setOf(2L)))
        // Its ledger was deleted: bills there would count in every total but show in no ledger.
        assertEquals(emptyList<java.time.LocalDateTime>(), weekly.due(now, setOf(1L)))
    }

    @Test
    fun photosNoBillNamesAreClearedOutOnceADayOld() {
        val now = 1_800_000_000_000L
        fun photo(name: String, ageMillis: Long) =
            folder.newFile(name).apply { writeBytes(byteArrayOf(1)); setLastModified(now - ageMillis) }
        val day = 24 * 60 * 60 * 1000L
        val named = photo("named.jpg", 30 * day)
        val shared = photo("shared.jpg", 30 * day)
        val deleted = photo("deleted.jpg", 30 * day)
        val picked = photo("picked.jpg", 60_000)
        val receipt = bill(LocalDate.of(2026, 11, 2), 1200)
        val bills = listOf(
            receipt.copy(id = 1, photo = named.absolutePath),
            // Add again for today copies the bill, photo and all, so two bills can name one file.
            receipt.copy(id = 2, photo = shared.absolutePath),
            receipt.copy(id = 3, photo = shared.absolutePath),
        )
        assertEquals(listOf("deleted.jpg"), PhotoFiles.sweep(folder.root, bills, now))
        assertFalse(deleted.exists())
        // Just picked for a bill not saved yet.
        assertTrue(picked.exists())
        assertTrue(named.exists() && shared.exists())
        assertEquals(emptyList<String>(), PhotoFiles.sweep(File(folder.root, "none yet"), bills, now))
    }

    @Test
    fun aRuleFindsBillsAlreadyRecordedForItsDates() {
        val rule = Recurring(
            ledgerId = 1,
            type = BillType.EXPENSE,
            categoryId = 5,
            accountId = 2,
            amountCents = 21000,
            currency = "AUD",
            note = "Rent",
            startEpochDay = LocalDate.of(2026, 5, 24).toEpochDay(),
            repeatKind = RepeatKind.WEEKLY.name,
            repeatValue = 7,
            minuteOfDay = 0,
            times = 3,
        )
        fun bill(date: LocalDate, id: Long, change: (Bill) -> Bill = { it }) = change(
            Bill(
                id = id,
                ledgerId = 1,
                type = BillType.EXPENSE,
                categoryId = 5,
                accountId = 2,
                amountCents = 21000,
                currency = "AUD",
                note = "Rent",
                atMillis = BillCsv.millisOf(date.atStartOfDay()),
            ),
        )
        val sunday = LocalDate.of(2026, 5, 24)
        val found = rule.existingBills(
            listOf(
                bill(sunday, 1),
                bill(sunday, 2),
                bill(sunday.plusWeeks(1), 3) { it.copy(note = "Other") },
                bill(sunday.plusWeeks(1), 4) { it.copy(recurringId = 9) },
                bill(sunday.plusDays(1), 5),
                bill(sunday.plusWeeks(2), 6),
                bill(sunday.plusWeeks(3), 7),
            ),
            rule.dates(0, 3),
            otherRules = setOf(9L),
        )
        assertEquals(mapOf(sunday to 1L, sunday.plusWeeks(2) to 6L), found.mapValues { it.value.id })
        val leftBehind = bill(sunday.plusWeeks(1), 8) { it.copy(recurringId = 9) }
        assertEquals(listOf(8L), rule.existingBills(listOf(leftBehind), rule.dates(0, 3), emptySet()).values.map { it.id })
        val drifted = rule.existingBills(
            listOf(
                bill(sunday, 10),
                bill(sunday.plusDays(1), 11),
                bill(sunday.plusWeeks(1).minusDays(1), 12) { it.copy(accountId = 3) },
            ),
            rule.dates(0, 3),
            emptySet(),
        )
        assertEquals(mapOf(sunday to 10L, sunday.plusWeeks(1) to 12L), drifted.mapValues { it.value.id })
        val transfer = rule.copy(type = BillType.TRANSFER)
        val otherAccount = bill(sunday, 13) { it.copy(type = BillType.TRANSFER, accountId = 3) }
        assertEquals(emptyMap<LocalDate, Bill>(), transfer.existingBills(listOf(otherAccount), rule.dates(0, 1), emptySet()))
        assertEquals(listOf(sunday.plusWeeks(2), sunday.plusWeeks(3)), rule.dates(2, 4))
    }
}
