package com.xern.xrnwallet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xern.xrnwallet.domain.Money
import com.xern.xrnwallet.domain.RepeatKind
import com.xern.xrnwallet.domain.RepeatRule
import com.xern.xrnwallet.domain.SavingsType
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object BillType {
    const val EXPENSE = 0
    const val INCOME = 1
    const val TRANSFER = 2
}

object AccountKind {
    const val SAVINGS = 0
    const val CREDIT = 1
}

private const val MINUTES_IN_DAY = 24 * 60

/** Dates with a year of four digits. A rule starting far beyond them works out steps past the last date there is. */
private val CALENDAR = LocalDate.of(1, 1, 1).toEpochDay()..LocalDate.of(9999, 12, 31).toEpochDay()

/** Why a repeat stored as [kind] and [value] can't be worked out, or null. */
private fun repeatProblem(kind: String, value: Int): String? {
    val known = RepeatKind.entries.firstOrNull { it.name == kind } ?: return "repeats \"$kind\", which this app doesn't know"
    return when {
        known == RepeatKind.MONTHLY && value < 1 -> "repeats monthly on day $value"
        known == RepeatKind.WEEKLY && value !in 1..7 -> "repeats weekly on weekday $value, not 1 to 7"
        else -> null
    }
}

@Serializable
@Entity
data class Ledger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cover: Int = 0,
    val currency: String = "AUD",
    val sort: Int = 0,
)

@Serializable
@Entity
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: Int = AccountKind.SAVINGS,
    val icon: String = "💳",
    val color: Long = 0xFF3A3A3F,
    val currency: String = "AUD",
    val openingCents: Long = 0,
    val note: String = "",
    val includeInTotal: Boolean = true,
    val archived: Boolean = false,
    val sort: Int = 0,
)

@Serializable
@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val name: String,
    val icon: String,
    val parentId: Long? = null,
    val active: Boolean = true,
    val sort: Int = 0,
)

@Serializable
@Entity
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFFFFB300,
    val sort: Int = 0,
)

@Serializable
@Entity(indices = [Index("ledgerId"), Index("atMillis"), Index("recurringId")])
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerId: Long,
    val type: Int,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val amountCents: Long,
    val currency: String,
    /** Value of one unit in the base currency when recorded, so history keeps its value. */
    val rate: Double = 1.0,
    val feeCents: Long = 0,
    /**
     * The amount in the currency of the account it moves money in, when that isn't the bill's own: a
     * transfer's receiving account, or the account of an expense or income. Fixed as the bill is saved,
     * so refreshing rates doesn't move that account's balance. Null for bills saved before, which are
     * still worked out at today's rate. See [Snapshot.fixedAmount].
     */
    val toAmountCents: Long? = null,
    val note: String = "",
    val photo: String? = null,
    val atMillis: Long,
    val inBudget: Boolean = true,
    val recurringId: Long? = null,
) {
    val baseCents: Long get() = Money.convert(amountCents, rate)
    val dateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault())
    val date: LocalDate get() = dateTime.toLocalDate()
}

@Serializable
@Entity(primaryKeys = ["billId", "tagId"], indices = [Index("tagId")])
data class BillTag(val billId: Long, val tagId: Long)

/** Monthly budget. A null [categoryId] is the overall budget. */
@Serializable
@Entity(indices = [Index(value = ["categoryId"], unique = true)])
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val amountCents: Long,
)

