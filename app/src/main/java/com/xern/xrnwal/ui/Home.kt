package com.xern.xrnwal.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xern.xrnwal.data.ALL_LEDGERS
import com.xern.xrnwal.data.Bill
import com.xern.xrnwal.data.BudgetStatus
import com.xern.xrnwal.data.Ledger
import com.xern.xrnwal.data.Seed
import com.xern.xrnwal.data.Stats
import com.xern.xrnwal.data.inRange
import com.xern.xrnwal.data.totals
import com.xern.xrnwal.domain.Dates
import com.xern.xrnwal.domain.Period
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen() {
    val s = snap()
    val settings = prefs()
    val ui = LocalAppUi.current
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val today = LocalDate.now()
    val rules = settings.periodRules
    val ledgerId = s.activeLedger(settings)
    val ledgerBills = remember(s, ledgerId) { s.inLedger(ledgerId) }
    val pages = rememberEndlessPages(ui.homePeriod, Period::shifted)
    val period = pages.current
    LaunchedEffect(period) { ui.homePeriod = period }
    val counted = remember(ledgerBills, settings.includeFuture, today) {
        if (settings.includeFuture) ledgerBills else ledgerBills.filter { it.date <= today }
    }
    var pickPeriod by remember { mutableStateOf(false) }
    var pickLedger by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val ledger = s.ledgerById[ledgerId]
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { pickLedger = true }
                    .padding(6.dp)
                    .semantics { contentDescription = "Ledger: ${ledger?.name ?: "All ledgers"}" },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(ledger?.let { Seed.ledgerCovers.getOrElse(it.cover) { Seed.ledgerCovers[0] }.first } ?: "📚", 28.dp)
                    Text(
                        ledger?.name ?: "All",
                        Modifier.padding(start = 6.dp).widthIn(max = 64.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            PeriodSwitcher(
                period.label(rules, today),
                onPrevious = { pages.turn(-1) },
                onNext = { pages.turn(1) },
                onPick = { pickPeriod = true },
                modifier = Modifier.weight(1f),
            )
            IconButton({ nav.navigate("filter") }) { Icon(Icons.Filled.Search, "Search", Modifier.size(28.dp)) }
            IconButton({ nav.navigate("calendar") }) { Icon(Icons.Filled.CalendarMonth, "Calendar", Modifier.size(28.dp)) }
        }
        EndlessPager(pages, Modifier.fillMaxSize()) { HomePage(it, counted) }
    }

    if (pickPeriod) {
        PeriodDialog(period, onDismiss = { pickPeriod = false }) {
            pages.jumpTo(it)
            pickPeriod = false
        }
    }
    if (pickLedger) {
        LedgerDialog(
            ledgers = s.ledgers,
            selected = ledgerId,
            allowAll = true,
            onDismiss = { pickLedger = false },
            onCreate = { pickLedger = false; nav.navigate("ledger/edit") },
        ) { id ->
            repo.store.update { it.copy(activeLedger = id) }
            pickLedger = false
        }
    }
}

/** One period's page. It works out its own figures, so the pages either side can show theirs while turning. */
@Composable
private fun HomePage(period: Period, counted: List<Bill>) {
    val s = snap()
    val settings = prefs()
    val nav = LocalNav.current
    val today = LocalDate.now()
    val rules = settings.periodRules
    val range = period.range(rules)
    val visible = remember(counted, range) { counted.inRange(range) }
    val totals = remember(visible) { visible.totals() }
    val balance = remember(counted, totals, range, settings.carryover) {
        if (settings.carryover) Stats.carriedBalance(counted, range.endInclusive) else totals.balance
    }
    val byDay = remember(visible) { visible.groupBy { it.date } }
    val days = remember(byDay, range, today) {
        (byDay.keys + if (today in range) setOf(today) else emptySet()).sortedDescending()
    }
    val month = (period as? Period.Month)?.month
    val budgetCents = s.budgets.firstOrNull { it.categoryId == null }?.amountCents ?: 0
    val status = remember(s, month, rules, today, budgetCents, settings.budgetEnabled, settings.includeFuture) {
        if (settings.budgetEnabled && month != null && budgetCents > 0) {
            Stats.budget(s, month, rules, today, budgetCents, settings.includeFuture)
        } else {
            null
        }
    }
    val openLists = if (settings.checklistOnHome) {
        s.checklists.mapNotNull { list ->
            s.checklistItems.filter { it.checklistId == list.id && !it.done }.takeIf { it.isNotEmpty() }?.let { list to it }
        }
    } else {
        emptyList()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SummaryCard(settings.cardStyle, totals.expense, totals.income, balance, status)
        }
        items(openLists, key = { "list${it.first.id}" }) { (list, open) ->
            Panel(Modifier.clickable { nav.navigate("checklist/${list.id}") }) {
                Column(Modifier.padding(16.dp)) {
                    Text(list.name, style = MaterialTheme.typography.titleMedium)
                    open.take(3).forEach { item ->
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckBoxOutlineBlank, null, Modifier.size(20.dp))
                            Text(item.text, Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (open.size > 3) Muted("+${open.size - 3} more", Modifier.padding(top = 6.dp))
                }
            }
        }
        items(days, key = { it.toEpochDay() }) { date ->
            val bills = byDay[date].orEmpty()
            if (bills.isEmpty()) {
                Column {
                    DayHeader(date, bills)
                    Panel {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("No record for today", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { nav.addBill(today) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, "Add bill", tint = Color.Black) }
                        }
                    }
                }
            } else {
                BillDays(bills, s) { nav.openBill(it.id) }
            }
        }
        if (days.isEmpty()) item { EmptyNote("No bills in this period") }
    }
}

