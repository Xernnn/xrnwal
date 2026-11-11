package com.xern.xrnwallet.data

import com.xern.xrnwallet.domain.DateRange
import com.xern.xrnwallet.domain.Money
import com.xern.xrnwallet.domain.Period
import com.xern.xrnwallet.domain.PeriodRules
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Every table in memory. Screens derive what they show from this, which keeps the sums testable. */
data class Snapshot(
    val ledgers: List<Ledger> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val billTags: List<BillTag> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val plans: List<SavingsPlan> = emptyList(),
    val deposits: List<SavingsDeposit> = emptyList(),
    val checklists: List<Checklist> = emptyList(),
    val checklistItems: List<ChecklistItem> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val rates: List<CurrencyRate> = emptyList(),
    val baseCurrency: String = "AUD",
) {
    val categoryById = categories.associateBy { it.id }
    val accountById = accounts.associateBy { it.id }
    val ledgerById = ledgers.associateBy { it.id }
    val tagById = tags.associateBy { it.id }
    private val rateByCode = rates.associate { it.code to it.rate }
    private val tagsByBill = billTags.groupBy({ it.billId }, { it.tagId })

    fun rate(code: String): Double = if (code == baseCurrency) 1.0 else rateByCode[code] ?: 1.0
    fun tagsOf(billId: Long): List<Tag> = tagsByBill[billId].orEmpty().mapNotNull { tagById[it] }
    fun tagIdsOf(billId: Long): List<Long> = tagsByBill[billId].orEmpty()

    fun categoryLabel(id: Long?): String {
        val category = categoryById[id] ?: return "Uncategorised"
        return category.name
    }

    fun topCategory(id: Long?): Category? {
        val category = categoryById[id] ?: return null
        return category.parentId?.let { categoryById[it] } ?: category
    }

    /**
     * Whether a bill or a recurring item uses [category] or one of its subcategories. Deleting it takes
     * the subcategories too, and anything left using one would be Uncategorised for good.
     */
    fun categoryInUse(category: Category): Boolean {
        val ids = categories.filter { it.parentId == category.id }.mapTo(hashSetOf(category.id)) { it.id }
        return bills.any { it.categoryId in ids } || recurring.any { it.categoryId in ids }
    }

    fun inLedger(ledgerId: Long): List<Bill> =
        if (ledgerId == ALL_LEDGERS) bills else bills.filter { it.ledgerId == ledgerId }

    private fun inAccountCurrency(bill: Bill, account: Account, cents: Long): Long =
        if (bill.currency == account.currency) cents
        else Money.convert(Money.convert(cents, bill.rate), 1.0 / rate(account.currency))

    /** The account whose currency [Bill.toAmountCents] is in: a transfer's receiving one, else the bill's own. */
    private fun fixedAccountId(bill: Bill): Long? = if (bill.type == BillType.TRANSFER) bill.toAccountId else bill.accountId

    /** What [bill] puts into or takes from its account, in the account's currency: as fixed, or worked out today. */
    private fun landed(bill: Bill, account: Account): Long =
        if (bill.currency == account.currency) bill.amountCents
        else bill.toAmountCents ?: inAccountCurrency(bill, account, bill.amountCents)

    /**
     * The amount [bill] puts into or takes from an account in another currency, fixed as it is saved, so
     * that account's balance doesn't move when rates are refreshed: the receiving account of a transfer,
     * or the account of an expense or income. Null within one currency. [before] is the bill as stored,
     * when one is edited: on the same account in the same currency it keeps the rate it was recorded at,
     * its amount scaled to a new one.
     */
    fun fixedAmount(bill: Bill, before: Bill? = null): Long? {
        val account = accountById[fixedAccountId(bill)] ?: return null
        if (account.currency == bill.currency) return null
        val kept = before?.toAmountCents
        if (kept != null && before.type == bill.type && before.currency == bill.currency &&
            fixedAccountId(before) == account.id && before.amountCents > 0
        ) {
            return BigDecimal.valueOf(kept).multiply(BigDecimal.valueOf(bill.amountCents))
                .divide(BigDecimal.valueOf(before.amountCents), 0, RoundingMode.HALF_UP).toLong()
        }
        return inAccountCurrency(bill, account, bill.amountCents)
    }

    /**
     * The bills whose fixed amount is in [account]'s currency, re-expressed for its change to [currency]
     * at today's rates, as the rest of its balance is. A bill in [currency] itself needs none.
     */
    fun fixedAmountsIn(account: Account, currency: String): List<Bill> {
        if (currency == account.currency) return emptyList()
        return bills.filter { it.toAmountCents != null && fixedAccountId(it) == account.id }.map { bill ->
            val kept = bill.toAmountCents ?: 0
            bill.copy(toAmountCents = if (bill.currency == currency) null else Money.convert(kept, rate(account.currency) / rate(currency)))
        }
    }

    /** Change a single bill makes to an account, in the account's own currency. */
    fun effectOn(bill: Bill, account: Account): Long {
        var total = 0L
        when (bill.type) {
            BillType.EXPENSE -> if (bill.accountId == account.id) total -= landed(bill, account)
            BillType.INCOME -> if (bill.accountId == account.id) total += landed(bill, account)
            BillType.TRANSFER -> {
                if (bill.accountId == account.id) {
                    total -= inAccountCurrency(bill, account, bill.amountCents + bill.feeCents)
                }
                if (bill.toAccountId == account.id) total += landed(bill, account)
            }
        }
        return total
    }

    /** What savings plans have paid into [account], their receive account, in its own currency. */
    fun depositsInto(account: Account): Long = plans.filter { it.accountId == account.id }.sumOf { plan ->
        val cents = deposits.filter { it.planId == plan.id }.sumOf { it.amountCents }
        if (plan.currency == account.currency) cents
        else Money.convert(Money.convert(cents, rate(plan.currency)), 1.0 / rate(account.currency))
    }

    fun balance(account: Account): Long =
        account.openingCents + bills.sumOf { effectOn(it, account) } + depositsInto(account)

    fun balanceInBase(account: Account): Long = Money.convert(balance(account), rate(account.currency))

    fun netAssets(): AssetSummary {
        var assets = 0L
        var debts = 0L
        accounts.filter { it.includeInTotal && !it.archived }.forEach {
            val value = balanceInBase(it)
            if (it.kind == AccountKind.CREDIT || value < 0) debts += value else assets += value
        }
        return AssetSummary(assets, debts)
    }

    fun historyOf(accountId: Long): List<Bill> =
        bills.filter { it.accountId == accountId || it.toAccountId == accountId }
}

