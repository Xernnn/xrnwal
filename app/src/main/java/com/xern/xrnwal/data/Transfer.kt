package com.xern.xrnwal.data

import com.xern.xrnwal.domain.Csv
import com.xern.xrnwal.domain.Money
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One bill read from a CSV file, before names are matched to ledgers, accounts and categories. */
data class ImportRow(
    val at: LocalDateTime,
    val type: Int,
    val category: String,
    val subcategory: String,
    val cents: Long,
    val currency: String,
    val rate: Double?,
    val account: String,
    val toAccount: String,
    val feeCents: Long,
    val ledger: String,
    val tags: List<String>,
    val note: String,
    val inBudget: Boolean,
)

data class ImportResult(val rows: List<ImportRow>, val skipped: List<String>)

/** Finds columns by name, so column order and small naming differences do not matter. */
object BillCsv {
    val header = listOf(
        "Date", "Time", "Type", "Category", "Subcategory", "Amount", "Currency", "Rate",
        "Account", "To Account", "Fee", "Ledger", "Tags", "Note", "In Budget",
    )

    private val aliases: Map<String, List<String>> = mapOf(
        "date" to listOf("date", "time", "datetime", "billtime", "recordtime", "billdate"),
        "time" to listOf("time", "clock"),
        "type" to listOf("type", "billtype", "kind", "incomeexpense"),
        "category" to listOf("category", "primarycategory", "maincategory", "toplevelcategory", "parentcategory", "firstcategory"),
        "subcategory" to listOf("subcategory", "secondarycategory", "childcategory", "secondcategory"),
        "amount" to listOf("amount", "money", "value", "sum"),
        "currency" to listOf("currency", "currencycode", "billcurrency"),
        "rate" to listOf("rate", "exchangerate", "fxrate"),
        "account" to listOf("account", "accounts", "paymentaccount", "fromaccount", "accountname"),
        "toaccount" to listOf("toaccount", "receiveaccount", "receivingaccount", "targetaccount", "transferaccount"),
        "fee" to listOf("fee", "fees", "transferfee", "servicecharge"),
        "ledger" to listOf("ledger", "ledgers", "book", "ledgername"),
        "tags" to listOf("tags", "tag", "label", "labels"),
        "note" to listOf("note", "notes", "remark", "remarks", "memo", "description"),
        "inbudget" to listOf("inbudget", "includedinbudget", "budget", "countinbudget"),
    )

    private val dateFormats = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm",
        "yyyy-MM-dd", "yyyy/MM/dd", "dd/MM/yyyy HH:mm", "dd/MM/yyyy", "yyyyMMdd",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    private fun key(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    fun looksFamiliar(headerRow: List<String>): Boolean = columns(headerRow).let { "date" in it && "amount" in it }

    private fun columns(headerRow: List<String>): Map<String, Int> {
        val keys = headerRow.map(::key)
        val found = mutableMapOf<String, Int>()
        aliases.forEach { (field, names) ->
            val index = names.firstNotNullOfOrNull { name ->
                keys.indexOf(name).takeIf { it >= 0 && it !in found.values }
            }
            if (index != null) found[field] = index
        }
        return found
    }

    private fun parseDate(date: String, time: String): LocalDateTime? {
        val text = listOf(date.trim(), time.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        for (format in dateFormats) {
            runCatching { return LocalDateTime.parse(text, format) }
            runCatching { return LocalDate.parse(text, format).atStartOfDay() }
        }
        return null
    }

    /** Some exports have no receiving-account column: a transfer's Category names both accounts, one per line. */
    private fun transferEnds(category: String): Pair<String, String>? =
        category.lines().map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.size == 2 }?.let { it[0] to it[1] }

    private fun parseType(text: String, cents: Long): Int = when (key(text)) {
        "income", "收入", "1" -> BillType.INCOME
        "transfer", "转账", "2" -> BillType.TRANSFER
        "expense", "expenses", "支出", "0" -> BillType.EXPENSE
        else -> if (cents > 0) BillType.INCOME else BillType.EXPENSE
    }

    fun read(text: String, defaultCurrency: String): ImportResult {
        val table = Csv.parse(text)
        val headerIndex = table.indexOfFirst(::looksFamiliar)
        if (headerIndex < 0) return ImportResult(emptyList(), listOf("No date and amount columns found"))
        val columns = columns(table[headerIndex])
        val rows = mutableListOf<ImportRow>()
        val skipped = mutableListOf<String>()
        table.drop(headerIndex + 1).forEachIndexed { index, row ->
            fun cell(field: String) = columns[field]?.let { row.getOrNull(it) }?.trim().orEmpty()
            val line = headerIndex + index + 2
            val at = parseDate(cell("date"), if (columns["time"] == columns["date"]) "" else cell("time"))
            val currency = cell("currency").uppercase().ifEmpty { defaultCurrency }
            // A file doesn't say which mark is its decimal, but the row's currency says whether it has cents.
            val wholeUnits = Money.wholeUnits(currency)
            val cents = Money.parse(cell("amount"), wholeUnits = wholeUnits)
            if (at == null || cents == null) {
                skipped += "Line $line: unreadable date or amount"
                return@forEachIndexed
            }
            val type = parseType(cell("type"), cents)
            val ends = if (type == BillType.TRANSFER && cell("toaccount").isEmpty()) transferEnds(cell("category")) else null
            rows += ImportRow(
                at = at,
                type = type,
                category = if (ends != null) "" else cell("category"),
                subcategory = cell("subcategory"),
                cents = kotlin.math.abs(cents),
                currency = currency,
                // Only a positive number is a rate. Zero would count the bill as nothing, and "NaN" can't be added up.
                rate = cell("rate").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 },
                account = cell("account").ifEmpty { ends?.first.orEmpty() },
                toAccount = ends?.second ?: cell("toaccount"),
                feeCents = Money.parse(cell("fee"), wholeUnits = wholeUnits) ?: 0,
                ledger = cell("ledger"),
                tags = cell("tags").split(';', '|', '，').map { it.trim() }.filter { it.isNotEmpty() },
                note = cell("note"),
                inBudget = cell("inbudget").lowercase() !in setOf("no", "false", "0", "n"),
            )
        }
        return ImportResult(rows, skipped)
    }

