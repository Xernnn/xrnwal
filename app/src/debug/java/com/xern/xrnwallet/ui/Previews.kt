package com.xern.xrnwallet.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.xern.xrnwallet.data.Account
import com.xern.xrnwallet.data.AccountKind
import com.xern.xrnwallet.data.AppDatabase
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Budget
import com.xern.xrnwallet.data.Insights
import com.xern.xrnwallet.data.Category
import com.xern.xrnwallet.data.Ledger
import com.xern.xrnwallet.data.Repository
import com.xern.xrnwallet.data.Seed
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.SettingsStore
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.domain.Period
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Random

// Made-up data for drawing the pages without a phone: android preview render "?:app"
// --fqn=com.xern.xrnwallet.ui.PreviewsKt.<Name>. Nothing here is anyone's real money.

private val today: LocalDate = LocalDate.now()
private val zone: ZoneId = ZoneId.systemDefault()

private const val CASH = 1L
private const val EVERYDAY = 2L
private const val CREDIT = 3L
private const val SAVER = 4L

private val sampleAccounts = listOf(
    Account(id = CASH, name = "Cash", icon = "💵", color = 0xFF4CAF50, openingCents = 18_000, sort = 0),
    Account(id = EVERYDAY, name = "Everyday", icon = "💳", color = 0xFF7C83FD, openingCents = 240_000, sort = 1),
    Account(id = CREDIT, name = "Credit card", kind = AccountKind.CREDIT, icon = "💳", color = 0xFFFF9800, sort = 2),
    Account(id = SAVER, name = "Saver", icon = "🐖", color = 0xFFD94CB5, openingCents = 900_000, sort = 3),
)

/** The seeded categories, numbered as a fresh install numbers them: expenses 1 to 20, income 21 on. */
private val sampleCategories: List<Category> =
    Seed.expenseCategories.mapIndexed { i, (name, icon) -> Category(id = i + 1L, type = BillType.EXPENSE, name = name, icon = icon, sort = i) } +
        Seed.incomeCategories.mapIndexed { i, (name, icon) -> Category(id = 21L + i, type = BillType.INCOME, name = name, icon = icon, sort = i) }

private fun categoryId(name: String, type: Int = BillType.EXPENSE): Long =
    sampleCategories.first { it.name == name && it.type == type }.id