data class AssetSummary(val assets: Long, val debts: Long) {
    val net: Long get() = assets + debts
}

data class Totals(val expense: Long, val income: Long) {
    val balance: Long get() = income - expense
}

fun Iterable<Bill>.totals(): Totals {
    var expense = 0L
    var income = 0L
    forEach {
        when (it.type) {
            BillType.EXPENSE -> expense += it.baseCents
            BillType.INCOME -> income += it.baseCents
        }
    }
    return Totals(expense, income)
}

fun List<Bill>.inRange(range: DateRange): List<Bill> = filter { it.date in range }

data class BudgetStatus(
    val budgetCents: Long,
    val spentCents: Long,
    val dailyAverageCents: Long,
) {
    val remainingCents: Long get() = budgetCents - spentCents
    val remainingPercent: Int
        get() = if (budgetCents <= 0) 0
        else (remainingCents.coerceAtLeast(0) * 100 / budgetCents).toInt()
}

object Stats {
    /**
     * Spending against a monthly budget. Budgets span ledgers. Bills marked as outside the budget
     * do not count, and future bills count only when [includeFuture] is on.
     */
    fun budget(
        snapshot: Snapshot,
        month: YearMonth,
        rules: PeriodRules,
        today: LocalDate,
        budgetCents: Long,
        includeFuture: Boolean,
        categoryId: Long? = null,
    ): BudgetStatus {
        val range = rules.monthRange(month)
        val spent = snapshot.bills.asSequence()
            .filter { it.type == BillType.EXPENSE && it.inBudget && it.date in range }
            .filter { includeFuture || it.date <= today }
            .filter {
                categoryId == null || it.categoryId == categoryId ||
                    snapshot.categoryById[it.categoryId]?.parentId == categoryId
            }
            .sumOf { it.baseCents }
        val daysLeft = if (today in range) ChronoUnit.DAYS.between(today, range.endInclusive) + 1 else 0
        val remaining = (budgetCents - spent).coerceAtLeast(0)
        return BudgetStatus(budgetCents, spent, if (daysLeft > 0) remaining / daysLeft else 0)
    }

    /** Income minus expenses for everything up to the period's end. */
    fun carriedBalance(bills: List<Bill>, end: LocalDate): Long =
        bills.filter { it.date <= end }.totals().balance

    /** Units an average divides by. The current period counts only what has elapsed. */
    fun averageUnits(period: Period, range: DateRange, today: LocalDate): Long {
        if (period is Period.Year) {
            return when {
                today.year == period.year -> today.monthValue.toLong()
                else -> 12
            }
        }
        val end = if (today in range) today else range.endInclusive
        return ChronoUnit.DAYS.between(range.start, end) + 1
    }

    fun average(totalCents: Long, units: Long): Long =
        if (units <= 0) 0 else Math.round(totalCents.toDouble() / units)

    data class Point(val label: String, val cents: Long)

    /** Monthly points for a year, daily points otherwise. Income or expenses only. */
    fun trend(bills: List<Bill>, period: Period, range: DateRange, type: Int): List<Point> {
        val matching = bills.filter { it.type == type && it.date in range }
        if (period is Period.Year) {
            val byMonth = matching.groupBy { it.date.monthValue }
            return (1..12).map { month ->
                Point(
                    java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    byMonth[month].orEmpty().sumOf { it.baseCents },
                )
            }
        }
        val byDay = matching.groupBy { it.date }
        return generateSequence(range.start) { it.plusDays(1) }
            .takeWhile { it <= range.endInclusive }
            .map { Point(it.dayOfMonth.toString(), byDay[it].orEmpty().sumOf { bill -> bill.baseCents }) }
            .toList()
    }

    data class Rank(val category: Category?, val cents: Long, val share: Double, val count: Int)

    /** Spending by category, largest first. [topLevel] folds subcategories into their parent. */
    fun ranking(snapshot: Snapshot, bills: List<Bill>, type: Int, topLevel: Boolean): List<Rank> {
        val matching = bills.filter { it.type == type }
        val total = matching.sumOf { it.baseCents }
        return matching
            .groupBy { (if (topLevel) snapshot.topCategory(it.categoryId) else snapshot.categoryById[it.categoryId])?.id }
            .map { (id, group) ->
                val cents = group.sumOf { it.baseCents }
                Rank(
                    snapshot.categoryById[id],
                    cents,
                    if (total == 0L) 0.0 else cents * 100.0 / total,
                    group.size,
                )
            }
            .sortedByDescending { it.cents }
    }

    fun top(bills: List<Bill>, type: Int, limit: Int = 10): List<Bill> =
        bills.filter { it.type == type }.sortedByDescending { it.baseCents }.take(limit)
}