    /** Foreign currencies with no rate in the file or on this phone: importing them would count each unit as one [base]. */
    fun missingRates(rows: List<ImportRow>, base: String, known: Set<String>): List<String> =
        rows.filter { it.rate == null && it.currency != base && it.currency !in known }.map { it.currency }.distinct().sorted()

    /**
     * What currency an account created by the file should use, by lowercase name. A bill's currency is how it was
     * entered, not what the account holds, so only an account whose every bill shares one currency takes it.
     */
    fun accountCurrencies(rows: List<ImportRow>, base: String): Map<String, String> =
        rows.filter { it.account.isNotBlank() }
            .groupBy({ it.account.trim().lowercase() }, { it.currency })
            .mapValues { (_, currencies) -> currencies.distinct().singleOrNull() ?: base }

    fun write(snapshot: Snapshot, bills: List<Bill>): String {
        val dates = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val times = DateTimeFormatter.ofPattern("HH:mm:ss")
        val rows = bills.map { bill ->
            val category = snapshot.categoryById[bill.categoryId]
            val parent = category?.parentId?.let { snapshot.categoryById[it] }
            listOf(
                bill.dateTime.format(dates),
                bill.dateTime.format(times),
                when (bill.type) {
                    BillType.INCOME -> "Income"
                    BillType.TRANSFER -> "Transfer"
                    else -> "Expense"
                },
                (parent ?: category)?.name.orEmpty(),
                if (parent != null) category?.name.orEmpty() else "",
                Money.fromCents(bill.amountCents).toPlainString(),
                bill.currency,
                bill.rate.toString(),
                snapshot.accountById[bill.accountId]?.name.orEmpty(),
                snapshot.accountById[bill.toAccountId]?.name.orEmpty(),
                Money.fromCents(bill.feeCents).toPlainString(),
                snapshot.ledgerById[bill.ledgerId]?.name.orEmpty(),
                snapshot.tagsOf(bill.id).joinToString(";") { it.name },
                bill.note,
                if (bill.inBudget) "Yes" else "No",
            )
        }
        return Csv.write(listOf(header) + rows)
    }

    /** A single worked example for the Import screen's template. */
    fun template(): String = Csv.write(
        listOf(
            header,
            listOf("2026-11-01", "12:30:00", "Expense", "Food", "", "18.95", "AUD", "1", "Cash", "", "0", "General", "", "Lunch", "Yes"),
            listOf("2026-11-02", "09:00:00", "Income", "Salary", "", "650", "AUD", "1", "Cash", "", "0", "General", "", "Pay", "Yes"),
            listOf("2026-11-03", "10:00:00", "Transfer", "", "", "100", "AUD", "1", "Cash", "Savings", "0", "General", "", "", "Yes"),
        ),
    )

    fun millisOf(at: LocalDateTime): Long = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * What a bill read from a file is matched by against those stored: its date and time as entered, in
     * whole seconds as a file keeps them, so a file written in one time zone matches in another.
     */
    fun fingerprint(at: LocalDateTime, cents: Long, note: String, type: Int) = "${at.withNano(0)}|$cents|$note|$type"

    fun bigDecimal(cents: Long): BigDecimal = Money.fromCents(cents)

    fun midnight(): LocalTime = LocalTime.MIDNIGHT
}

/**
 * Matches imported rows against what is already stored. Each stored item answers for one row, so
 * importing a file twice adds nothing while two genuinely identical bills in one file both stay.
 */
class AlreadyStored<K>(stored: Iterable<K>) {
    private val unclaimed = stored.groupingBy { it }.eachCount().toMutableMap()

    /** True when [key] matches a stored item that no earlier row has used. */
    fun claim(key: K): Boolean {
        val left = unclaimed[key] ?: return false
        if (left == 0) return false
        unclaimed[key] = left - 1
        return true
    }
}
