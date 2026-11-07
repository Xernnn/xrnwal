package com.xern.xrnwallet.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.xern.xrnwallet.data.ALL_LEDGERS
import com.xern.xrnwallet.data.Account
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Budget
import com.xern.xrnwallet.data.Category
import com.xern.xrnwallet.data.Recurring
import com.xern.xrnwallet.data.SavingsDeposit
import com.xern.xrnwallet.data.SavingsPlan
import com.xern.xrnwallet.data.Seed
import com.xern.xrnwallet.data.Stats
import com.xern.xrnwallet.domain.Calculator
import com.xern.xrnwallet.domain.Currencies
import com.xern.xrnwallet.domain.Dates
import com.xern.xrnwallet.domain.Money
import com.xern.xrnwallet.domain.Period
import com.xern.xrnwallet.domain.RepeatKind
import com.xern.xrnwallet.domain.RepeatRule
import com.xern.xrnwallet.domain.Savings
import com.xern.xrnwallet.domain.SavingsType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun BudgetScreen() {
    val s = snap()
    val settings = prefs()
    val f = fmt()
    val repo = LocalRepo.current
    val today = LocalDate.now()
    val rules = settings.periodRules
    var month by remember { mutableStateOf(rules.monthOf(today)) }
    var pick by remember { mutableStateOf(false) }
    var editBudget by remember { mutableStateOf<Budget?>(null) }
    var editOverall by remember { mutableStateOf(false) }
    var addCategory by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf<Category?>(null) }

    val overall = s.budgets.firstOrNull { it.categoryId == null }
    val status = Stats.budget(s, month, rules, today, overall?.amountCents ?: 0, settings.includeFuture)
    val categoryBudgets = s.budgets.filter { it.categoryId != null }

    Page(
        "Budget",
        actions = {
            Switch(
                settings.budgetEnabled,
                { on -> repo.store.update { it.copy(budgetEnabled = on) } },
                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = Color.Black),
            )
        },
    ) {
        ScrollBody {
            if (!settings.budgetEnabled) {
                Panel { Muted("Budget is off. Turn it on to see it on the home page.", Modifier.padding(16.dp)) }
            } else {
                Panel {
                    Column(Modifier.padding(16.dp)) {
                        PeriodSwitcher(
                            Period.Month(month).label(rules, today),
                            onPrevious = { month = month.minusMonths(1) },
                            onNext = { month = month.plusMonths(1) },
                            onPick = { pick = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ring(status.remainingPercent / 100f, size = 104.dp) { Text("${status.remainingPercent}%") }
                            Column(Modifier.padding(start = 20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                BudgetLine("Budget", f.plain(status.budgetCents)) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        "Change budget",
                                        Modifier.padding(start = 4.dp).size(18.dp).clickable { editOverall = true },
                                    )
                                }
                                RemainingLine(status.remainingCents)
                                BudgetLine("Daily avg", f.plain(status.dailyAverageCents))
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Category budget", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton({ addCategory = true }) { Icon(Icons.Filled.AddBox, "Add category budget") }
                }
                categoryBudgets.forEach { budget ->
                    val category = s.categoryById[budget.categoryId]
                    val line = Stats.budget(s, month, rules, today, budget.amountCents, settings.includeFuture, budget.categoryId)
                    Panel(Modifier.clickable { editBudget = budget }) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(category?.name ?: "Removed category", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Ring(line.remainingPercent / 100f, size = 64.dp, stroke = 6.dp) {
                                        Text(category?.icon ?: "⭐", fontSize = 24.sp)
                                    }
                                    Text("${line.remainingPercent}%", Modifier.padding(top = 4.dp))
                                }
                                Column(Modifier.padding(start = 24.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BudgetLine("Budget", f.plain(line.budgetCents))
                                    BudgetLine("Expenses", f.plain(line.spentCents))
                                    RemainingLine(line.remainingCents)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (pick) {
        PeriodDialog(Period.Month(month), monthOnly = true, onDismiss = { pick = false }) {
            month = (it as Period.Month).month
            pick = false
        }
    }
    if (editOverall) {
        InputDialog(
            "Monthly budget",
            overall?.let { Calculator.format(Money.fromCents(it.amountCents)) }.orEmpty(),
            numeric = true,
            onDismiss = { editOverall = false },
        ) { text ->
            val cents = Money.parse(text) ?: 0
            repo.save { put(overall?.copy(amountCents = cents) ?: Budget(amountCents = cents)) }
            editOverall = false
        }
    }
    if (addCategory) {
        val taken = categoryBudgets.mapNotNull { it.categoryId }.toSet()
        OptionDialog(
            "Category",
            s.categories.filter { it.type == BillType.EXPENSE && it.parentId == null && it.active && it.id !in taken },
            null,
            { "${it.icon}  ${it.name}" },
            { addCategory = false },
        ) {
            addCategory = false
            newCategory = it
        }
    }
    newCategory?.let { category ->
        InputDialog("${category.name} budget", numeric = true, onDismiss = { newCategory = null }) { text ->
            val cents = Money.parse(text) ?: 0
            if (cents > 0) repo.save { put(Budget(categoryId = category.id, amountCents = cents)) }
            newCategory = null
        }
    }
    editBudget?.let { budget ->
        var text by remember(budget) { mutableStateOf(Calculator.format(Money.fromCents(budget.amountCents))) }
        DialogCard({ editBudget = null }) {
            Text(s.categoryById[budget.categoryId]?.name ?: "Category", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            WalletField(text, { text = it }, Modifier.fillMaxWidth(), numeric = true)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("Delete", Modifier.weight(1f)) {
                    repo.save { delete(budget) }
                    editBudget = null
                }
                PrimaryButton("Save", Modifier.weight(1f)) {
                    val cents = Money.parse(text) ?: budget.amountCents
                    repo.save { put(budget.copy(amountCents = cents)) }
                    editBudget = null
                }
            }
        }
    }
}

@Composable
fun SavingsScreen() {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    Page("Savings Plan") {
        ScrollBody {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple(SavingsType.FIXED, "👛", "Fixed"),
                    Triple(SavingsType.INCREMENT, "📈", "Increment"),
                    Triple(SavingsType.FLEXIBLE, "💰", "Flexible"),
                ).forEach { (type, icon, label) ->
                    Panel(Modifier.weight(1f).clickable { nav.navigate("savings/new/${type.name}") }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Glyph(icon, 36.dp)
                            Text(label, Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            SectionTitle("My Plans")
            if (s.plans.isEmpty()) EmptyNote("No plans yet")
            s.plans.forEach { plan ->
                val saved = s.deposits.filter { it.planId == plan.id }.sumOf { it.amountCents }
                Panel(Modifier.clickable { nav.navigate("savings/plan/${plan.id}") }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Bubble(plan.icon, 48.dp)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.bodyLarge)
                            Muted(plan.planType.label)
                        }
                        Text("${f.money(saved, plan.currency)} / ${f.money(plan.targetCents, plan.currency)}")
                    }
                }
            }
        }
    }
}

@Composable
fun SavingsEditScreen(typeName: String) {
    val type = SavingsType.valueOf(typeName)
    val s = snap()
    val settings = prefs()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🐷") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(settings.baseCurrency) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var kind by remember { mutableStateOf(RepeatKind.WEEKLY) }
    var repetitions by remember { mutableIntStateOf(52) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf<String?>(null) }
    val cents = Money.parse(amount.ifBlank { "0" }) ?: 0
    val rule = RepeatRule(
        kind,
        when (kind) {
            RepeatKind.WEEKLY -> start.dayOfWeek.value
            RepeatKind.MONTHLY -> start.dayOfMonth
            else -> 1
        },
    )
    val target = when (type) {
        SavingsType.INCREMENT -> Savings.incrementTarget(cents, repetitions)
        else -> cents
    }

    Page("${type.label} plan") {
        ScrollBody {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable { dialog = "icon" }) { Bubble(icon) }
                WalletField(name, { name = it }, Modifier.padding(start = 12.dp).weight(1f), "Plan name")
            }
            AmountField(if (type == SavingsType.INCREMENT) "Starting amount" else "Target amount", amount) { amount = it }
            Panel {
                ListRow("Currency", currency) { dialog = "currency" }
                if (type != SavingsType.FLEXIBLE) {
                    Divider()
                    ListRow("Start date", Dates.iso(start)) { dialog = "start" }
                    Divider()
                    ListRow("Period", kind.label) { dialog = "period" }
                    Divider()
                    ListRow("Repetitions", repetitions.toString()) { dialog = "reps" }
                    Divider()
                    ListRow("End date", Dates.iso(rule.occurrence(start, repetitions - 1)))
                    Divider()
                    if (type == SavingsType.FIXED) {
                        ListRow("Amount per repetition", f.money(Savings.fixedDeposit(cents, repetitions), currency))
                    } else {
                        ListRow("Target amount", f.money(target, currency))
                    }
                }
                Divider()
                ListRow("Receive account", s.accountById[accountId]?.name?.trim() ?: "None") { dialog = "account" }
            }
            WalletField(note, { note = it }, Modifier.fillMaxWidth(), "Notes", singleLine = false)
            PrimaryButton("Save", Modifier.fillMaxWidth()) {
                if (name.isBlank() || cents <= 0) {
                    Toast.makeText(context, "Please enter a name and an amount", Toast.LENGTH_SHORT).show()
                    return@PrimaryButton
                }
                repo.save {
                    put(
                        SavingsPlan(
                            type = type.name,
                            name = name.trim(),
                            icon = icon,
                            targetCents = target,
                            startCents = cents,
                            currency = currency,
                            startEpochDay = start.toEpochDay(),
                            repeatKind = rule.kind.name,
                            repeatValue = rule.value,
                            repetitions = repetitions,
                            accountId = accountId,
                            note = note.trim(),
                        ),
                    )
                }
                nav.leavePage()
            }
        }
    }
    when (dialog) {
        "icon" -> IconDialog(icon, onDismiss = { dialog = null }) {
            icon = it
            dialog = null
        }
        "currency" -> CurrencyDialog({ dialog = null }) {
            currency = it
            dialog = null
        }
        "start" -> DateDialog("Start date", start, onDismiss = { dialog = null }) {
            start = it
            dialog = null
        }
        "period" -> OptionDialog(
            "Period",
            listOf(RepeatKind.DAILY, RepeatKind.WEEKLY, RepeatKind.MONTHLY, RepeatKind.YEARLY),
            kind,
            { it.label },
            { dialog = null },
        ) {
            kind = it
            dialog = null
        }
        "reps" -> InputDialog("Repetitions", repetitions.toString(), numeric = true, onDismiss = { dialog = null }) {
            repetitions = it.toIntOrNull()?.coerceIn(1, 520) ?: repetitions
            dialog = null
        }
        "account" -> AccountDialog(s.accounts.filter { !it.archived }, allowNone = true, onDismiss = { dialog = null }) {
            accountId = it
            dialog = null
        }
    }
}

@Composable
fun IconDialog(selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    DialogCard(onDismiss) {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            IconPicker(selected, onPick)
        }
    }
}

@Composable
fun SavingsDetailScreen(id: Long) {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val plan = s.plans.firstOrNull { it.id == id }
    var confirm by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    if (plan == null) {
        Page("Savings Plan") { EmptyNote("This plan no longer exists") }
        return
    }
    val deposits = s.deposits.filter { it.planId == id }
    val saved = deposits.sumOf { it.amountCents }
    val fraction = if (plan.targetCents > 0) saved.toFloat() / plan.targetCents else 0f

    fun amountAt(index: Int) = when (plan.planType) {
        SavingsType.INCREMENT -> Savings.incrementDeposit(plan.startCents, index)
        else -> Savings.fixedDeposit(plan.targetCents, plan.repetitions)
    }

    Page(
        plan.name,
        actions = { IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "Delete plan", tint = Color(0xFFFF3B30)) } },
    ) {
        ScrollBody {
            Panel {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ring(fraction, size = 96.dp) { Text("${(fraction * 100).toInt().coerceAtMost(100)}%") }
                    Column(Modifier.padding(start = 20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BudgetLine("Target", f.money(plan.targetCents, plan.currency))
                        BudgetLine("Saved", f.money(saved, plan.currency))
                        BudgetLine("Left", f.money((plan.targetCents - saved).coerceAtLeast(0), plan.currency))
                    }
                }
            }
            if (plan.note.isNotEmpty()) Muted(plan.note)
            if (plan.planType == SavingsType.FLEXIBLE) {
                PrimaryButton("Add deposit", Modifier.fillMaxWidth()) { add = true }
                deposits.forEach { deposit ->
                    Panel {
                        ListRow(
                            Dates.full(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(deposit.atMillis), java.time.ZoneId.systemDefault())),
                            f.money(deposit.amountCents, plan.currency),
                            chevron = false,
                        )
                    }
                }
            } else {
                SectionTitle("Schedule")
                Panel {
                    plan.rule.occurrences(plan.start, plan.repetitions).forEachIndexed { index, date ->
                        val done = index < deposits.size
                        if (index > 0) Divider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = index == deposits.size) {
                                    repo.save {
                                        put(SavingsDeposit(planId = plan.id, amountCents = amountAt(index), atMillis = System.currentTimeMillis()))
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", Modifier.width(36.dp))
                            Text(Dates.iso(date), Modifier.weight(1f))
                            Text(f.money(amountAt(index), plan.currency))
                            Icon(
                                Icons.Filled.Check,
                                if (done) "Deposited" else null,
                                Modifier.padding(start = 8.dp).size(20.dp),
                                tint = if (done) MaterialTheme.colorScheme.primary else Color.Transparent,
                            )
                        }
                    }
                }
            }
        }
    }
    if (add) {
        InputDialog("Deposit", numeric = true, onDismiss = { add = false }) { text ->
            val cents = Money.parse(text) ?: 0
            if (cents > 0) repo.save { put(SavingsDeposit(planId = plan.id, amountCents = cents, atMillis = System.currentTimeMillis())) }
            add = false
        }
    }
    if (confirm) {
        val note = if (plan.accountId != null) "Its deposits go with it and leave the receive account's balance." else "Its deposit history goes with it."
        ConfirmDialog("Delete ${plan.name}?", note, onDismiss = { confirm = false }) {
            confirm = false
            repo.deletePlan(plan)
            nav.leavePage()
        }
    }
}

/** One side of a transfer: the account's icon above its name. */
@Composable
private fun TransferEnd(title: String, account: Account?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        if (account != null) AccountBubble(account.name, account.icon, account.color, 56.dp) else Bubble("＋", 56.dp)
        Text(account?.name?.trim() ?: title, Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun RecurringCard(rule: Recurring, onClick: () -> Unit) {
    val s = snap()
    val f = fmt()
    val sign = if (rule.type == BillType.EXPENSE) -1 else 1
    fun totalFor(count: Int): String {
        val cents = rule.amountCents * count * sign
        return if (rule.type == BillType.TRANSFER) f.money(cents, rule.currency) else f.signed(cents, rule.currency).removePrefix("+")
    }
    val title = if (rule.type == BillType.TRANSFER) {
        "${s.accountById[rule.accountId]?.name?.trim() ?: "?"} » ${s.accountById[rule.toAccountId]?.name?.trim() ?: "?"}"
    } else {
        s.categoryLabel(rule.categoryId)
    }
    Panel(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Bubble(if (rule.type == BillType.TRANSFER) "🪙" else s.categoryById[rule.categoryId]?.icon ?: "⭐", 56.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (rule.note.isNotEmpty()) Muted(rule.note)
            }
            Text("${totalFor(1)}/${rule.rule.shortLabel}", style = MaterialTheme.typography.bodyLarge)
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoLine("Start time", Dates.iso(rule.start))
            InfoLine("Repeat", rule.rule.longLabel)
            InfoLine("Recorded", "${rule.generated} bills, ${totalFor(rule.generated)}")
            InfoLine("Remaining", rule.remaining?.let { "$it bills, ${totalFor(it)}" } ?: "No end date")
            InfoLine("Account", s.accountById[rule.accountId]?.name?.trim() ?: "None")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f))
        Text(value)
    }
}

@Composable
fun RecurringScreen() {
    val s = snap()
    val nav = LocalNav.current
    Page(
        "Recurring Transactions",
        actions = { IconButton({ nav.navigate("recurring/edit") }) { Icon(Icons.Filled.AddBox, "Add recurring item") } },
    ) {
        ScrollBody {
            if (s.recurring.isEmpty()) EmptyNote("Nothing recurring yet")
            s.recurring.forEach { rule -> RecurringCard(rule) { nav.navigate("recurring/view/${rule.id}") } }
        }
    }
}

@Composable
fun RecurringDetailScreen(id: Long) {
    val s = snap()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val rule = s.recurring.firstOrNull { it.id == id }
    var confirm by remember { mutableStateOf(false) }
    if (rule == null) {
        Page("Recurring Transactions") { EmptyNote("This item no longer exists") }
        return
    }
    val bills = s.bills.filter { it.recurringId == id }
    Page(
        "Recurring",
        actions = {
            IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF3B30)) }
            IconButton({ nav.navigate("recurring/edit?id=$id") }) { Icon(Icons.Filled.Edit, "Edit") }
        },
    ) {
        ScrollBody {
            RecurringCard(rule) {}
            BillDays(bills, s) { nav.openBill(it.id) }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "Delete this recurring item?",
            "Bills it already recorded stay. No new ones will be added.",
            onDismiss = { confirm = false },
        ) {
            confirm = false
            repo.save { delete(rule) }
            nav.leavePage()
        }
    }
}

