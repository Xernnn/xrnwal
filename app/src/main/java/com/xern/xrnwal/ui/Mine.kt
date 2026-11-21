package com.xern.xrnwal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xern.xrnwal.BuildConfig
import com.xern.xrnwal.data.BillType
import com.xern.xrnwal.data.Seed
import com.xern.xrnwal.data.totals
import kotlinx.coroutines.launch

private data class Tile(val icon: String, val label: String, val route: String)

private val commonlyUsed = listOf(
    Tile("📊", "Budget", "budget"),
    Tile("🐷", "Savings Plan", "savings"),
    Tile("🔁", "Recurring Transactions", "recurring"),
    Tile("📅", "Calendar", "calendar"),
    Tile("📘", "My Ledgers", "ledgers"),
    Tile("🗂️", "Category", "categories"),
    Tile("🏷️", "Tags", "tags"),
    Tile("✅", "Checklist", "checklists"),
)

private val assistive = listOf(
    Tile("🎨", "Appearance", "appearance"),
    Tile("⏰", "Reminders", "reminders"),
    Tile("🔒", "Password Protection", "password"),
    Tile("💱", "Exchange Rate", "exchange"),
    Tile("🔍", "Filter", "filter"),
    Tile("🔄", "Data synchronization", "sync"),
    Tile("📤", "Export Data", "export"),
    Tile("📥", "Import Data", "import"),
    Tile("🗓️", "Start of Month", "startofmonth"),
    Tile("➕", "Balance Carryover", "carryover"),
    Tile("⚙️", "Preference Settings", "preferences"),
)

private val other = listOf(
    Tile("💲", "Currencies and exchange rates", "currencies"),
    Tile("🔤", "Languages", "languages"),
    Tile("📳", "Haptic feedback", "haptics"),
    Tile("🖼️", "App Icons", "appicons"),
    Tile("👋", "Getting started", "welcome"),
)

@Composable
fun MineScreen() {
    val s = snap()
    val settings = prefs()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val sync = syncManager()
    val scope = rememberCoroutineScope()
    val totals = remember(s) { s.bills.totals() }
    val records = remember(s) { s.bills.count { it.type != BillType.TRANSFER } }
    var editName by remember { mutableStateOf(false) }
    var editAvatar by remember { mutableStateOf(false) }
    var deleteAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            Text("Mine", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { editAvatar = true }) { Bubble("e:${settings.avatar}", 62.dp) }
                    Row(Modifier.padding(start = 14.dp).clickable { editName = true }, verticalAlignment = Alignment.CenterVertically) {
                        Text(settings.nickname, style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Filled.Edit, "Change nickname", Modifier.padding(start = 4.dp).size(18.dp))
                    }
                }
            }
            Panel {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    MineStat("Expenses", f.money(totals.expense), f.expenseArrow, f.expenseColor, Modifier.weight(1f))
                    MineStat("Income", f.money(totals.income), f.incomeArrow, f.incomeColor, Modifier.weight(1f))
                    MineStat("Records", records.toString(), null, Color.Unspecified, Modifier.weight(1f))
                }
            }
            SectionTitle("Commonly Used")
            TileGrid(commonlyUsed)
            SectionTitle("Assistive")
            TileGrid(assistive)
            SectionTitle("Other")
            TileGrid(other)
            Panel(Modifier.clickable { deleteAll = true }) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Delete, null, tint = Color(0xFFE53935))
                    Text("Delete all data", Modifier.padding(start = 8.dp), color = Color(0xFFE53935), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Muted(BuildConfig.VERSION_NAME, Modifier.fillMaxWidth().padding(top = 8.dp))
            Muted("Font: MiSans by Xiaomi", Modifier.fillMaxWidth())
        }
    }

    if (editName) {
        InputDialog("Nickname", settings.nickname, onDismiss = { editName = false }) { name ->
            if (name.isNotEmpty()) repo.store.update { it.copy(nickname = name) }
            editName = false
        }
    }
    if (editAvatar) {
        DialogCard({ editAvatar = false }) {
            Text("Avatar", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Seed.avatars.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { avatar ->
                        Box(
                            Modifier
                                .padding(6.dp)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (avatar == settings.avatar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable {
                                    repo.store.update { it.copy(avatar = avatar) }
                                    editAvatar = false
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(avatar, fontSize = 28.sp) }
                    }
                }
            }
        }
    }
    if (deleteAll) {
        val connected = sync.status.collectAsStateWithLifecycle().value.connected
        ConfirmDialog(
            "Delete all data?",
            "Every bill, account, ledger, budget and plan on this phone will be removed. " +
                if (connected) "The server's copy stays, and Data synchronization asks which of the two to keep." else "This cannot be undone.",
            onDismiss = { deleteAll = false },
        ) {
            deleteAll = false
            nav.popBackStack("main", false)
            // Not sent up as an edit, which would empty the server's copy and every other phone.
            scope.launch { sync.replaceHere { repo.deleteEverything() } }
        }
    }
}

@Composable
private fun MineStat(label: String, value: String, arrow: String?, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (arrow != null) Arrow(arrow, color)
            Muted(label)
        }
        Text(value, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TileGrid(tiles: List<Tile>) {
    val nav = LocalNav.current
    Panel {
        Column(Modifier.padding(vertical = 10.dp)) {
            tiles.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { tile ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { nav.navigate(tile.route) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Glyph(tile.icon, 40.dp)
                            Text(
                                tile.label,
                                Modifier.padding(top = 6.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