@Composable
fun LedgerDialog(
    ledgers: List<Ledger>,
    selected: Long?,
    allowAll: Boolean,
    onDismiss: () -> Unit,
    onCreate: (() -> Unit)? = null,
    onPick: (Long) -> Unit,
) {
    DialogCard(onDismiss) {
        Box(Modifier.fillMaxWidth()) {
            Text("Ledgers", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
            if (onCreate != null) {
                IconButton(onCreate, Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Filled.Add, "New ledger") }
            }
        }
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (allowAll) LedgerOption("📚", "All ledgers", selected == ALL_LEDGERS) { onPick(ALL_LEDGERS) }
            ledgers.forEach { ledger ->
                LedgerOption(Seed.ledgerCovers.getOrElse(ledger.cover) { Seed.ledgerCovers[0] }.first, ledger.name, selected == ledger.id) {
                    onPick(ledger.id)
                }
            }
        }
    }
}

@Composable
private fun LedgerOption(icon: String, name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, 30.dp)
        Text(name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun Arrow(arrow: String, color: Color) {
    Icon(
        if (arrow == "↑") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
        null,
        Modifier.size(18.dp),
        tint = color,
    )
}

@Composable
private fun Stat(label: String, value: String, arrow: String?, arrowColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (arrow != null) Arrow(arrow, arrowColor)
            Muted(label)
        }
        Text(value, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

/** Expenses, income and balance in the chosen style, with the budget underneath when it is on. */
@Composable
fun SummaryCard(style: Int, expense: Long, income: Long, balance: Long, status: BudgetStatus?) {
    val f = fmt()
    val nav = LocalNav.current
    var lead by remember { mutableIntStateOf(0) }
    val labels = listOf("Expenses", "Income", "Balance")
    val values = listOf(f.money(expense), f.money(income), f.money(balance))
    val arrows = listOf(f.expenseArrow to f.expenseArrowColor, f.incomeArrow to f.incomeArrowColor, null to Color.Unspecified)
    Panel {
        when (style) {
            2 -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEachIndexed { i, label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        arrows[i].first?.let { Arrow(it, arrows[i].second) }
                        Muted(label, Modifier.weight(1f))
                        Text(values[i], style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            3, 4 -> {
                val centered = style == 4
                val others = (0..2).filter { it != lead }
                Column(
                    Modifier.fillMaxWidth().clickable { lead = (lead + 1) % 3 }.padding(16.dp),
                    horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        arrows[lead].first?.let { Arrow(it, arrows[lead].second) }
                        Muted(labels[lead])
                        Icon(Icons.Filled.SwapHoriz, "Switch figure", Modifier.padding(start = 4.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Text(values[lead], fontSize = 34.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        others.forEach { i ->
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                            ) {
                                Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    arrows[i].first?.let { Arrow(it, arrows[i].second) }
                                    Muted(labels[i])
                                }
                                Text(values[i], style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            else -> Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                labels.forEachIndexed { i, label ->
                    if (i > 0) Box(Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
                    Stat(label, values[i], arrows[i].first, arrows[i].second, Modifier.weight(1f))
                }
            }
        }
        if (status != null) {
            HorizontalLine()
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate("budget") }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ring(status.remainingPercent / 100f, size = 92.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Glyph(budgetFace(status.remainingCents, status.remainingPercent), 38.dp)
                        Text("${status.remainingPercent}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.padding(start = 20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BudgetLine("Budget", f.plain(status.budgetCents))
                    RemainingLine(status.remainingCents, "(${status.remainingPercent}%)")
                    BudgetLine("Daily avg", f.plain(status.dailyAverageCents))
                }
            }
        }
    }
}

@Composable
private fun HorizontalLine() {
    Box(Modifier.padding(horizontal = 16.dp)) { Divider() }
}

@Composable
fun BudgetLine(
    label: String,
    value: String,
    accent: String? = null,
    valueColor: Color = Color.Unspecified,
    trailing: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Muted(label, Modifier.weight(1f))
        if (accent != null) Text("$accent ", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

/**
 * How the budget is going, as a face: plenty left, some, little, none, over. A ring with a face reads
 * at a glance, where a percentage has to be read; the reference does the same with its mascot.
 */
fun budgetFace(remainingCents: Long, remainingPercent: Int): String = when {
    remainingCents < 0 -> "😱"
    remainingPercent >= 60 -> "😄"
    remainingPercent >= 30 -> "🙂"
    remainingPercent > 0 -> "😐"
    else -> "😟"
}

/** Over budget is a warning, not an error, so it's amber; red stays for money going out. */
val ExceededAmber = Color(0xFFF5A33A)

/** "Remaining" with a negative number makes people do sums, so an overspend is named and shown as positive. */
@Composable
fun RemainingLine(cents: Long, accent: String? = null) {
    val f = fmt()
    if (cents < 0) {
        BudgetLine("Exceeded", f.plain(-cents), valueColor = ExceededAmber)
    } else {
        BudgetLine("Remaining", f.plain(cents), accent)
    }
}

@Composable
fun CalendarScreen() {
    val s = snap()
    val settings = prefs()
    val nav = LocalNav.current
    val today = LocalDate.now()
    val months = rememberEndlessPages(YearMonth.from(today)) { month, steps -> month.plusMonths(steps.toLong()) }
    var selected by remember { mutableStateOf(today) }
    val ledgerId = s.activeLedger(settings)
    val bills = remember(s, ledgerId) { s.inLedger(ledgerId) }
    val byDay = remember(bills) { bills.groupBy { it.date } }

    Page(
        "Calendar",
        actions = {
            IconButton({ nav.addBill(selected) }) { Icon(Icons.Filled.Add, "Add bill on this date") }
        },
    ) {
        ArrowHeader(monthTitle(months.current), { months.turn(-1) }, { months.turn(1) })
        EndlessPager(months, Modifier.weight(1f).padding(top = 14.dp)) { month ->
            CalendarPage(month, bills, byDay, selected) { selected = it }
        }
    }
}

@Composable
private fun CalendarPage(
    month: YearMonth,
    bills: List<Bill>,
    byDay: Map<LocalDate, List<Bill>>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    val today = LocalDate.now()
    val firstDay = DayOfWeek.of(prefs().firstDayOfWeek)
    val monthBills = remember(bills, month) { bills.filter { YearMonth.from(it.date) == month } }
    val totals = remember(monthBills) { monthBills.totals() }

    Column(Modifier.fillMaxSize()) {
        ScrollBody {
            Panel {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    Stat("Expenses", f.money(totals.expense), f.expenseArrow, f.expenseArrowColor, Modifier.weight(1f))
                    Stat("Income", f.money(totals.income), f.incomeArrow, f.incomeArrowColor, Modifier.weight(1f))
                    Stat("Balance", f.money(totals.balance), null, Color.Unspecified, Modifier.weight(1f))
                }
            }
            Panel {
                // The weekdays on a band of the accent, as the reference's calendar has them.
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(vertical = 10.dp)) {
                    weekdayLabels(firstDay).forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Black, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column(Modifier.padding(8.dp)) {
                    monthCells(month, firstDay).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                Box(Modifier.weight(1f).height(62.dp).padding(1.dp)) {
                                    if (date != null) DayCell(date, byDay[date].orEmpty(), date == selected, date == today) { onSelect(date) }
                                }
                            }
                        }
                    }
                }
            }
            val dayBills = byDay[selected].orEmpty()
            if (dayBills.isEmpty()) {
                Column {
                    DayHeader(selected, emptyList())
                    Panel { Muted("No records", Modifier.padding(16.dp)) }
                }
            } else {
                BillDays(dayBills, s) { nav.openBill(it.id) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, bills: List<Bill>, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val f = fmt()
    val totals = remember(bills) { bills.totals() }
    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(top = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
        )
        if (totals.expense != 0L) Text(f.plain(totals.expense), color = f.expenseColor, fontSize = 10.sp, maxLines = 1)
        if (totals.income != 0L) Text(f.plain(totals.income), color = f.incomeColor, fontSize = 10.sp, maxLines = 1)
    }
}