/** Fourteen months of an ordinary student-and-job budget, the same every time it's drawn. */
private val sampleBills: List<Bill> = run {
    val random = Random(7)
    val bills = mutableListOf<Bill>()
    var id = 1L
    fun add(day: LocalDate, hour: Int, type: Int, category: String, cents: Long, account: Long, note: String = "", rule: Long? = null) {
        val at = day.atTime(LocalTime.of(hour, random.nextInt(60))).atZone(zone).toInstant().toEpochMilli()
        val categoryType = if (type == BillType.INCOME) BillType.INCOME else BillType.EXPENSE
        bills += Bill(
            id = id++, ledgerId = 1, type = type, categoryId = categoryId(category, categoryType), accountId = account,
            amountCents = cents, currency = "AUD", note = note, atMillis = at, recurringId = rule,
        )
    }
    fun between(low: Int, high: Int): Long = (low + random.nextInt(high - low + 1)) * 100L + random.nextInt(100)
    val start = YearMonth.from(today).minusMonths(13).atDay(1)
    var day = start
    while (day <= today) {
        val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
        val winter = day.monthValue in 6..8
        if (random.nextInt(100) < 75) add(day, 12, BillType.EXPENSE, "Food", between(9, 26), if (random.nextInt(5) == 0) CASH else EVERYDAY, listOf("Lunch", "Sushi train", "Pho", "Coles", "Woolworths")[random.nextInt(5)])
        if (random.nextInt(100) < 35) add(day, 19, BillType.EXPENSE, "Food", between(14, 48), EVERYDAY, listOf("Dinner", "Thai", "Ramen")[random.nextInt(3)])
        if (!weekend && random.nextInt(100) < 85) add(day, 8, BillType.EXPENSE, "Trans", between(3, 5), EVERYDAY, "Opal")
        if (random.nextInt(100) < 25) add(day, 15, BillType.EXPENSE, "Snacks", between(2, 8), CASH)
        if ((day.dayOfWeek == DayOfWeek.FRIDAY || day.dayOfWeek == DayOfWeek.SATURDAY) && random.nextInt(100) < 55) add(day, 21, BillType.EXPENSE, "Drink", between(12, 55), CREDIT, "Bar")
        if (weekend && random.nextInt(100) < 30) add(day, 16, BillType.EXPENSE, "Fun", between(18, 70), CREDIT, listOf("Cinema", "Bowling", "Concert")[random.nextInt(3)])
        if (day.dayOfWeek == DayOfWeek.SUNDAY) add(day, 11, BillType.EXPENSE, "Daily nes.", between(22, 64), EVERYDAY, "Groceries")
        if (random.nextInt(100) < 4) add(day, 17, BillType.EXPENSE, "Health", between(20, 90), EVERYDAY, "Pharmacy")
        if (random.nextInt(100) < (if (winter) 6 else 3)) add(day, 14, BillType.EXPENSE, "Clothing", between(30, 140), CREDIT)
        if (random.nextInt(100) < 3) add(day, 13, BillType.EXPENSE, "Education", between(25, 95), EVERYDAY, "Textbook")
        when (day.dayOfMonth) {
            1 -> add(day, 9, BillType.EXPENSE, "Rent", 165_000, EVERYDAY, "Rent", rule = 1)
            3 -> add(day, 7, BillType.EXPENSE, "Membership", 5_500, CREDIT, "Gym", rule = 2)
            12 -> add(day, 20, BillType.EXPENSE, "Gaming", 1_499, CREDIT, "Game pass", rule = 3)
            18 -> add(day, 10, BillType.EXPENSE, "Digital", 1_299, CREDIT, "Music", rule = 4)
            20 -> if (random.nextInt(3) == 0) add(day, 18, BillType.INCOME, "Side job", between(250, 600), EVERYDAY, "Tutoring")
        }
        if ((day.monthValue == 2 || day.monthValue == 7) && day.dayOfMonth == 15) add(day, 10, BillType.EXPENSE, "Education", 128_000, EVERYDAY, "Semester fees")
        if (day.dayOfWeek == DayOfWeek.THURSDAY && java.time.temporal.ChronoUnit.WEEKS.between(start, day) % 2 == 0L) {
            add(day, 6, BillType.INCOME, "Salary", 235_000, EVERYDAY, "Pay", rule = 5)
        }
        if (day.monthValue == 12 && day.dayOfMonth == 19) add(day, 9, BillType.INCOME, "Bonus", 150_000, EVERYDAY)
        if (day.monthValue % 3 == 0 && day.dayOfMonth == 28) add(day, 9, BillType.INCOME, "Dividend", between(90, 160), SAVER)
        day = day.plusDays(1)
    }
    // Next month's rent, already entered on the card it's always paid from: the newest bill by date.
    add(YearMonth.from(today).plusMonths(1).atDay(1), 9, BillType.EXPENSE, "Rent", 165_000, EVERYDAY, "Rent")
    // The bill entered last, on the credit card, for yesterday.
    add(today.minusDays(1), 13, BillType.EXPENSE, "Fun", 3_200, CREDIT, "Mini golf")
    bills.sortedWith(compareByDescending<Bill> { it.atMillis }.thenByDescending { it.id })
}

private val sampleSnapshot = Snapshot(
    ledgers = listOf(Ledger(id = 1, name = "General")),
    accounts = sampleAccounts,
    categories = sampleCategories,
    bills = sampleBills,
    budgets = listOf(Budget(id = 1, amountCents = 380_000)),
)

private val sampleSettings = Settings(activeLedger = 1, welcomeSeen = true)

