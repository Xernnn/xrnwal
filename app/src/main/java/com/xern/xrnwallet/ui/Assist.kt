package com.xern.xrnwallet.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillCsv
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.CurrencyRate
import com.xern.xrnwallet.data.ImportResult
import com.xern.xrnwallet.data.Reminder
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.data.UnusableBackup
import com.xern.xrnwallet.data.totals
import com.xern.xrnwallet.domain.Belonging
import com.xern.xrnwallet.domain.Currencies
import com.xern.xrnwallet.domain.Dates
import com.xern.xrnwallet.domain.DecimalMode
import com.xern.xrnwallet.domain.Money
import com.xern.xrnwallet.domain.SeparatorStyle
import com.xern.xrnwallet.domain.WeekendRule
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppearanceScreen() {
    val settings = prefs()
    val repo = LocalRepo.current
    Page("Appearance") {
        ScrollBody {
            Panel {
                listOf(
                    Settings.THEME_LIGHT to "Light",
                    Settings.THEME_DARK to "Dark",
                    Settings.THEME_SYSTEM to "Automatically follow system",
                ).forEachIndexed { index, (mode, label) ->
                    if (index > 0) Divider()
                    Row(
                        Modifier.fillMaxWidth().clickable { repo.store.update { it.copy(themeMode = mode) } }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (settings.themeMode == mode) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            SectionTitle("Colour theme")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                accents.forEachIndexed { index, (name, color) ->
                    Column(
                        Modifier.clickable { repo.store.update { it.copy(accent = index) } }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(color)), contentAlignment = Alignment.Center) {
                            if (settings.accent == index) Icon(Icons.Filled.Check, "Selected", tint = Color.Black)
                        }
                        Text(name, Modifier.padding(top = 4.dp), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersScreen() {
    val s = snap()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    Page(
        "Reminders",
        actions = { IconButton({ nav.navigate("reminder/edit") }) { Icon(Icons.Filled.AddBox, "New reminder") } },
    ) {
        ScrollBody {
            if (s.reminders.isEmpty()) EmptyNote("No reminders yet")
            s.reminders.forEach { reminder ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 18.dp)) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { nav.navigate("reminder/edit?id=${reminder.id}") }
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                        ) {
                            Text(reminder.name, style = MaterialTheme.typography.bodyLarge)
                            Muted("${Dates.time(LocalTime.of(reminder.minuteOfDay / 60, reminder.minuteOfDay % 60))}  ${REPEATS[reminder.repeat]}")
                        }
                        Switch(
                            reminder.enabled,
                            { on -> repo.saveReminder(reminder.copy(enabled = on)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.Black),
                        )
                    }
                }
            }
        }
    }
}

private val REPEATS = listOf("Daily", "Weekly", "Monthly")
private val PRESETS = listOf(
    "Time to record your accounts" to "Record one now",
    "Did you spend today?" to "Add it before you forget",
    "Check your budget" to "See how this month is going",
)

@Composable
fun ReminderEditScreen(id: Long) {
    val s = snap()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    val existing = remember { s.reminders.firstOrNull { it.id == id } }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var minute by remember { mutableIntStateOf(existing?.minuteOfDay ?: 1200) }
    var repeat by remember { mutableIntStateOf(existing?.repeat ?: 0) }
    var title by remember { mutableStateOf(existing?.title ?: PRESETS[0].first) }
    var body by remember { mutableStateOf(existing?.body ?: PRESETS[0].second) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Page(
        if (existing == null) "New reminder" else "Edit reminder",
        actions = {
            if (existing != null) {
                IconButton({
                    repo.deleteReminder(existing)
                    nav.leavePage()
                }) { Icon(Icons.Filled.Delete, "Delete reminder", tint = Color(0xFFFF3B30)) }
            }
        },
    ) {
        ScrollBody {
            WalletField(name, { name = it }, Modifier.fillMaxWidth(), "Name")
            Panel {
                ListRow("Time", Dates.time(LocalTime.of(minute / 60, minute % 60))) { dialog = "time" }
                Divider()
                ListRow("Repeat", REPEATS[repeat]) { dialog = "repeat" }
                Divider()
                ListRow("Notification content", "Preset") { dialog = "preset" }
            }
            WalletField(title, { title = it }, Modifier.fillMaxWidth(), "Title")
            WalletField(body, { body = it }, Modifier.fillMaxWidth(), "Body")
            PrimaryButton("Save", Modifier.fillMaxWidth()) {
                if (name.isBlank()) {
                    Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@PrimaryButton
                }
                if (Build.VERSION.SDK_INT >= 33 &&
                    PermissionChecker.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED
                ) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                val base = existing ?: Reminder(name = name)
                // Set weekly or monthly today, it counts from today, as a new one does.
                val start = if (base.repeat == repeat) base.startEpochDay else LocalDate.now().toEpochDay()
                repo.saveReminder(base.copy(name = name.trim(), minuteOfDay = minute, repeat = repeat, title = title, body = body, startEpochDay = start))
                nav.leavePage()
            }
        }
    }
    when (dialog) {
        "time" -> TimeDialog(LocalTime.of(minute / 60, minute % 60), { dialog = null }) {
            minute = it.hour * 60 + it.minute
            dialog = null
        }
        "repeat" -> OptionDialog("Repeat", listOf(0, 1, 2), repeat, { REPEATS[it] }, { dialog = null }) {
            repeat = it
            dialog = null
        }
        "preset" -> OptionDialog("Notification content", PRESETS, null, { it.first }, { dialog = null }) {
            title = it.first
            body = it.second
            dialog = null
        }
    }
}

@Composable
fun ExchangeScreen() {
    val s = snap()
    val settings = prefs()
    val repo = LocalRepo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val codes = listOf(settings.baseCurrency) + s.rates.map { it.code }.filter { it != settings.baseCurrency }
    var active by remember { mutableStateOf(settings.baseCurrency) }
    var text by remember { mutableStateOf("100") }
    var busy by remember { mutableStateOf(false) }

    fun valueOf(code: String): String {
        if (code == active) return text
        val entered = text.replace(',', '.').toBigDecimalOrNull() ?: return ""
        val inBase = entered * BigDecimal(s.rate(active))
        val converted = inBase.divide(BigDecimal(s.rate(code)), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return if (settings.separators == SeparatorStyle.DOT_COMMA.ordinal) converted.replace('.', ',') else converted
    }

    Page(
        "Exchange Rate",
        actions = {
            IconButton({
                if (busy) return@IconButton
                busy = true
                scope.launch {
                    val updated = repo.refreshRates()
                    busy = false
                    Toast.makeText(context, if (updated) "Rates updated" else "Could not reach the rate service", Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Filled.Refresh, "Refresh rates") }
        },
    ) {
        ScrollBody {
            codes.forEach { code ->
                val info = Currencies.info(code)
                Panel {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(info.flag, fontSize = 26.sp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(code, style = MaterialTheme.typography.bodyLarge)
                            Muted(info.name, maxLines = 1)
                        }
                        WalletField(
                            valueOf(code),
                            {
                                active = code
                                text = it
                            },
                            Modifier.width(170.dp),
                            numeric = true,
                        )
                    }
                }
            }
            if (settings.ratesUpdatedAt > 0) {
                val at = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(settings.ratesUpdatedAt), java.time.ZoneId.systemDefault())
                Muted("Updated ${Dates.full(at)}")
            }
        }
    }
}

/** Search terms split on commas. A bill matches if any term hits its category, note, tags or amount. */
private fun matchesQuery(bill: Bill, s: Snapshot, terms: List<String>): Boolean {
    if (terms.isEmpty()) return true
    val parent = s.topCategory(bill.categoryId)?.name.orEmpty()
    val haystack = buildList {
        add(s.categoryLabel(bill.categoryId))
        add(parent)
        add(bill.note)
        s.tagsOf(bill.id).forEach { add(it.name) }
        add(Money.fromCents(bill.amountCents).stripTrailingZeros().toPlainString())
    }.map { it.lowercase() }
    return terms.any { term -> haystack.any { term in it } }
}

data class BillQuery(
    val text: String = "",
    val types: Set<Long> = emptySet(),
    val categories: Set<Long> = emptySet(),
    val ledgers: Set<Long> = emptySet(),
    val accounts: Set<Long> = emptySet(),
    val tags: Set<Long> = emptySet(),
    val minCents: Long? = null,
    val maxCents: Long? = null,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
) {
    fun apply(s: Snapshot): List<Bill> {
        val terms = text.split(',', '，').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val expanded = categories + s.categories.filter { it.parentId in categories }.map { it.id }
        return s.bills.filter { bill ->
            (types.isEmpty() || bill.type.toLong() in types) &&
                (expanded.isEmpty() || bill.categoryId in expanded) &&
                (ledgers.isEmpty() || bill.ledgerId in ledgers) &&
                (accounts.isEmpty() || bill.accountId in accounts || bill.toAccountId in accounts) &&
                (tags.isEmpty() || s.tagIdsOf(bill.id).any { it in tags }) &&
                (minCents == null || bill.baseCents >= minCents) &&
                (maxCents == null || bill.baseCents <= maxCents) &&
                (start == null || bill.date >= start) &&
                (end == null || bill.date <= end) &&
                matchesQuery(bill, s, terms)
        }
    }
}

private val AMOUNT_PRESETS = listOf(
    0L to 199L, 200L to 499L, 500L to 999L, 1000L to 1499L, 1500L to 2999L, 3000L to 9999L,
)
private val BILL_TYPES = listOf(
    BillType.EXPENSE.toLong() to "Expenses",
    BillType.INCOME.toLong() to "Income",
    BillType.TRANSFER.toLong() to "Transfer",
)

@Composable
private fun QueryDialogs(open: String?, query: BillQuery, onClose: () -> Unit, onChange: (BillQuery) -> Unit) {
    val s = snap()
    when (open) {
        "amount" -> AmountDialog(query, onClose, onChange)
        "start" -> DateDialog("Start time", query.start ?: LocalDate.now(), onDismiss = onClose) {
            onChange(query.copy(start = it))
            onClose()
        }
        "end" -> DateDialog("End time", query.end ?: LocalDate.now(), onDismiss = onClose) {
            onChange(query.copy(end = it))
            onClose()
        }
    }
}

/** A filter chip whose list opens under the row of chips. */
@Composable
private fun DropPill(text: String, selected: Boolean, open: Boolean, onClick: () -> Unit) {
    Pill("$text ${if (open) "▴" else "▾"}", selected, onClick = onClick)
}

/** The list for whichever chip is open, or nothing when none is. */
@Composable
private fun DropPanel(open: String?, query: BillQuery, onClose: () -> Unit, onChange: (BillQuery) -> Unit) {
    val s = snap()
    when (open) {
        "type" -> FilterPanel(BILL_TYPES, query.types, onClose) { onChange(query.copy(types = it)) }
        "category" -> FilterPanel(
            s.categories.filter { it.parentId == null }.sortedBy { it.type }.map { it.id to it.name },
            query.categories,
            onClose,
        ) { onChange(query.copy(categories = it)) }
        "ledger" -> FilterPanel(s.ledgers.map { it.id to it.name }, query.ledgers, onClose) { onChange(query.copy(ledgers = it)) }
        "account" -> FilterPanel(s.accounts.map { it.id to it.name.trim() }, query.accounts, onClose) { onChange(query.copy(accounts = it)) }
        "tags" -> FilterPanel(s.tags.map { it.id to it.name }, query.tags, onClose) { onChange(query.copy(tags = it)) }
    }
}

@Composable
private fun FilterPanel(options: List<Pair<Long, String>>, chosen: Set<Long>, onClose: () -> Unit, onDone: (Set<Long>) -> Unit) {
    var picked by remember(options) { mutableStateOf(chosen) }
    Panel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                options.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (id, name) ->
                            val on = id in picked
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { picked = if (on) picked - id else picked + id }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    name,
                                    color = if (on) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GhostButton("Reset", Modifier.weight(1f)) {
                    onDone(emptySet())
                    onClose()
                }
                PrimaryButton("Confirm", Modifier.weight(1f)) {
                    onDone(picked)
                    onClose()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountDialog(query: BillQuery, onDismiss: () -> Unit, onChange: (BillQuery) -> Unit) {
    val f = fmt()
    var low by remember { mutableStateOf(query.minCents?.let { (it / 100).toString() }.orEmpty()) }
    var high by remember { mutableStateOf(query.maxCents?.let { (it / 100).toString() }.orEmpty()) }
    DialogCard(onDismiss) {
        Text("Amount", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            WalletField(low, { low = it }, Modifier.weight(1f), "Min", numeric = true)
            Text(" - ")
            WalletField(high, { high = it }, Modifier.weight(1f), "Max", numeric = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AMOUNT_PRESETS.forEach { (from, to) ->
                Pill("$from-$to", low == from.toString() && high == to.toString()) {
                    low = from.toString()
                    high = to.toString()
                }
            }
        }
        CancelConfirmRow(onDismiss) {
            onChange(query.copy(minCents = f.parse(low), maxCents = f.parse(high)))
            onDismiss()
        }
    }
}

@Composable
fun FilterScreen(category: Long, start: Long, end: Long, type: Int) {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    var query by remember {
        mutableStateOf(
            BillQuery(
                types = if (type >= 0) setOf(type.toLong()) else emptySet(),
                categories = if (category != 0L) setOf(category) else emptySet(),
                start = start.takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay),
                end = end.takeIf { it != NO_DATE }?.let(LocalDate::ofEpochDay),
            ),
        )
    }
    var open by remember { mutableStateOf<String?>(null) }
    val results = remember(s, query) { query.apply(s) }
    val totals = remember(results) { results.totals() }
    val days = remember(results) { results.groupBy { it.date }.toList() }

    Page("Filter") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null)
                WalletField(
                    query.text,
                    { query = query.copy(text = it) },
                    Modifier.padding(start = 8.dp).weight(1f),
                    "Search",
                )
            }
            Row(
                Modifier
                    .padding(vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DropPill("Bill type", query.types.isNotEmpty(), open == "type") { open = "type".takeIf { it != open } }
                DropPill("Category", query.categories.isNotEmpty(), open == "category") { open = "category".takeIf { it != open } }
                DropPill("Ledger", query.ledgers.isNotEmpty(), open == "ledger") { open = "ledger".takeIf { it != open } }
                DropPill("Account", query.accounts.isNotEmpty(), open == "account") { open = "account".takeIf { it != open } }
                DropPill("Tags", query.tags.isNotEmpty(), open == "tags") { open = "tags".takeIf { it != open } }
                Pill("Amount", query.minCents != null || query.maxCents != null) { open = "amount" }
                Pill(query.start?.let(Dates::iso) ?: "From", query.start != null) { open = "start" }
                Pill(query.end?.let(Dates::iso) ?: "To", query.end != null) { open = "end" }
                if (query != BillQuery()) Pill("Reset", false) { query = BillQuery() }
            }
            DropPanel(open, query, { open = null }) { query = it }
            Panel {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    SummaryCell("Records", results.size.toString(), Modifier.weight(1f))
                    SummaryCell("Expenses", f.money(totals.expense), Modifier.weight(1f))
                    SummaryCell("Income", f.money(totals.income), Modifier.weight(1f))
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            items(days, key = { it.first.toEpochDay() }) { (_, bills) ->
                BillDays(bills, s) { nav.openBill(it.id) }
            }
        }
    }
    QueryDialogs(open, query, { open = null }) { query = it }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Muted(label)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun writeUri(context: Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
}

private fun readUri(context: Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

@Composable
fun ExportScreen() {
    val s = snap()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember {
        mutableStateOf(BillQuery(start = s.bills.lastOrNull()?.date ?: LocalDate.now(), end = LocalDate.now()))
    }
    var open by remember { mutableStateOf<String?>(null) }
    val bills = remember(s, query) { query.apply(s).reversed() }
    fun label(chosen: Set<Long>, names: Map<Long, String>) = when {
        chosen.isEmpty() -> "All"
        chosen.size == 1 -> names[chosen.first()].orEmpty()
        else -> "${chosen.size} selected"
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val text = BillCsv.write(s, bills)
            scope.launch {
                withContext(Dispatchers.IO) { writeUri(context, uri, text) }
                Toast.makeText(context, "Saved ${billCount(bills.size)}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Page("Export Data") {
        ScrollBody {
            Panel {
                ListRow("Start time", query.start?.let(Dates::iso)) { open = "start" }
                Divider()
                ListRow("End time", query.end?.let(Dates::iso)) { open = "end" }
                Divider()
                ListRow("Ledgers", label(query.ledgers, s.ledgers.associate { it.id to it.name })) { open = "ledger" }
                Divider()
                ListRow("Accounts", label(query.accounts, s.accounts.associate { it.id to it.name.trim() })) { open = "account" }
                Divider()
                ListRow("Bill type", label(query.types, BILL_TYPES.toMap())) { open = "type" }
                Divider()
                ListRow("Category", label(query.categories, s.categories.associate { it.id to it.name })) { open = "category" }
                Divider()
                ListRow("Tags", label(query.tags, s.tags.associate { it.id to it.name })) { open = "tags" }
            }
            DropPanel(open, query, { open = null }) { query = it }
            Muted(billCount(bills.size))
            PrimaryButton("Save to a file", Modifier.fillMaxWidth(), enabled = bills.isNotEmpty()) {
                saver.launch("xrnwallet-${LocalDate.now()}.csv")
            }
            GhostButton("Share CSV", Modifier.fillMaxWidth()) {
                val text = BillCsv.write(s, bills)
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        File(context.cacheDir, "exports").apply { mkdirs() }
                            .resolve("xrnwallet-${LocalDate.now()}.csv")
                            .apply { writeText(text) }
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val share = Intent(Intent.ACTION_SEND)
                        .setType("text/csv")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(share, "Export"))
                }
            }
        }
    }
    QueryDialogs(open, query, { open = null }) { query = it }
}

@Composable
fun ImportScreen() {
    val s = snap()
    val settings = prefs()
    val repo = LocalRepo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var parsed by remember { mutableStateOf<ImportResult?>(null) }
    var finished by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val inbox = remember { File(context.filesDir, "inbox/received.csv") }

    fun load(text: String?) {
        finished = null
        parsed = text?.let { BillCsv.read(it, settings.baseCurrency) }
            ?: ImportResult(emptyList(), listOf("The file could not be read"))
    }

    // A file shared to the app waits here until it is imported, even if the app closes first.
    LaunchedEffect(Unit) {
        if (inbox.exists()) load(withContext(Dispatchers.IO) { inbox.readText() })
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { load(withContext(Dispatchers.IO) { runCatching { readUri(context, uri) }.getOrNull() }) }
    }
    val template = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { withContext(Dispatchers.IO) { writeUri(context, uri, BillCsv.template()) } }
    }

    Page("Import Data") {
        ScrollBody {
            PrimaryButton("Choose file", Modifier.fillMaxWidth()) { picker.launch(arrayOf("*/*")) }
            parsed?.let { result ->
                val rows = result.rows
                val noRate = BillCsv.missingRates(rows, settings.baseCurrency, s.rates.map { it.code }.toSet())
                Panel {
                    ListRow("Bills found", rows.size.toString())
                    if (rows.isNotEmpty()) {
                        Divider()
                        ListRow("From", Dates.iso(rows.minOf { it.at }.toLocalDate()))
                        Divider()
                        ListRow("To", Dates.iso(rows.maxOf { it.at }.toLocalDate()))
                        Divider()
                        ListRow("Expenses", rows.count { it.type == BillType.EXPENSE }.toString())
                        Divider()
                        ListRow("Income", rows.count { it.type == BillType.INCOME }.toString())
                        Divider()
                        ListRow("Transfers", rows.count { it.type == BillType.TRANSFER }.toString())
                    }
                    if (result.skipped.isNotEmpty()) {
                        Divider()
                        ListRow("Skipped", result.skipped.size.toString(), subtitle = result.skipped.take(3).joinToString("\n"))
                    }
                    if (noRate.isNotEmpty()) {
                        Divider()
                        ListRow("No exchange rate", noRate.joinToString(", "))
                    }
                }
                if (rows.isNotEmpty() && finished == null && noRate.isEmpty()) {
                    PrimaryButton(if (busy) "Importing..." else "Import ${rows.size} bills", Modifier.fillMaxWidth(), enabled = !busy) {
                        busy = true
                        scope.launch {
                            val added = repo.importRows(rows)
                            busy = false
                            withContext(Dispatchers.IO) { inbox.delete() }
                            finished = "Added $added bills. ${rows.size - added} were already here."
                        }
                    }
                }
            }
            finished?.let { Panel { Text(it, Modifier.padding(16.dp)) } }
            Panel {
                ListRow("Download general template") { template.launch("xrnwallet-template.csv") }
            }
        }
    }
}

@Composable
fun BackupScreen() {
    val repo = LocalRepo.current
    val sync = syncManager()
    val connected = sync.status.collectAsStateWithLifecycle().value.connected
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { repo.backup(it) } }
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    Page("Data synchronization") {
        ScrollBody {
            HomeServerSection()
            Panel {
                ListRow("Back up to a file") {
                    saver.launch("xrnwallet-backup-${LocalDate.now()}.json")
                }
                Divider()
                ListRow("Restore from a file") { opener.launch(arrayOf("*/*")) }
            }
        }
    }
    pendingRestore?.let { uri ->
        ConfirmDialog(
            "Restore this backup?",
            "Everything on this phone will be replaced by the backup." +
                if (connected) " The server's copy stays, and the next sync asks which of the two to keep." else "",
            confirm = "Restore",
            onDismiss = { pendingRestore = null },
        ) {
            pendingRestore = null
            scope.launch {
                val outcome = runCatching {
                    // Checked first, so a file refused leaves the sync as it was too, with no copy to choose.
                    val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { repo.readBackup(it) } }
                        ?: error("unreadable")
                    // Not sent up as an edit: the server's copy can hold other phones' newer changes.
                    sync.replaceHere { repo.restore(text) }
                }
                val failure = outcome.exceptionOrNull()
                Toast.makeText(
                    context,
                    when (failure) {
                        null -> "Backup restored"
                        is UnusableBackup -> "This backup can't be used: ${failure.message}. Nothing changed."
                        else -> "That file is not a valid backup. Nothing changed."
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}

@Composable
fun StartOfMonthScreen() {
    val settings = prefs()
    val repo = LocalRepo.current
    var dialog by remember { mutableStateOf<String?>(null) }
    val rules = settings.periodRules
    val today = LocalDate.now()
    val month = rules.monthOf(today)
    val range = rules.monthRange(month)
    val monthName = month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
    Page("Start Of Month") {
        ScrollBody {
            Panel {
                ListRow("Start of Month", settings.startDay.toString()) { dialog = "day" }
                Divider()
                ListRow("On weekends", rules.weekend.label) { dialog = "weekend" }
                Divider()
                ListRow(
                    "Belonging month",
                    rules.belonging.label,
                    "Today : ${Dates.short(today)}\n${Dates.short(range.start)} - ${Dates.short(range.endInclusive)}: $monthName",
                ) { dialog = "belonging" }
            }
        }
    }
    when (dialog) {
        "day" -> WheelDialog("Start of Month", (1..28).toList(), settings.startDay, { it.toString() }, { dialog = null }) { day ->
            repo.store.update { it.copy(startDay = day) }
            dialog = null
        }
        "weekend" -> WheelDialog("On weekends", WeekendRule.entries.toList(), rules.weekend, { it.label }, { dialog = null }) { rule ->
            repo.store.update { it.copy(weekendRule = rule.ordinal) }
            dialog = null
        }
        "belonging" -> WheelDialog("Belonging month", Belonging.entries.toList(), rules.belonging, { it.label }, { dialog = null }) { rule ->
            repo.store.update { it.copy(belonging = rule.ordinal) }
            dialog = null
        }
    }
}

@Composable
fun CarryoverScreen() {
    val settings = prefs()
    val repo = LocalRepo.current
    Page("Balance Carryover") {
        ScrollBody {
            Panel { SwitchRow("Balance Carryover", settings.carryover) { on -> repo.store.update { it.copy(carryover = on) } } }
        }
    }
}

@Composable
fun PreferencesScreen() {
    val settings = prefs()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    var dialog by remember { mutableStateOf<String?>(null) }
    fun update(change: (Settings) -> Settings) = repo.store.update(change)
    Page("Preference Settings") {
        ScrollBody {
            Panel {
                SwitchRow("Show base currency symbol", settings.showBaseSymbol) { on -> update { it.copy(showBaseSymbol = on) } }
                Divider()
                ListRow("Thousands Separator", SeparatorStyle.entries[settings.separators].label) { dialog = "separator" }
                Divider()
                ListRow("Decimal places", DecimalMode.entries[settings.decimals].label) { dialog = "decimals" }
                Divider()
                ListRow(
                    "First Day of Week",
                    DayOfWeek.of(settings.firstDayOfWeek).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                ) { dialog = "week" }
                Divider()
                ListRow("Income-Expense Card Type", "Style ${settings.cardStyle}") { nav.navigate("cardstyle") }
                Divider()
                SwitchRow("Enable note recommendations", settings.noteSuggestions) { on -> update { it.copy(noteSuggestions = on) } }
                Divider()
                SwitchRow("Show account on bill card", settings.showAccountOnCard) { on -> update { it.copy(showAccountOnCard = on) } }
                Divider()
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate("colors") }.padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Income-Expense Colors", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Arrow(f.expenseArrow, f.expenseArrowColor)
                    Text(f.money(6000), color = f.expenseColor)
                    Spacer(Modifier.width(8.dp))
                    Arrow(f.incomeArrow, f.incomeArrowColor)
                    Text(f.money(10000), color = f.incomeColor)
                }
                Divider()
                ListRow("Income-Expense Arrow Direction", if (settings.arrowsReversed) "Reverse" else "Default") { dialog = "arrow" }
                Divider()
                SwitchRow("Include future bills", settings.includeFuture) { on -> update { it.copy(includeFuture = on) } }
            }
        }
    }
    when (dialog) {
        "separator" -> OptionDialog("Thousands Separator", SeparatorStyle.entries.toList(), SeparatorStyle.entries[settings.separators], { it.label }, { dialog = null }) { style ->
            update { it.copy(separators = style.ordinal) }
            dialog = null
        }
        "decimals" -> OptionDialog("Decimal places", DecimalMode.entries.toList(), DecimalMode.entries[settings.decimals], { it.label }, { dialog = null }) { mode ->
            update { it.copy(decimals = mode.ordinal) }
            dialog = null
        }
        "week" -> OptionDialog(
            "First Day of Week",
            DayOfWeek.entries.toList(),
            DayOfWeek.of(settings.firstDayOfWeek),
            { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
            { dialog = null },
        ) { day ->
            update { it.copy(firstDayOfWeek = day.value) }
            dialog = null
        }
        "arrow" -> OptionDialog("Arrow Direction", listOf(false, true), settings.arrowsReversed, { if (it) "Reverse" else "Default" }, { dialog = null }) { reversed ->
            update { it.copy(arrowsReversed = reversed) }
            dialog = null
        }
    }
}

@Composable
fun CardStyleScreen() {
    val settings = prefs()
    val repo = LocalRepo.current
    Page("Income-expense Card Type") {
        ScrollBody {
            (1..4).forEach { style ->
                Row(
                    Modifier.fillMaxWidth().clickable { repo.store.update { it.copy(cardStyle = style) } }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Style $style", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (settings.cardStyle == style) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
                Box(Modifier.clickable { repo.store.update { it.copy(cardStyle = style) } }) {
                    SummaryCard(style, 143200, 286400, 143200, null)
                }
            }
        }
    }
}

private val SWATCHES: List<Long> = listOf(
    0xFFFFFFFF, Settings.DEFAULT_EXPENSE, Settings.DEFAULT_INCOME, 0xFFF4668F, 0xFF6C9BEF, 0xFFFFD21F,
    0xFF40E0C8, 0xFFD94CB5, 0xFFF2994A, 0xFF7ED332, 0xFFA3DCFF, 0xFF3344BB,
)

private class ColorSlot(val color: (Settings) -> Long, val set: (Settings, Long) -> Settings)

private val EXPENSE_ARROW = ColorSlot({ it.expenseArrowColor }) { s, c -> s.copy(expenseArrowColor = c) }
private val INCOME_ARROW = ColorSlot({ it.incomeArrowColor }) { s, c -> s.copy(incomeArrowColor = c) }
private val EXPENSE_AMOUNT = ColorSlot({ it.expenseColor }) { s, c -> s.copy(expenseColor = c) }
private val INCOME_AMOUNT = ColorSlot({ it.incomeColor }) { s, c -> s.copy(incomeColor = c) }

@Composable
fun ColorsScreen() {
    val settings = prefs()
    val f = fmt()
    val repo = LocalRepo.current
    var picking by remember { mutableStateOf<ColorSlot?>(null) }
    val now = remember { Dates.time(java.time.LocalTime.now()) }
    Page(
        "Income-expense Colors",
        actions = {
            IconButton({
                repo.store.update {
                    it.copy(
                        expenseColor = Settings.DEFAULT_EXPENSE,
                        incomeColor = Settings.DEFAULT_INCOME,
                        expenseArrowColor = Settings.DEFAULT_EXPENSE,
                        incomeArrowColor = Settings.DEFAULT_INCOME,
                    )
                }
            }) { Icon(Icons.Filled.Refresh, "Reset colors") }
        },
    ) {
        ScrollBody {
            Panel {
                ListRow("Expense Arrow", f.expenseArrow, valueColor = f.expenseArrowColor) { picking = EXPENSE_ARROW }
                Divider()
                ListRow("Income Arrow", f.incomeArrow, valueColor = f.incomeArrowColor) { picking = INCOME_ARROW }
                Divider()
                ListRow("Expense Amount", f.money(6000), valueColor = f.expenseColor) { picking = EXPENSE_AMOUNT }
                Divider()
                ListRow("Income Amount", f.money(10000), valueColor = f.incomeColor) { picking = INCOME_AMOUNT }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Preview", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Arrow(f.expenseArrow, f.expenseArrowColor)
                Text(" ${f.money(6000)}")
                Spacer(Modifier.width(8.dp))
                Arrow(f.incomeArrow, f.incomeArrowColor)
                Text(" ${f.money(10000)}")
            }
            Panel {
                PreviewBill("🥤", "Beverages", "-${f.money(6000)}", f.expenseColor, now)
                Divider()
                PreviewBill("👛", "Salary", "+${f.money(10000)}", f.incomeColor, now)
            }
        }
    }
    picking?.let { slot ->
        ColorDialog(slot.color(settings), { picking = null }) { color ->
            repo.store.update { slot.set(it, color) }
            picking = null
        }
    }
}

@Composable
private fun PreviewBill(icon: String, name: String, amount: String, color: Color, time: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Bubble(icon, 48.dp)
        Text(name, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, color = color, fontWeight = FontWeight.Medium)
            Muted(time)
        }
    }
}

@Composable
private fun ColorDialog(selected: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var choice by remember { mutableLongStateOf(selected) }
    DialogCard(onDismiss) {
        Text("Select color", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        SWATCHES.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { color ->
                    val fill = if (color == choice) MaterialTheme.colorScheme.surfaceContainerHigh else Color(color)
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(5.dp, Color(color), CircleShape)
                            .clickable { choice = color },
                    )
                }
            }
        }
        CancelConfirmRow(onDismiss) { onPick(choice) }
    }
}

@Composable
fun CurrenciesScreen() {
    val s = snap()
    val settings = prefs()
    val repo = LocalRepo.current
    val known by repo.allRates.collectAsState()
    val scope = rememberCoroutineScope()
    var add by remember { mutableStateOf(false) }
    var pickBase by remember { mutableStateOf(false) }
    var confirmBase by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CurrencyRate?>(null) }
    var creating by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Account balances are valued at today's rate, so taking one away would count each unit as one
    // of the base currency.
    fun remove(rate: CurrencyRate) {
        val inUse = s.accounts.any { it.currency == rate.code } || s.plans.any { it.currency == rate.code } ||
            s.recurring.any { it.currency == rate.code }
        if (inUse) {
            Toast.makeText(context, "${rate.code} is in use, so it cannot be removed", Toast.LENGTH_SHORT).show()
        } else {
            repo.save { delete(rate) }
        }
    }

    Page(
        "Currencies",
        actions = { IconButton({ add = true }) { Icon(Icons.Filled.AddBox, "Add currency") } },
    ) {
        ScrollBody {
            Panel {
                val base = Currencies.info(settings.baseCurrency)
                ListRow("Base currency", "${base.flag} ${base.code}") { pickBase = true }
            }
            SectionTitle("Billing currencies")
            if (s.rates.isEmpty()) Muted("None yet")
            ReorderGrid(
                items = s.rates,
                columns = 1,
                keyOf = { it.code },
                onMove = { order -> repo.save { putRates(order.mapIndexed { i, rate -> rate.copy(sort = i) }) } },
            ) { rate ->
                val info = Currencies.info(rate.code)
                val swipe = rememberSwipeToDismissBoxState(
                    confirmValueChange = { target ->
                        if (target == SwipeToDismissBoxValue.EndToStart) remove(rate)
                        false
                    },
                )
                SwipeToDismissBox(
                    swipe,
                    backgroundContent = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(vertical = 5.dp)
                                .clip(CardShape)
                                .background(Color(0xFFFF3B30))
                                .padding(end = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { Icon(Icons.Filled.Delete, "Remove", tint = Color.White) }
                    },
                    enableDismissFromStartToEnd = false,
                ) {
                Panel(Modifier.padding(vertical = 5.dp).clickable { editing = rate }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(info.flag, fontSize = 24.sp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("${rate.code} ${info.symbol}", style = MaterialTheme.typography.bodyLarge)
                            Muted(info.name, maxLines = 1)
                        }
                        Muted("${rate.rate.toBigDecimal().round(java.math.MathContext(6)).stripTrailingZeros().toPlainString()} ${settings.baseCurrency}")
                    }
                }
                }
            }
        }
    }
    if (add) {
        val taken = s.rates.map { it.code }.toSet() + settings.baseCurrency
        OptionDialog(
            "Add currency",
            Currencies.all.filter { it.code !in taken },
            null,
            { "${it.flag}  ${it.code}  ${it.name}" },
            { add = false },
        ) {
            add = false
            creating = it.code
        }
    }
    creating?.let { code ->
        InputDialog(
            "1 $code in ${settings.baseCurrency}",
            known[code]?.toString().orEmpty(),
            numeric = true,
            onDismiss = { creating = null },
        ) { text ->
            val rate = text.replace(',', '.').toDoubleOrNull()
            if (rate != null && rate > 0) repo.save { put(CurrencyRate(code, rate, s.rates.size)) }
            creating = null
        }
    }
    editing?.let { rate ->
        var text by remember(rate) { mutableStateOf(rate.rate.toBigDecimal().toPlainString()) }
        DialogCard({ editing = null }) {
            Text("1 ${rate.code} in ${settings.baseCurrency}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            WalletField(text, { text = it }, Modifier.fillMaxWidth(), numeric = true)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("Remove", Modifier.weight(1f)) {
                    remove(rate)
                    editing = null
                }
                PrimaryButton("Save", Modifier.weight(1f)) {
                    val value = text.replace(',', '.').toDoubleOrNull()
                    if (value != null && value > 0) repo.save { put(rate.copy(rate = value)) }
                    editing = null
                }
            }
        }
    }
    if (pickBase) {
        OptionDialog(
            "Base currency",
            Currencies.all.map { it.code },
            settings.baseCurrency,
            { "${Currencies.info(it).flag}  $it  ${Currencies.info(it).name}" },
            { pickBase = false },
        ) {
            pickBase = false
            if (it != settings.baseCurrency) confirmBase = it
        }
    }
    confirmBase?.let { code ->
        val hasRate = code in s.rates.map { it.code }
        ConfirmDialog(
            "Change base currency to $code?",
            if (hasRate) "Every total and budget will be shown in $code, using today's rate between the two." else "Add $code as a billing currency with a rate first, so totals can be converted.",
            confirm = if (hasRate) "Change" else "OK",
            danger = false,
            onDismiss = { confirmBase = null },
        ) {
            confirmBase = null
            if (hasRate) scope.launch { repo.changeBase(code) }
        }
    }
}

@Composable
fun HapticsScreen() {
    val settings = prefs()
    val repo = LocalRepo.current
    Page("Haptic feedback") {
        ScrollBody {
            Panel { SwitchRow("Haptic feedback", settings.hapticsOn) { on -> repo.store.update { it.copy(hapticsOn = on) } } }
            if (settings.hapticsOn) {
                Panel {
                    listOf("Light", "Medium", "Strong").forEachIndexed { index, label ->
                        if (index > 0) Divider()
                        Row(
                            Modifier.fillMaxWidth().clickable { repo.store.update { it.copy(hapticStrength = index) } }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (settings.hapticStrength == index) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