@Composable
fun RecurringEditScreen(id: Long) {
    val s = snap()
    val settings = prefs()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember { s.recurring.firstOrNull { it.id == id } }
    var tab by remember { mutableIntStateOf(if (existing?.type == BillType.TRANSFER) 1 else 0) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.let { Calculator.format(Money.fromCents(it.amountCents)) }.orEmpty()) }
    var currency by remember { mutableStateOf(existing?.currency ?: settings.baseCurrency) }
    var start by remember { mutableStateOf(existing?.start ?: LocalDate.now()) }
    var kind by remember { mutableStateOf(existing?.rule?.kind ?: RepeatKind.MONTHLY) }
    var value by remember { mutableIntStateOf(existing?.repeatValue ?: LocalDate.now().dayOfMonth) }
    var every by remember { mutableIntStateOf(existing?.every ?: 1) }
    var inBudget by remember { mutableStateOf(existing?.inBudget ?: true) }
    var tagIds by remember { mutableStateOf(existing?.tags.orEmpty()) }
    var minute by remember { mutableIntStateOf(existing?.minuteOfDay ?: 480) }
    var times by remember { mutableIntStateOf(existing?.times ?: 12) }
    var accountId by remember { mutableStateOf(existing?.accountId) }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId) }
    var ledgerId by remember {
        mutableLongStateOf(existing?.ledgerId ?: s.activeLedger(settings).takeIf { it != ALL_LEDGERS } ?: s.ledgers.firstOrNull()?.id ?: 0)
    }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pendingPast by remember { mutableStateOf<Recurring?>(null) }

    fun candidate(): Recurring? {
        val cents = Money.parse(amount) ?: 0
        val problem = when {
            cents <= 0 -> "Please enter amount"
            tab == 0 && categoryId == null -> "Please select a category"
            tab == 1 && (accountId == null || toAccountId == null) -> "Please select an account"
            else -> null
        }
        if (problem != null) {
            Toast.makeText(context, problem, Toast.LENGTH_SHORT).show()
            return null
        }
        return Recurring(
            id = existing?.id ?: 0,
            ledgerId = ledgerId,
            type = if (tab == 1) BillType.TRANSFER else s.categoryById[categoryId]?.type ?: BillType.EXPENSE,
            categoryId = if (tab == 1) null else categoryId,
            accountId = accountId,
            toAccountId = if (tab == 1) toAccountId else null,
            amountCents = cents,
            currency = currency,
            note = note.trim(),
            startEpochDay = start.toEpochDay(),
            repeatKind = kind.name,
            repeatValue = value,
            minuteOfDay = minute,
            times = times,
            generated = existing?.generated ?: 0,
            every = every,
            inBudget = inBudget,
            tagIds = tagIds.joinToString(","),
        )
    }

    /** A rule whose dates moved has to be looked at from the start. Otherwise from where it got to. */
    fun firstOpenStep(rule: Recurring): Int =
        if (existing == null || existing.start != rule.start || existing.rule != rule.rule) 0 else existing.generated

    // The page closes only after the write, so a second tap can arrive first and add the rule twice.
    var saving by remember { mutableStateOf(false) }

    fun commit(rule: Recurring, recorded: List<Bill> = emptyList(), missing: List<LocalDate> = emptyList()) {
        if (saving) return
        saving = true
        scope.launch {
            val id = repo.dao.put(rule)
            if (recorded.isNotEmpty()) repo.dao.linkBills(id, recorded.map { it.id })
            if (missing.isNotEmpty()) repo.recordDates(rule.copy(id = id), missing)
            repo.runRecurring()
            nav.leavePage()
        }
    }

    Page(
        "Recurring Transactions",
        actions = {
            IconButton({
                val rule = candidate() ?: return@IconButton
                val first = firstOpenStep(rule)
                if (pastCount(rule) > first) pendingPast = rule else commit(rule.copy(generated = first))
            }) { Icon(Icons.Filled.Check, "Save") }
        },
    ) {
        UnderlineTabs(listOf("Income/Expenses", "Transfer"), tab, onSelect = { tab = it })
        ScrollBody {
            Panel {
                if (tab == 0) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val category = s.categoryById[categoryId]
                        Box(Modifier.clickable { dialog = "category" }) { Bubble(category?.icon ?: "＋") }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(category?.name ?: "Category", Modifier.clickable { dialog = "category" }, style = MaterialTheme.typography.bodyLarge)
                            WalletField(note, { note = it }, Modifier.fillMaxWidth().padding(top = 6.dp), "Add a note")
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TransferEnd("Payment account", s.accountById[accountId], Modifier.weight(1f)) { dialog = "from" }
                        IconButton({
                            val payment = accountId
                            accountId = toAccountId
                            toAccountId = payment
                        }) { Icon(Icons.Filled.SwapHoriz, "Swap accounts") }
                        TransferEnd("Receive account", s.accountById[toAccountId], Modifier.weight(1f)) { dialog = "to" }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notes", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        WalletField(note, { note = it }, Modifier.weight(1f))
                    }
                }
                Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    AmountField("Amount per repetition", amount) { amount = it }
                }
                ListRow("Currency", "${Currencies.symbol(currency)} ($currency)") { dialog = "currency" }
                ListRow("Start date", Dates.iso(start)) { dialog = "start" }
                ListRow("Repeat", RepeatRule(kind, value, every).shortLabel) { dialog = "repeat" }
                when (kind) {
                    RepeatKind.MONTHLY -> NumberGrid((1..31).toList(), 7, value) { value = it }
                    RepeatKind.WEEKLY -> NumberGrid((1..7).toList(), 7, value, { d ->
                        DayOfWeek.of(d).getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
                    }) { value = it }
                    else -> Unit
                }
                ListRow("Time", Dates.time(LocalTime.of(minute / 60, minute % 60))) { dialog = "time" }
                ListRow("Repeat times", if (times == 0) "Forever" else "$times Times") { dialog = "times" }
                if (tab == 0) ListRow("Accounts", s.accountById[accountId]?.name?.trim() ?: " ") { dialog = "account" }
                ListRow("Ledger", s.ledgerById[ledgerId]?.name ?: " ") { dialog = "ledger" }
                if (tab == 0) SwitchRow("Included in budget", inBudget) { inBudget = it }
                ListRow("Tags", tagIds.mapNotNull { id -> s.tags.firstOrNull { it.id == id }?.name }.joinToString(", ")) { dialog = "tags" }
            }
        }
    }
    pendingPast?.let { rule ->
        val count = pastCount(rule)
        val dates = rule.dates(firstOpenStep(rule), count)
        val others = s.recurring.filter { it.id != rule.id }.mapTo(HashSet()) { it.id }
        val recorded = rule.existingBills(s.bills, dates, others)
        val missing = dates.filterNot { it in recorded }
        fun finish(addMissing: Boolean) {
            pendingPast = null
            commit(rule.copy(generated = count), recorded.values.toList(), if (addMissing) missing else emptyList())
        }
        // Past dates that already have a bill are never recorded again, whichever button is pressed.
        when {
            missing.isEmpty() -> ConfirmDialog(
                if (dates.size == 1) "1 past date" else "${dates.size} past dates",
                if (dates.size == 1) {
                    "That bill is already in your records, so it will be linked to this rule. Nothing new is added."
                } else {
                    "All ${dates.size} bills are already in your records, so they will be linked to this rule. Nothing new is added."
                },
                confirm = "Link",
                danger = false,
                onDismiss = { pendingPast = null },
            ) { finish(addMissing = false) }
            recorded.isEmpty() -> ConfirmDialog(
                if (dates.size == 1) "1 past date" else "${dates.size} past dates",
                "This starts in the past. Record ${if (dates.size == 1) "that bill" else "those ${dates.size} bills"} now? " +
                    "Choose Skip if they are already in your records, so nothing is counted twice.",
                confirm = "Record",
                danger = false,
                dismiss = "Skip",
                onOutside = { pendingPast = null },
                onDismiss = { finish(addMissing = false) },
            ) { finish(addMissing = true) }
            else -> ConfirmDialog(
                "${dates.size} past dates",
                "${recorded.size} of these ${if (recorded.size == 1) "is" else "are"} already in your records, so " +
                    "${if (recorded.size == 1) "it" else "they"} will be linked to this rule. " +
                    "Add ${if (missing.size == 1) "the other one" else "the other ${missing.size}"} too?",
                confirm = "Add ${missing.size}",
                danger = false,
                dismiss = "Link only",
                onOutside = { pendingPast = null },
                onDismiss = { finish(addMissing = false) },
            ) { finish(addMissing = true) }
        }
    }
    when (dialog) {
        "category" -> CategoryDialog({ dialog = null }) {
            categoryId = it
            dialog = null
        }
        "currency" -> CurrencyDialog({ dialog = null }) {
            currency = it
            dialog = null
        }
        "start" -> DateDialog("Start date", start, onDismiss = { dialog = null }) {
            start = it
            if (kind == RepeatKind.MONTHLY) value = it.dayOfMonth
            if (kind == RepeatKind.WEEKLY) value = it.dayOfWeek.value
            dialog = null
        }
        "repeat" -> RepeatDialog(RepeatRule(kind, value, every), { dialog = null }) { count, unit ->
            val rule = RepeatRule.ofWheel(count, unit, start)
            kind = rule.kind
            value = rule.value
            every = rule.every
            dialog = null
        }
        "tags" -> TagDialog(s.tags, tagIds, onDismiss = { dialog = null }) {
            tagIds = it
            dialog = null
        }
        "time" -> TimeDialog(LocalTime.of(minute / 60, minute % 60), { dialog = null }) {
            minute = it.hour * 60 + it.minute
            dialog = null
        }
        // The field starts empty so that typing replaces the number, where editing it could leave 012.
        "times" -> InputDialog(
            "Repeat times",
            placeholder = if (times == 0) "Forever" else times.toString(),
            numeric = true,
            below = {
                TextButton(
                    {
                        times = 0
                        dialog = null
                    },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text("Repeat forever") }
            },
            onDismiss = { dialog = null },
        ) {
            times = it.toIntOrNull()?.coerceIn(0, 9999) ?: times
            dialog = null
        }
        "account", "from" -> AccountDialog(s.accounts.filter { !it.archived }, allowNone = dialog == "account", onDismiss = { dialog = null }) {
            accountId = it
            dialog = null
        }
        "to" -> AccountDialog(s.accounts.filter { !it.archived }, allowNone = false, onDismiss = { dialog = null }) {
            toAccountId = it
            dialog = null
        }
        "ledger" -> LedgerDialog(s.ledgers, ledgerId, allowAll = false, onDismiss = { dialog = null }) {
            ledgerId = it
            dialog = null
        }
    }
}