/** SharedPreferences held in memory, for a settings store nothing is saved from. */
private class PreviewPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    override fun getAll(): Map<String, *> = values
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { values[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = apply { values[key] = value }
        override fun putLong(key: String, value: Long) = apply { values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
        override fun remove(key: String) = apply { values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit() = true
        override fun apply() = Unit
    }
}

/**
 * A page drawn from [snapshot] and [settings]. The repository is there only because some pages ask
 * for one; its scope is already over, so it never opens its database.
 */
@Composable
private fun Frame(settings: Settings = sampleSettings, snapshot: Snapshot = sampleSnapshot, period: Period? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val repo = remember {
        val stopped = CoroutineScope(Job().apply { cancel() })
        Repository(context, Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build(), SettingsStore(PreviewPrefs()), stopped)
    }
    val ui = remember { AppUi(today, settings.periodRules).also { if (period != null) it.reportPeriod = period } }
    val undo = remember { BillUndo(repo, CoroutineScope(Job().apply { cancel() })) }
    WalletTheme(settings) {
        CompositionLocalProvider(
            LocalFixedData provides FixedData(snapshot, settings),
            LocalRepo provides repo,
            LocalNav provides nav,
            LocalAppUi provides ui,
            LocalBillUndo provides undo,
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

private val thisMonth: Period get() = Period.Month(YearMonth.from(today))

@Preview(name = "Reports month", widthDp = 393, heightDp = 3000)
@Composable
fun ReportsMonthPreview() = Frame(period = thisMonth) { ReportsScreen() }

@Preview(name = "Reports year", widthDp = 393, heightDp = 3000)
@Composable
fun ReportsYearPreview() = Frame(period = Period.Year(today.year)) { ReportsScreen() }

@Preview(name = "Reports 90 days", widthDp = 393, heightDp = 3000)
@Composable
fun ReportsCustomPreview() = Frame(period = Period.Custom(today.minusDays(89), today)) { ReportsScreen() }

private val scrolled = sampleSettings.copy(trendScroll = true)

@Preview(name = "Reports month scrolling", widthDp = 393, heightDp = 3000)
@Composable
fun ReportsMonthScrollPreview() = Frame(settings = scrolled, period = thisMonth) { ReportsScreen() }

@Preview(name = "Reports year scrolling", widthDp = 393, heightDp = 3000)
@Composable
fun ReportsYearScrollPreview() = Frame(settings = scrolled, period = Period.Year(today.year)) { ReportsScreen() }

@Preview(name = "Reports 90 days scrolling", widthDp = 393, heightDp = 3000)
@Composable
fun ReportsCustomScrollPreview() = Frame(settings = scrolled, period = Period.Custom(today.minusDays(89), today)) { ReportsScreen() }

@Preview(name = "Reports 365 days", widthDp = 393, heightDp = 3000)
@Composable
fun ReportsLongPreview() = Frame(period = Period.Custom(today.minusDays(364), today)) { ReportsScreen() }

@Preview(name = "Reports month light", widthDp = 393, heightDp = 3000)
@Composable
fun ReportsMonthLightPreview() = Frame(settings = sampleSettings.copy(themeMode = Settings.THEME_LIGHT), period = thisMonth) { ReportsScreen() }

@Preview(name = "Add bill", widthDp = 393, heightDp = 873)
@Composable
fun AddBillPreview() = Frame { BillEditScreen(0, NO_DATE, BillType.EXPENSE) }

@Preview(name = "Add bill light", widthDp = 393, heightDp = 873)
@Composable
fun AddBillLightPreview() = Frame(settings = sampleSettings.copy(themeMode = Settings.THEME_LIGHT)) { BillEditScreen(0, NO_DATE, BillType.EXPENSE) }

@Preview(name = "Welcome", widthDp = 393, heightDp = 873)
@Composable
fun WelcomePreview() = Frame(snapshot = Snapshot(ledgers = sampleSnapshot.ledgers, accounts = sampleAccounts.take(1), categories = sampleCategories)) { WelcomeScreen() }

/** The twelve-month cards on their own, as they sit below the ranking on Reports. */
@Composable
private fun TwelveMonths(period: Period) {
    val rules = sampleSettings.periodRules
    val range = period.range(rules)
    val months = Insights.window(period, range, rules)
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FlowCard(Insights.flows(sampleBills, months, rules), months.last())
        MixCard(Insights.mix(sampleSnapshot, sampleBills, months, rules), months, months.last())
    }
}

@Preview(name = "Twelve months", widthDp = 393, heightDp = 900)
@Composable
fun TwelveMonthsPreview() = Frame { TwelveMonths(thisMonth) }

@Preview(name = "Twelve months light", widthDp = 393, heightDp = 900)
@Composable
fun TwelveMonthsLightPreview() = Frame(settings = sampleSettings.copy(themeMode = Settings.THEME_LIGHT)) { TwelveMonths(thisMonth) }

/** The four drawings that used to be too dark for their circle, among some ordinary ones. */
@Composable
private fun CategoryIcons() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(listOf("🎓", "🎵", "🐾", "🔌", "🍗"), listOf("🎬", "🎮", "📷", "🏠", "💊")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { row.forEach { Bubble(it, 58.dp) } }
        }
    }
}

@Preview(name = "Category icons", widthDp = 393, heightDp = 170)
@Composable
fun CategoryIconsPreview() = Frame { CategoryIcons() }

@Preview(name = "Category icons light", widthDp = 393, heightDp = 170)
@Composable
fun CategoryIconsLightPreview() = Frame(settings = sampleSettings.copy(themeMode = Settings.THEME_LIGHT)) { CategoryIcons() }

/** Every launcher colour as the family tile, to hold against docs/icon.svg. */
@Preview(name = "App tiles", widthDp = 393, heightDp = 220)
@Composable
fun AppTilesPreview() = Frame {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcons.choices.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { row.forEach { AppTile(76.dp, choice = it) } }
        }
    }
}
