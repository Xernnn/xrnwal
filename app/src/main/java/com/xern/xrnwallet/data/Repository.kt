package com.xern.xrnwallet.data

import android.content.Context
import androidx.room.withTransaction
import com.xern.xrnwallet.work.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Base64

/** Written into every backup and server copy. Raise it when an older app couldn't read the newer shape. */
const val BACKUP_VERSION = 1

@Serializable
data class Backup(
    val version: Int = BACKUP_VERSION,
    val settings: String,
    val ledgers: List<Ledger>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val tags: List<Tag>,
    val bills: List<Bill>,
    val billTags: List<BillTag>,
    val budgets: List<Budget>,
    val recurring: List<Recurring>,
    val plans: List<SavingsPlan>,
    val deposits: List<SavingsDeposit>,
    val checklists: List<Checklist>,
    val checklistItems: List<ChecklistItem>,
    val reminders: List<Reminder>,
    val rates: List<CurrencyRate>,
    /** Base64 image data by file name, so a backup restores completely on another phone. */
    val photos: Map<String, String> = emptyMap(),
)

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    val store: SettingsStore,
    private val scope: CoroutineScope,
) {
    val dao: AppDao = db.dao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Every currency with a known rate to the base, refreshed on request. */
    private val marketRates = MutableStateFlow<Map<String, Double>>(emptyMap())
    val allRates: StateFlow<Map<String, Double>> = marketRates

    val snapshot: StateFlow<Snapshot> = run {
        val flows: List<Flow<List<*>>> = listOf(
            dao.ledgers(), dao.accounts(), dao.categories(), dao.tags(), dao.bills(), dao.billTags(),
            dao.budgets(), dao.recurring(), dao.plans(), dao.deposits(), dao.checklists(),
            dao.checklistItems(), dao.reminders(), dao.rates(),
        )
        val tables = combine(flows) { it.toList() }
        @Suppress("UNCHECKED_CAST")
        combine(tables, store.settings) { t, settings ->
            Snapshot(
                ledgers = t[0] as List<Ledger>,
                accounts = t[1] as List<Account>,
                categories = t[2] as List<Category>,
                tags = t[3] as List<Tag>,
                bills = t[4] as List<Bill>,
                billTags = t[5] as List<BillTag>,
                budgets = t[6] as List<Budget>,
                recurring = t[7] as List<Recurring>,
                plans = t[8] as List<SavingsPlan>,
                deposits = t[9] as List<SavingsDeposit>,
                checklists = t[10] as List<Checklist>,
                checklistItems = t[11] as List<ChecklistItem>,
                reminders = t[12] as List<Reminder>,
                rates = t[13] as List<CurrencyRate>,
                baseCurrency = settings.baseCurrency,
            )
        }.stateIn(scope, SharingStarted.Eagerly, Snapshot())
    }

    private fun launchIo(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) { block() }
    }

    suspend fun seedIfEmpty() {
        if (dao.ledgerCount() > 0) return
        val base = store.settings.value.baseCurrency
        db.withTransaction {
            val ledgerId = dao.put(Ledger(name = "General", currency = base))
            dao.put(Account(name = "Cash", icon = "💵", color = 0xFF4CAF50, currency = base))
            dao.putCategories(
                Seed.expenseCategories.mapIndexed { i, (name, icon) ->
                    Category(type = BillType.EXPENSE, name = name, icon = icon, sort = i)
                } + Seed.incomeCategories.mapIndexed { i, (name, icon) ->
                    Category(type = BillType.INCOME, name = name, icon = icon, sort = i)
                },
            )
            store.update { it.copy(activeLedger = ledgerId) }
        }
    }

    // Bills

    suspend fun saveBill(bill: Bill, tagIds: List<Long>): Long = db.withTransaction {
        val id = dao.put(bill)
        dao.deleteBillTags(id)
        dao.putBillTags(tagIds.map { BillTag(id, it) })
        id
    }

    fun updateBill(bill: Bill) = launchIo { dao.put(bill) }

    fun deleteBill(id: Long) = launchIo {
        db.withTransaction {
            dao.deleteBillTags(id)
            dao.deleteBill(id)
        }
    }

    // Generic saves used by the editors

    fun save(block: suspend AppDao.() -> Unit) = launchIo { dao.block() }

    /** Its recurring items go too, as they would only ever record into a ledger that isn't there. */
    fun deleteLedger(ledger: Ledger) = launchIo {
        db.withTransaction {
            dao.deleteBillTagsOfLedger(ledger.id)
            dao.deleteBillsOfLedger(ledger.id)
            dao.deleteRecurringOfLedger(ledger.id)
            dao.delete(ledger)
        }
        store.update { if (it.activeLedger == ledger.id) it.copy(activeLedger = ALL_LEDGERS) else it }
    }

    fun deleteAccount(account: Account) = launchIo {
        db.withTransaction {
            dao.detachAccount(account.id)
            dao.detachToAccount(account.id)
            dao.delete(account)
        }
    }

    fun deleteChecklist(checklist: Checklist) = launchIo {
        db.withTransaction {
            dao.deleteItemsOf(checklist.id)
            dao.delete(checklist)
        }
    }

    fun deletePlan(plan: SavingsPlan) = launchIo {
        db.withTransaction {
            dao.deleteDepositsOf(plan.id)
            dao.delete(plan)
        }
    }

    fun deleteTag(tag: Tag) = launchIo {
        db.withTransaction {
            dao.deleteTagLinks(tag.id)
            dao.delete(tag)
        }
    }

    fun saveReminder(reminder: Reminder) = launchIo {
        dao.put(reminder)
        Reminders.scheduleAll(context, dao.remindersNow())
    }

    fun deleteReminder(reminder: Reminder) = launchIo {
        dao.delete(reminder)
        Reminders.cancel(context, reminder.id)
    }

    // Recurring bills

    /** Records everything due up to [now]. Safe to call repeatedly: progress is kept on the rule. */
    suspend fun runRecurring(now: LocalDateTime = LocalDateTime.now()) {
        db.withTransaction {
            val knownTags = dao.tagsNow().map { it.id }.toSet()
            val ledgers = dao.ledgersNow().mapTo(HashSet()) { it.id }
            dao.recurringNow().forEach { rule ->
                val due = rule.due(now, ledgers)
                due.forEach { addRuleBill(rule, it, knownTags) }
                if (due.isNotEmpty()) dao.put(rule.copy(generated = rule.generated + due.size))
            }
        }
    }

    /** Records a bill on each of [dates], for dates a rule passed without one. */
    suspend fun recordDates(rule: Recurring, dates: List<LocalDate>) {
        db.withTransaction {
            val knownTags = dao.tagsNow().map { it.id }.toSet()
            dates.forEach { addRuleBill(rule, it.atTime(rule.minuteOfDay / 60, rule.minuteOfDay % 60), knownTags) }
        }
    }

    private suspend fun addRuleBill(rule: Recurring, at: LocalDateTime, knownTags: Set<Long>) {
        val billId = dao.put(
            Bill(
                ledgerId = rule.ledgerId,
                type = rule.type,
                categoryId = rule.categoryId,
                accountId = rule.accountId,
                toAccountId = rule.toAccountId,
                amountCents = rule.amountCents,
                currency = rule.currency,
                rate = snapshot.value.rate(rule.currency),
                note = rule.note,
                atMillis = BillCsv.millisOf(at),
                inBudget = rule.inBudget,
                recurringId = rule.id,
            ),
        )
        dao.putBillTags(rule.tags.filter { it in knownTags }.map { BillTag(billId, it) })
    }

    // Import and export

    /**
     * Adds rows to the database, creating any ledger, account, category or tag it has not seen.
     * Rows matching an existing bill are skipped, so importing a file twice changes nothing. Matching
     * counts occurrences, because two genuinely identical bills in one file must both be kept.
     */
    suspend fun importRows(rows: List<ImportRow>): Int = withContext(Dispatchers.IO) {
        db.withTransaction {
            val base = store.settings.value.baseCurrency
            val ledgers = dao.ledgersNow().associateBy { it.name.lowercase() }.toMutableMap()
            val accounts = dao.accountsNow().associateBy { it.name.trim().lowercase() }.toMutableMap()
            val categories = dao.categoriesNow().toMutableList()
            val tags = dao.tagsNow().associateBy { it.name.lowercase() }.toMutableMap()
            val rates = dao.ratesNow().associateBy { it.code }.toMutableMap()
            val existing = AlreadyStored(dao.billsNow().map { fingerprint(it.atMillis, it.amountCents, it.note, it.type) })
            val accountCurrencies = BillCsv.accountCurrencies(rows, base)

            suspend fun ledgerFor(name: String): Ledger {
                val key = name.ifEmpty { "General" }.lowercase()
                return ledgers.getOrPut(key) {
                    val ledger = Ledger(name = name.ifEmpty { "General" }, currency = base)
                    ledger.copy(id = dao.put(ledger))
                }
            }

            suspend fun accountFor(name: String): Account? {
                if (name.isBlank()) return null
                return accounts.getOrPut(name.trim().lowercase()) {
                    val account = Account(name = name.trim(), currency = accountCurrencies[name.trim().lowercase()] ?: base)
                    account.copy(id = dao.put(account))
                }
            }

            suspend fun categoryFor(name: String, type: Int, parent: Long?): Category? {
                if (name.isBlank()) return null
                return categories.firstOrNull {
                    it.type == type && it.parentId == parent && it.name.equals(name, ignoreCase = true)
                } ?: run {
                    val category = Category(type = type, name = name, icon = Seed.iconFor(name, type), parentId = parent, sort = categories.size)
                    category.copy(id = dao.put(category)).also { categories += it }
                }
            }

            var added = 0
            for (row in rows) {
                val millis = BillCsv.millisOf(row.at)
                if (existing.claim(fingerprint(millis, row.cents, row.note, row.type))) continue
                val parent = if (row.type == BillType.TRANSFER) null else categoryFor(row.category, row.type, null)
                val category = if (parent != null && row.subcategory.isNotBlank()) {
                    categoryFor(row.subcategory, row.type, parent.id)
                } else {
                    parent
                }
                val rate = row.rate ?: rates[row.currency]?.rate ?: 1.0
                if (row.currency != base && row.currency !in rates) {
                    val entry = CurrencyRate(row.currency, rate, rates.size)
                    dao.put(entry)
                    rates[row.currency] = entry
                }
                val id = dao.put(
                    Bill(
                        ledgerId = ledgerFor(row.ledger).id,
                        type = row.type,
                        categoryId = category?.id,
                        accountId = accountFor(row.account)?.id,
                        toAccountId = accountFor(row.toAccount)?.id,
                        amountCents = row.cents,
                        currency = row.currency,
                        rate = rate,
                        feeCents = row.feeCents,
                        note = row.note,
                        atMillis = millis,
                        inBudget = row.inBudget,
                    ),
                )
                val links = row.tags.map { name ->
                    val tag = tags.getOrPut(name.lowercase()) {
                        val created = Tag(name = name)
                        created.copy(id = dao.put(created))
                    }
                    BillTag(id, tag.id)
                }
                dao.putBillTags(links)
                added++
            }
            added
        }
    }

    /** Whole seconds, because that is all an exported file keeps. */
    private fun fingerprint(millis: Long, cents: Long, note: String, type: Int) =
        "${millis / 1000}|$cents|$note|$type"

    suspend fun backup(): String = withContext(Dispatchers.IO) {
        val bills = dao.billsNow()
        val encoder = Base64.getEncoder()
        json.encodeToString(
            Backup(
                settings = store.export(),
                ledgers = dao.ledgersNow(),
                accounts = dao.accountsNow(),
                categories = dao.categoriesNow(),
                tags = dao.tagsNow(),
                bills = bills,
                billTags = dao.billTagsNow(),
                budgets = dao.budgetsNow(),
                recurring = dao.recurringNow(),
                plans = dao.plansNow(),
                deposits = dao.depositsNow(),
                checklists = dao.checklistsNow(),
                checklistItems = dao.checklistItemsNow(),
                reminders = dao.remindersNow(),
                rates = dao.ratesNow(),
                photos = bills.mapNotNull { it.photo }.map(::File).filter(File::exists)
                    .associate { it.name to encoder.encodeToString(it.readBytes()) },
            ),
        )
    }

    /**
     * What the home server keeps: a backup with the app lock left out and photos named rather than
     * included. Rows are in a fixed order, because the sync compares this text to see what changed.
     */
    suspend fun syncDocument(): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            json.encodeToString(
                Backup(
                    settings = store.exportWithoutLock(),
                    ledgers = dao.ledgersNow().sortedBy { it.id },
                    accounts = dao.accountsNow().sortedBy { it.id },
                    categories = dao.categoriesNow().sortedBy { it.id },
                    tags = dao.tagsNow().sortedBy { it.id },
                    bills = dao.billsNow().sortedBy { it.id }.map { it.copy(photo = it.photo?.let(::File)?.name) },
                    billTags = dao.billTagsNow().sortedWith(compareBy({ it.billId }, { it.tagId })),
                    budgets = dao.budgetsNow().sortedBy { it.id },
                    recurring = dao.recurringNow().sortedBy { it.id },
                    plans = dao.plansNow().sortedBy { it.id },
                    deposits = dao.depositsNow().sortedBy { it.id },
                    checklists = dao.checklistsNow().sortedBy { it.id },
                    checklistItems = dao.checklistItemsNow().sortedBy { it.id },
                    reminders = dao.remindersNow().sortedBy { it.id },
                    rates = dao.ratesNow().sortedBy { it.code },
                ),
            )
        }
    }

    /** The photo files a server copy names, leaving out any name that isn't a plain file name. */
    fun photosIn(body: String): List<String> =
        json.decodeFromString<Backup>(body).bills.mapNotNull { it.photo }.map { File(it).name }
            .filter(::isSafePhotoName).distinct()

    fun photoFile(name: String): File = File(File(context.filesDir, "photos"), name)

    /**
     * Replaces everything with a backup. The file is fully parsed before anything is deleted.
     * [keepLock] is for server copies, which leave the app lock out: this phone keeps its own.
     */
    suspend fun restore(text: String, keepLock: Boolean = false) = withContext(Dispatchers.IO) {
        val backup = json.decodeFromString<Backup>(text)
        // This app would drop what it can't read, then send that back to the server without it.
        check(backup.version <= BACKUP_VERSION) { "Made by a newer version of the app" }
        val decoder = Base64.getDecoder()
        val photos = backup.photos.mapValues { decoder.decode(it.value) }
        val folder = File(context.filesDir, "photos").apply { mkdirs() }
        // Names come from the file, so only the last part of each is ever used as a path.
        photos.forEach { (name, bytes) -> File(folder, File(name).name).writeBytes(bytes) }
        db.withTransaction {
            dao.clearEverything()
            dao.putLedgers(backup.ledgers)
            dao.putAccounts(backup.accounts)
            dao.putCategories(backup.categories)
            dao.putTags(backup.tags)
            dao.putBills(backup.bills.map { bill -> bill.copy(photo = bill.photo?.let { File(folder, File(it).name).path }) })
            dao.putBillTags(backup.billTags)
            dao.putBudgets(backup.budgets)
            dao.putRecurring(backup.recurring)
            dao.putPlans(backup.plans)
            dao.putDeposits(backup.deposits)
            dao.putChecklists(backup.checklists)
            dao.putChecklistItems(backup.checklistItems)
            dao.putReminders(backup.reminders)
            dao.putRates(backup.rates)
        }
        if (keepLock) store.restoreKeepingLock(backup.settings) else store.restore(backup.settings)
        Reminders.scheduleAll(context, backup.reminders)
    }

    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        dao.remindersNow().forEach { Reminders.cancel(context, it.id) }
        dao.clearEverything()
        File(context.filesDir, "photos").deleteRecursively()
        seedIfEmpty()
    }

    /**
     * Re-expresses everything against a new base. [code] must already have a rate. Each bill's own
     * rate is rescaled, so converted history stays consistent instead of being revalued.
     */
    suspend fun changeBase(code: String) = withContext(Dispatchers.IO) {
        val previous = store.settings.value.baseCurrency
        val pivot = dao.ratesNow().firstOrNull { it.code == code }?.rate ?: return@withContext
        db.withTransaction {
            dao.putBills(dao.billsNow().map { it.copy(rate = it.rate / pivot) })
            val rebased = dao.ratesNow().filter { it.code != code }.map { it.copy(rate = it.rate / pivot) }
            dao.clearRates()
            dao.putRates(rebased + CurrencyRate(previous, 1.0 / pivot, rebased.size))
        }
        store.update { it.copy(baseCurrency = code) }
    }

    /** Fetches public rates for the base currency. Only ever called from the refresh button. */
    suspend fun refreshRates(): Boolean = withContext(Dispatchers.IO) {
        val base = store.settings.value.baseCurrency
        val parsed = runCatching {
            val connection = URL("https://open.er-api.com/v6/latest/$base").openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
            val rates = root["rates"] as kotlinx.serialization.json.JsonObject
            rates.mapValues { (_, value) -> 1.0 / value.toString().toDouble() }
        }.getOrNull() ?: return@withContext false
        marketRates.value = parsed
        dao.ratesNow().forEach { current ->
            parsed[current.code]?.let { dao.put(current.copy(rate = it)) }
        }
        store.update { it.copy(ratesUpdatedAt = System.currentTimeMillis()) }
        true
    }
}