/** How many of a rule's dates fall before now. */
private fun pastCount(rule: Recurring): Int {
    val now = LocalDateTime.now()
    var count = 0
    while (rule.times == 0 || count < rule.times) {
        val at = rule.rule.occurrence(rule.start, count).atTime(rule.minuteOfDay / 60, rule.minuteOfDay % 60)
        if (at.isAfter(now)) break
        count++
    }
    return count
}

@Composable
private fun NumberGrid(values: List<Int>, columns: Int, selected: Int, label: (Int) -> String = { it.toString() }, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        values.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { value ->
                    Box(Modifier.weight(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (value == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPick(value) },
                            contentAlignment = Alignment.Center,
                        ) { Text(label(value), color = if (value == selected) Color.Black else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Every in-use category, expense or income, grouped under tabs. */
@Composable
fun CategoryDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val s = snap()
    var tab by remember { mutableIntStateOf(0) }
    DialogCard(onDismiss) {
        UnderlineTabs(listOf("Expenses", "Income"), tab, onSelect = { tab = it })
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            s.categories.filter { it.type == tab && it.active }.forEach { category ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(category.id) }
                        .padding(vertical = 8.dp, horizontal = if (category.parentId == null) 8.dp else 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(category.icon, 24.dp)
                    Text(category.name, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}
