package com.xern.xrnwallet.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xern.xrnwallet.data.Account
import com.xern.xrnwallet.data.AccountKind
import com.xern.xrnwallet.data.Seed
import com.xern.xrnwallet.domain.Calculator
import com.xern.xrnwallet.domain.Currencies
import com.xern.xrnwallet.domain.Money

@Composable
fun AccountsScreen() {
    val s = snap()
    val settings = prefs()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val hide = settings.hideAmounts
    val summary = remember(s) { s.netAssets() }
    fun shown(text: String) = if (hide) "****" else text

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp)) {
            IconButton({ repo.store.update { it.copy(hideAmounts = !it.hideAmounts) } }, Modifier.align(Alignment.CenterStart)) {
                Icon(if (hide) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (hide) "Show amounts" else "Hide amounts")
            }
            Text("Accounts", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleLarge)
            IconButton({ nav.navigate("account/edit") }, Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Filled.AddBox, "New account")
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel {
                Column(Modifier.padding(20.dp)) {
                    Muted(if (hide) "Prosperous" else "Net Assets")
                    Text(shown(f.money(summary.net)), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(top = 14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Muted("Assets")
                            Text(shown(f.money(summary.assets)), style = MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            Muted("Debts")
                            Text(shown(f.money(summary.debts)), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            AccountGroup("Savings Account", s.accounts.filter { !it.archived && it.kind == AccountKind.SAVINGS }, hide)
            AccountGroup("Credit Account", s.accounts.filter { !it.archived && it.kind == AccountKind.CREDIT }, hide)
            AccountGroup("Archived", s.accounts.filter { it.archived }, hide)
        }
    }
}

@Composable
private fun AccountGroup(title: String, accounts: List<Account>, hide: Boolean) {
    if (accounts.isEmpty()) return
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    SectionTitle(title)
    ReorderGrid(
        items = accounts,
        columns = 1,
        keyOf = { it.id },
        onMove = { order -> repo.save { putAccounts(order.mapIndexed { i, account -> account.copy(sort = i) }) } },
    ) { account ->
        Panel(Modifier.padding(vertical = 5.dp).clickable { nav.navigate("account/${account.id}") }) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                AccountBubble(account.name, account.icon, account.color, 48.dp)
                Text(
                    account.name.trim(),
                    Modifier.padding(start = 12.dp).weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (hide) "****" else f.money(s.balance(account), account.currency), style = MaterialTheme.typography.bodyLarge)
                    if (account.currency != f.base) {
                        Muted(if (hide) "****" else f.money(s.balanceInBase(account)))
                    }
                }
            }
        }
    }
}

@Composable
fun AccountDetailScreen(id: Long) {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    val account = s.accountById[id]
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var corrected by remember { mutableStateOf(false) }
    if (account == null) {
        Page("Account") { EmptyNote("This account no longer exists") }
        return
    }
    val history = remember(s, id) { s.historyOf(id) }

    Page(
        account.name.trim(),
        actions = {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Edit") }, {
                        menu = false
                        nav.navigate("account/edit?id=$id")
                    })
                    DropdownMenuItem({ Text(if (account.archived) "Unarchive" else "Archive") }, {
                        menu = false
                        repo.save { put(account.copy(archived = !account.archived)) }
                    })
                    DropdownMenuItem({ Text("Balance Correction") }, {
                        menu = false
                        corrected = true
                    })
                    DropdownMenuItem({ Text("Delete", color = Color(0xFFFF3B30)) }, {
                        menu = false
                        confirm = true
                    })
                }
            }
        },
    ) {
        ScrollBody {
            Panel {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AccountBubble(account.name, account.icon, account.color, 56.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(account.name.trim(), style = MaterialTheme.typography.titleMedium)
                        if (account.includeInTotal) Muted("Included in total assets")
                        if (account.note.isNotEmpty()) Muted(account.note)
                    }
                }
                Divider()
                ListRow("Current balance", f.money(s.balance(account), account.currency))
                if (account.currency != f.base) ListRow("In ${f.base}", f.money(s.balanceInBase(account)))
                val saved = s.depositsInto(account)
                if (saved != 0L) ListRow("From savings plans", f.money(saved, account.currency), chevron = false)
            }
            if (history.isEmpty()) EmptyNote("No bills on this account") else BillDays(history, s, showAccount = false) { nav.openBill(it.id) }
        }
    }
    if (corrected) {
        ConfirmDialog(
            "Balance Correction",
            "No anomaly detected in the account balance, no correction needed",
            confirm = "OK",
            danger = false,
            onDismiss = { corrected = false },
        ) { corrected = false }
    }
    if (confirm) {
        ConfirmDialog(
            "Delete ${account.name.trim()}?",
            "Its bills stay, but they will no longer be linked to an account. This cannot be undone.",
            onDismiss = { confirm = false },
        ) {
            confirm = false
            repo.deleteAccount(account)
            Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
            nav.leavePage()
        }
    }
}