@Serializable
@Entity
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerId: Long,
    val type: Int,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val amountCents: Long,
    val currency: String,
    val note: String = "",
    val startEpochDay: Long,
    val repeatKind: String = RepeatKind.MONTHLY.name,
    val repeatValue: Int = 1,
    val minuteOfDay: Int = 480,
    /** Total occurrences. Zero repeats forever. */
    val times: Int = 12,
    val generated: Int = 0,
    /** Every how many weeks, months or years. See [RepeatRule.every]. */
    val every: Int = 1,
    val inBudget: Boolean = true,
    /** Tag ids given to each bill this rule records, comma separated. */
    val tagIds: String = "",
) {
    val rule: RepeatRule get() = RepeatRule(RepeatKind.valueOf(repeatKind), repeatValue, every)
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val remaining: Int? get() = if (times == 0) null else (times - generated).coerceAtLeast(0)
    val tags: List<Long> get() = tagIds.split(',').mapNotNull { it.trim().toLongOrNull() }

    /** What in a rule from a file or the server this app couldn't work out, or null. See [Settings.problem]. */
    fun problem(): String? = when {
        repeatProblem(repeatKind, repeatValue) != null -> "a recurring item ${repeatProblem(repeatKind, repeatValue)}"
        minuteOfDay !in 0 until MINUTES_IN_DAY -> "a recurring item is due at minute $minuteOfDay of the day"
        startEpochDay !in CALENDAR -> "a recurring item starts on no date the calendar has"
        else -> null
    }

    /** The dates of this rule's steps [from] up to, but not including, [until]. */
    fun dates(from: Int, until: Int): List<LocalDate> = (from until until).map { rule.occurrence(start, it) }

    /**
     * When the steps due by [now] and not yet recorded fall. None while the rule's ledger isn't among
     * [ledgers], as when it was deleted by an older build that kept its rules: bills recorded there
     * would count in every total but show in no ledger.
     */
    fun due(now: LocalDateTime, ledgers: Set<Long>): List<LocalDateTime> {
        if (ledgerId !in ledgers) return emptyList()
        val due = ArrayList<LocalDateTime>()
        while (times == 0 || generated + due.size < times) {
            val at = rule.occurrence(start, generated + due.size).atTime(minuteOfDay / 60, minuteOfDay % 60)
            if (at.isAfter(now)) break
            due += at
        }
        return due
    }

    /**
     * The bill this rule records at [at], valued by [rates] against [base]. Both come from whoever reads
     * the rule, out of the database: the snapshot the screens use is still empty while the app starts,
     * which is when most rules come due, and an empty one values every currency at one of the base. Into
     * one of [accounts] in another currency, what it puts there is fixed now, as for a bill saved by hand.
     */
    fun billAt(at: LocalDateTime, base: String, rates: List<CurrencyRate>, accounts: List<Account>): Bill = Bill(
        ledgerId = ledgerId,
        type = type,
        categoryId = categoryId,
        accountId = accountId,
        toAccountId = toAccountId,
        amountCents = amountCents,
        currency = currency,
        rate = if (currency == base) 1.0 else rates.firstOrNull { it.code == currency }?.rate ?: 1.0,
        note = note,
        atMillis = BillCsv.millisOf(at),
        inBudget = inBudget,
        recurringId = id,
    ).let { it.copy(toAmountCents = Snapshot(accounts = accounts, rates = rates, baseCurrency = base).fixedAmount(it)) }

    /**
     * The bill already recorded for each of [dates], such as those imported from another app, by date.
     * It must match what the rule would write, with two allowances. It may sit a day off, because an app
     * counting 24-hour days drifts a day across daylight saving. It may be on another account, because
     * a payment can move, though for a transfer the accounts are what the bill is. An exact date wins and
     * each bill counts once. A bill still belonging to one of [otherRules] is taken. One left behind by a
     * deleted rule is free again.
     */
    fun existingBills(bills: List<Bill>, dates: List<LocalDate>, otherRules: Set<Long>): Map<LocalDate, Bill> {
        val free = bills.filterTo(ArrayList()) {
            it.recurringId !in otherRules && it.ledgerId == ledgerId && it.type == type &&
                it.categoryId == categoryId && it.toAccountId == toAccountId &&
                (type != BillType.TRANSFER || it.accountId == accountId) &&
                it.amountCents == amountCents && it.currency == currency && it.note == note
        }
        val found = HashMap<LocalDate, Bill>()
        for (offset in listOf(0L, -1L, 1L)) {
            for (date in dates) {
                if (date in found) continue
                val bill = free.firstOrNull { it.date == date.plusDays(offset) } ?: continue
                found[date] = bill
                free.remove(bill)
            }
        }
        return found
    }
}

@Serializable
@Entity
data class SavingsPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = SavingsType.FIXED.name,
    val name: String,
    val icon: String = "🐷",
    val targetCents: Long,
    val startCents: Long = 0,
    val currency: String,
    val startEpochDay: Long,
    val repeatKind: String = RepeatKind.MONTHLY.name,
    val repeatValue: Int = 1,
    val repetitions: Int = 12,
    val accountId: Long? = null,
    val note: String = "",
) {
    val planType: SavingsType get() = SavingsType.valueOf(type)
    val rule: RepeatRule get() = RepeatRule(RepeatKind.valueOf(repeatKind), repeatValue)
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)

    /** As [Recurring.problem]. */
    fun problem(): String? = when {
        SavingsType.entries.none { it.name == type } -> "a savings plan is of the kind \"$type\", which this app doesn't know"
        repeatProblem(repeatKind, repeatValue) != null -> "a savings plan ${repeatProblem(repeatKind, repeatValue)}"
        startEpochDay !in CALENDAR -> "a savings plan starts on no date the calendar has"
        else -> null
    }
}

@Serializable
@Entity(indices = [Index("planId")])
data class SavingsDeposit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val amountCents: Long,
    val atMillis: Long,
)

/**
 * A correction of an account's balance to what it really holds, as the reference keeps one when its
 * balance is edited: [afterCents] from [atMillis] on, where the bills had it at [beforeCents]. The
 * balance is then the latest correction plus what came after it, so bills before it no longer move
 * the balance; deleting the correction gives them back their say. Amounts are in the account's currency.
 */
@Serializable
@Entity(indices = [Index("accountId")])
data class BalanceAdjustment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val atMillis: Long,
    val beforeCents: Long,
    val afterCents: Long,
) {
    val differenceCents: Long get() = afterCents - beforeCents
    val dateTime: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault())
    val date: LocalDate get() = dateTime.toLocalDate()
}

@Serializable
@Entity
data class Checklist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
)

@Serializable
@Entity(indices = [Index("checklistId")])
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val text: String,
    val done: Boolean = false,
    val sort: Int = 0,
)

@Serializable
@Entity
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minuteOfDay: Int = 1200,
    /** 0 daily, 1 weekly, 2 monthly. */
    val repeat: Int = 0,
    val title: String = "Time to record your accounts",
    val body: String = "Record one now",
    val enabled: Boolean = true,
) {
    /** As [Recurring.problem]: its next time is worked out for every reminder after a restore and each boot. */
    fun problem(): String? =
        if (minuteOfDay !in 0 until MINUTES_IN_DAY) "a reminder is set for minute $minuteOfDay of the day" else null
}

/** A billing currency. [rate] is one unit expressed in the base currency. */
@Serializable
@Entity
data class CurrencyRate(
    @PrimaryKey val code: String,
    val rate: Double,
    val sort: Int = 0,
)
