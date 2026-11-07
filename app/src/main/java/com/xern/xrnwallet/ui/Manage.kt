package com.xern.xrnwallet.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Category
import com.xern.xrnwallet.data.Checklist
import com.xern.xrnwallet.data.ChecklistItem
import com.xern.xrnwallet.data.Ledger
import com.xern.xrnwallet.data.Seed
import com.xern.xrnwallet.data.Tag
import com.xern.xrnwallet.data.totals
import com.xern.xrnwallet.domain.Currencies

@Composable
fun LedgersScreen() {
    val s = snap()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    Page(
        "My Ledgers",
        actions = { IconButton({ nav.navigate("ledger/edit") }) { Icon(Icons.Filled.AddBox, "New ledger") } },
    ) {
        ScrollBody {
            Muted("Long press to reorder")
            ReorderGrid(
                items = s.ledgers,
                columns = 2,
                keyOf = { it.id },
                onMove = { order -> repo.save { putLedgers(order.mapIndexed { i, ledger -> ledger.copy(sort = i) }) } },
            ) { ledger ->
                val (icon, color) = Seed.ledgerCovers.getOrElse(ledger.cover) { Seed.ledgerCovers[0] }
                Column(
                    Modifier
                        .padding(6.dp)
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(Color(color))
                        .clickable { nav.navigate("ledger/${ledger.id}") }
                        .padding(14.dp),
                ) {
                    Text(icon, fontSize = 34.sp)
                    Text(ledger.name, Modifier.padding(top = 18.dp), color = Color.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(billCount(s.bills.count { it.ledgerId == ledger.id }), color = Color.Black.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun LedgerEditScreen(id: Long) {
    val s = snap()
    val settings = prefs()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    val existing = remember { s.ledgerById[id] }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var cover by remember { mutableIntStateOf(existing?.cover ?: 0) }
    var currency by remember { mutableStateOf(existing?.currency ?: settings.baseCurrency) }
    var pick by remember { mutableStateOf(false) }

    Page(if (existing == null) "New ledger" else "Edit ledger") {
        ScrollBody {
            SectionTitle("Cover")
            Seed.ledgerCovers.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { index, (icon, color) ->
                        val position = rowIndex * 3 + index
                        Selectable(position == cover, Modifier.weight(1f), onClick = { cover = position }) {
                            Box(Modifier.fillMaxWidth().height(84.dp).background(Color(color)), contentAlignment = Alignment.Center) {
                                Text(icon, fontSize = 30.sp)
                            }
                        }
                    }
                }
            }
            WalletField(name, { name = it }, Modifier.fillMaxWidth(), "Ledger name")
            Panel { ListRow("Currency", currency) { pick = true } }
            PrimaryButton("Save", Modifier.fillMaxWidth()) {
                if (name.isBlank()) {
                    Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@PrimaryButton
                }
                val base = existing ?: Ledger(name = name, sort = s.ledgers.size)
                repo.save { put(base.copy(name = name.trim(), cover = cover, currency = currency)) }
                nav.popBackStack()
            }
        }
    }
    if (pick) {
        OptionDialog("Currency", Currencies.all.map { it.code }, currency, { "$it  ${Currencies.info(it).name}" }, { pick = false }) {
            currency = it
            pick = false
        }
    }
}

@Composable
fun LedgerDetailScreen(id: Long) {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val ledger = s.ledgerById[id]
    var confirm by remember { mutableStateOf(false) }
    if (ledger == null) {
        Page("Ledger") { EmptyNote("This ledger no longer exists") }
        return
    }
    val bills = remember(s, id) { s.bills.filter { it.ledgerId == id } }
    val totals = remember(bills) { bills.totals() }
    Page(
        ledger.name,
        actions = {
            IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "Delete ledger", tint = Color(0xFFFF3B30)) }
            IconButton({ nav.navigate("ledger/edit?id=$id") }) { Icon(Icons.Filled.Edit, "Edit ledger") }
        },
    ) {
        ScrollBody {
            Panel {
                ListRow("Expenses", f.money(totals.expense))
                Divider()
                ListRow("Income", f.money(totals.income))
                Divider()
                ListRow("Balance", f.money(totals.balance))
                Divider()
                ListRow("Bills", bills.size.toString())
            }
            if (bills.isEmpty()) EmptyNote("No bills in this ledger") else BillDays(bills, s) { nav.openBill(it.id) }
        }
    }
    if (confirm) {
        ConfirmDialog(
            "Delete ${ledger.name}?",
            "All ${bills.size} bills in this ledger will be deleted too. This cannot be undone.",
            onDismiss = { confirm = false },
        ) {
            confirm = false
            repo.deleteLedger(ledger)
            nav.popBackStack()
        }
    }
}

@Composable
private fun CategoryTile(category: Category, size: Dp = 62.dp, badge: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(Modifier.padding(4.dp)) { Bubble(category.icon, size) }
            Box(Modifier.align(Alignment.TopStart)) { badge() }
        }
        Text(
            category.name,
            Modifier.padding(top = 4.dp),
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.Black, modifier = Modifier.size(18.dp)) }
}

/** Opens under a category's row: the category itself, its subcategories, then a tile to add one. */
@Composable
private fun SubcategoryPanel(category: Category, subs: List<Category>) {
    val nav = LocalNav.current
    Panel(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val tiles: List<Category?> = listOf(category) + subs + null
        tiles.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    Box(Modifier.weight(1f)) {
                        if (tile != null) {
                            CategoryTile(tile, 52.dp) { nav.navigate("category/edit/${tile.id}") }
                        } else {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { nav.navigate("category/sub/${category.type}/${category.id}") }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.padding(4.dp)) { Bubble("＋", 52.dp) }
                                Text(
                                    "Subcategory",
                                    Modifier.padding(top = 4.dp),
                                    textDecoration = TextDecoration.Underline,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun CategoriesScreen() {
    val s = snap()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val type = if (tab == 0) BillType.EXPENSE else BillType.INCOME
    val top = s.categories.filter { it.type == type && it.parentId == null }
    val inUse = top.filter { it.active }
    val notUsed = top.filter { !it.active }
    val expanded = top.firstOrNull { it.id == open }
    val panel: @Composable (Category) -> Unit = { category ->
        SubcategoryPanel(category, s.categories.filter { it.parentId == category.id })
    }
    fun toggle(category: Category) {
        open = category.id.takeIf { it != open }
    }

    Page(
        "Category",
        actions = { IconButton({ nav.navigate("category/new/$type") }) { Icon(Icons.Filled.AddBox, "New category") } },
    ) {
        UnderlineTabs(listOf("Expenses", "Income"), tab, onSelect = { tab = it })
        ScrollBody {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("In use ", style = MaterialTheme.typography.titleMedium)
                Muted("(Long press to reorder)")
            }
            ReorderGrid(
                items = inUse,
                columns = 4,
                keyOf = { it.id },
                onMove = { order -> repo.save { putCategories(order.mapIndexed { i, c -> c.copy(sort = i) }) } },
                expandedKey = expanded?.takeIf { it.active }?.id,
                expansion = { expanded?.let { panel(it) } },
            ) { category ->
                CategoryTile(
                    category,
                    badge = { Badge(Icons.Filled.Remove, "Stop using ${category.name}") { repo.save { put(category.copy(active = false)) } } },
                ) { toggle(category) }
            }
            Text("Not used", style = MaterialTheme.typography.titleMedium)
            notUsed.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { category ->
                        Box(Modifier.weight(1f)) {
                            CategoryTile(
                                category,
                                badge = { Badge(Icons.Filled.Add, "Use ${category.name}") { repo.save { put(category.copy(active = true)) } } },
                            ) { toggle(category) }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                if (expanded != null && expanded in row) panel(expanded)
            }
        }
    }
}

/** Tabs of the app's drawing styles and emoji, with a grid of icons under them. */
@Composable
fun IconPicker(selected: String, onPick: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(Art.tabOf(selected)) }
    UnderlineTabs((1..Art.STYLES).map { "Style$it" } + "emoji", tab, onSelect = { tab = it })
    val options = if (tab == Art.STYLES) Art.emojiChoices else Art.choices(tab + 1)
    options.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            row.forEach { option ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val ring = if (Art.same(option, selected)) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).then(ring).clickable { onPick(option) },
                        contentAlignment = Alignment.Center,
                    ) { Bubble(option, 56.dp) }
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun CategoryEditScreen(id: Long, type: Int, parent: Long?) {
    val s = snap()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    val existing = remember { s.categoryById[id] }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var icon by remember { mutableStateOf(existing?.icon ?: "⭐") }
    var incomeTab by remember { mutableIntStateOf(if (type == BillType.INCOME) 1 else 0) }
    var confirm by remember { mutableStateOf(false) }
    val used = existing != null && s.bills.any { it.categoryId == existing.id }
    val creatingTop = existing == null && parent == null

    Page(
        when {
            existing != null -> "Edit category"
            parent != null -> "Add subcategory"
            else -> "Custom"
        },
        actions = {
            if (existing != null) {
                IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "Delete category", tint = Color(0xFFFF3B30)) }
            }
            IconButton({
                if (name.isBlank()) {
                    Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@IconButton
                }
                val chosenType = if (!creatingTop) type else if (incomeTab == 1) BillType.INCOME else BillType.EXPENSE
                val base = existing ?: Category(type = chosenType, name = name, icon = icon, parentId = parent, sort = s.categories.size)
                repo.save { put(base.copy(name = name.trim(), icon = icon)) }
                nav.popBackStack()
            }) { Icon(Icons.Filled.Check, "Save") }
        },
    ) {
        if (creatingTop) UnderlineTabs(listOf("Expenses", "Income"), incomeTab, onSelect = { incomeTab = it })
        ScrollBody {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(86.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Bubble(icon, 76.dp) }
                WalletField(name, { name = it }, Modifier.padding(top = 12.dp).width(240.dp), "Enter category name")
            }
            IconPicker(icon) { icon = it }
        }
    }
    if (confirm && existing != null) {
        if (used) {
            ConfirmDialog(
                "In use",
                "Bills use this category, so it cannot be deleted. Move it to Not used instead.",
                confirm = "OK",
                danger = false,
                onDismiss = { confirm = false },
            ) { confirm = false }
        } else {
            ConfirmDialog("Delete ${existing.name}?", "This cannot be undone.", onDismiss = { confirm = false }) {
                confirm = false
                repo.save {
                    deleteCategoryBudget(existing.id)
                    deleteCategory(existing.id)
                }
                nav.popBackStack()
            }
        }
    }
}

@Composable
fun TagsScreen() {
    val s = snap()
    val settings = prefs()
    val repo = LocalRepo.current
    var edit by remember { mutableStateOf<Tag?>(null) }
    var create by remember { mutableStateOf(false) }
    Page(
        "Tags",
        actions = { IconButton({ create = true }) { Icon(Icons.Filled.AddBox, "New tag") } },
    ) {
        ScrollBody {
            Panel { SwitchRow("Show on bill card", settings.showTagsOnCard) { on -> repo.store.update { it.copy(showTagsOnCard = on) } } }
            if (s.tags.isEmpty()) EmptyNote("No tags yet")
            ReorderGrid(
                items = s.tags,
                columns = 1,
                keyOf = { it.id },
                onMove = { order -> repo.save { putTags(order.mapIndexed { i, tag -> tag.copy(sort = i) }) } },
            ) { tag ->
                Panel(Modifier.padding(vertical = 5.dp).clickable { edit = tag }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        TagChip(tag.name, Color(tag.color))
                        Spacer(Modifier.weight(1f))
                        Muted(billCount(s.billTags.count { it.tagId == tag.id }))
                    }
                }
            }
        }
    }
    if (create) TagEditor(null, { create = false })
    edit?.let { TagEditor(it, { edit = null }) }
}