@Composable
fun AccountEditScreen(id: Long) {
    val s = snap()
    val settings = prefs()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    val existing = remember { s.accountById[id] }
    var preset by remember { mutableStateOf<Seed.AccountPreset?>(null) }
    var name by remember { mutableStateOf(existing?.name?.trim().orEmpty()) }
    var icon by remember { mutableStateOf(existing?.icon ?: "💳") }
    var color by remember { mutableLongStateOf(existing?.color ?: 0xFF3A3A3F) }
    var kind by remember { mutableIntStateOf(existing?.kind ?: AccountKind.SAVINGS) }
    var currency by remember { mutableStateOf(existing?.currency ?: settings.baseCurrency) }
    var balance by remember {
        mutableStateOf(existing?.let { Calculator.format(Money.fromCents(s.balance(it))) } ?: "")
    }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var included by remember { mutableStateOf(existing?.includeInTotal ?: true) }
    var pickCurrency by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(existing == null) }

    if (choosing) {
        Page("Add account") {
            ScrollBody {
                listOf(AccountKind.SAVINGS to "Savings Account", AccountKind.CREDIT to "Credit Account").forEach { (type, label) ->
                    SectionTitle(label)
                    Panel {
                        Seed.accountPresets.filter { it.kind == type }.forEachIndexed { index, option ->
                            if (index > 0) Divider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        preset = option
                                        icon = option.icon
                                        color = option.color
                                        kind = option.kind
                                        if (!option.custom) name = option.name
                                        choosing = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AccountBubble(option.name, option.icon, option.color, 42.dp)
                                Text(option.name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Page(
        if (existing == null) "Add account" else "Edit account",
        onBack = { if (existing == null) choosing = true else nav.leavePage() },
    ) {
        ScrollBody {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountBubble(name, icon, color, 56.dp)
                WalletField(name, { name = it }, Modifier.padding(start = 12.dp).weight(1f), "Account name")
            }
            AmountField("Current balance", balance) { balance = it }
            Panel {
                ListRow("Currency", currency) { pickCurrency = true }
                Divider()
                SwitchRow("Included in total assets", included) { included = it }
            }
            WalletField(note, { note = it }, Modifier.fillMaxWidth(), "Notes", singleLine = false)
            PrimaryButton("Save", Modifier.fillMaxWidth()) {
                if (name.isBlank()) {
                    Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@PrimaryButton
                }
                val wanted = Money.parse(balance.ifBlank { "0" }) ?: 0
                val base = existing ?: Account(name = name.trim(), sort = s.accounts.size)
                // Measured in the currency being saved, so changing currency and balance together comes out right.
                val movement = if (existing != null) s.balance(existing.copy(currency = currency)) - existing.openingCents else 0
                repo.save {
                    put(
                        base.copy(
                            name = name.trim(),
                            kind = kind,
                            icon = icon,
                            color = color,
                            currency = currency,
                            openingCents = wanted - movement,
                            note = note.trim(),
                            includeInTotal = included,
                        ),
                    )
                }
                nav.leavePage()
            }
        }
    }
    if (pickCurrency) {
        OptionDialog(
            "Currency",
            Currencies.all.map { it.code },
            currency,
            { "${Currencies.info(it).flag}  $it  ${Currencies.info(it).name}" },
            { pickCurrency = false },
        ) {
            currency = it
            pickCurrency = false
        }
    }
}