@Composable
private fun TagEditor(tag: Tag?, onDismiss: () -> Unit) {
    val s = snap()
    val repo = LocalRepo.current
    var name by remember { mutableStateOf(tag?.name.orEmpty()) }
    var color by remember { mutableLongStateOf(tag?.color ?: Seed.tagColors[0]) }
    DialogCard(onDismiss) {
        WalletField(name, { name = it }, Modifier.fillMaxWidth(), "Tag name")
        Spacer(Modifier.height(12.dp))
        Seed.tagColors.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { option ->
                    Box(
                        Modifier
                            .padding(5.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(option))
                            .clickable { color = option },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (option == color) Icon(Icons.Filled.Check, null, tint = Color.White)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (tag != null) {
                GhostButton("Delete", Modifier.weight(1f)) {
                    repo.deleteTag(tag)
                    onDismiss()
                }
            }
            PrimaryButton("Save", Modifier.weight(1f), enabled = name.isNotBlank()) {
                val base = tag ?: Tag(name = name, sort = s.tags.size)
                repo.save { put(base.copy(name = name.trim(), color = color)) }
                onDismiss()
            }
        }
    }
}

@Composable
fun ChecklistsScreen() {
    val s = snap()
    val settings = prefs()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    var create by remember { mutableStateOf(false) }
    Page(
        "Checklist",
        actions = { IconButton({ create = true }) { Icon(Icons.Filled.AddBox, "New list") } },
    ) {
        ScrollBody {
            Panel {
                SwitchRow("Show incomplete lists on homepage", settings.checklistOnHome) { on ->
                    repo.store.update { it.copy(checklistOnHome = on) }
                }
            }
            if (s.checklists.isEmpty()) EmptyNote("No lists yet")
            s.checklists.forEach { list ->
                val items = s.checklistItems.filter { it.checklistId == list.id }
                Panel(Modifier.clickable { nav.navigate("checklist/${list.id}") }) {
                    ListRow(list.name, "${items.count { it.done }}/${items.size}", onClick = { nav.navigate("checklist/${list.id}") })
                }
            }
        }
    }
    if (create) {
        InputDialog("New list", onDismiss = { create = false }) { name ->
            if (name.isNotEmpty()) repo.save { put(Checklist(name = name, sort = s.checklists.size)) }
            create = false
        }
    }
}

@Composable
fun ChecklistDetailScreen(id: Long) {
    val s = snap()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val list = s.checklists.firstOrNull { it.id == id }
    var add by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    if (list == null) {
        Page("Checklist") { EmptyNote("This list no longer exists") }
        return
    }
    val items = s.checklistItems.filter { it.checklistId == id }
    Page(
        list.name,
        actions = {
            IconButton({ confirm = true }) { Icon(Icons.Filled.Delete, "Delete list", tint = Color(0xFFFF3B30)) }
            IconButton({ rename = true }) { Icon(Icons.Filled.Edit, "Rename list") }
            IconButton({ add = true }) { Icon(Icons.Filled.AddBox, "Add item") }
        },
    ) {
        ScrollBody {
            if (items.isEmpty()) EmptyNote("No items yet")
            items.forEach { item ->
                Panel {
                    Row(
                        Modifier.fillMaxWidth().clickable { repo.save { put(item.copy(done = !item.done)) } }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (item.done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            if (item.done) "Done" else "Not done",
                            tint = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            item.text,
                            Modifier.padding(start = 10.dp).weight(1f),
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        )
                        Icon(
                            Icons.Filled.Close,
                            "Remove item",
                            Modifier.size(20.dp).clickable { repo.save { delete(item) } },
                        )
                    }
                }
            }
        }
    }
    if (add) {
        InputDialog("New item", onDismiss = { add = false }) { text ->
            if (text.isNotEmpty()) repo.save { put(ChecklistItem(checklistId = id, text = text, sort = items.size)) }
            add = false
        }
    }
    if (rename) {
        InputDialog("List name", list.name, onDismiss = { rename = false }) { text ->
            if (text.isNotEmpty()) repo.save { put(list.copy(name = text)) }
            rename = false
        }
    }
    if (confirm) {
        ConfirmDialog("Delete ${list.name}?", "Its items go with it.", onDismiss = { confirm = false }) {
            confirm = false
            repo.deleteChecklist(list)
            nav.popBackStack()
        }
    }
}
