package com.xern.xrnwallet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xern.xrnwallet.data.ALL_LEDGERS
import com.xern.xrnwallet.data.Account
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillCsv
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Category
import com.xern.xrnwallet.data.Tag
import com.xern.xrnwallet.domain.Calculator
import com.xern.xrnwallet.domain.Currencies
import com.xern.xrnwallet.domain.Dates
import com.xern.xrnwallet.domain.Money
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Copies a picked image into private storage so it survives the source being deleted. */
private const val PHOTO_LONG_EDGE = 1600

/**
 * Stores a shrunken JPEG, which keeps receipts readable while keeping backups a sensible size.
 * Encoding drops the original orientation tag, so the turn it describes is applied here.
 */
private fun savePhoto(context: Context, uri: Uri): String? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_LONG_EDGE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching null
    val turn = when (resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) }) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val scale = minOf(1f, PHOTO_LONG_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(turn)
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    val target = File(File(context.filesDir, "photos").apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
    target.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    target.absolutePath
}.getOrNull()

@Composable
fun BillEditScreen(id: Long, date: Long, initialType: Int) {
    val s = snap()
    val settings = prefs()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember { s.bills.firstOrNull { it.id == id } }

    var tab by remember { mutableIntStateOf(existing?.type ?: initialType) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var ledgerId by remember {
        mutableLongStateOf(
            existing?.ledgerId
                ?: s.activeLedger(settings).takeIf { it != ALL_LEDGERS }
                ?: s.ledgers.firstOrNull()?.id
                ?: 0,
        )
    }
    val usable = s.accounts.filter { !it.archived }
    var accountId by remember {
        mutableStateOf(
            if (existing != null) existing.accountId
            else s.bills.firstOrNull { it.accountId != null && it.type != BillType.TRANSFER }?.accountId?.takeIf { s.accountById.containsKey(it) }
                ?: usable.firstOrNull()?.id,
        )
    }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.let { Calculator.format(Money.fromCents(it.amountCents)) } ?: "0") }
    var currency by remember { mutableStateOf(existing?.currency ?: settings.baseCurrency) }
    var feeCents by remember { mutableLongStateOf(existing?.feeCents ?: 0) }
    var photo by remember { mutableStateOf(existing?.photo) }
    var at by remember {
        mutableStateOf(
            existing?.dateTime
                ?: if (date != NO_DATE) LocalDate.ofEpochDay(date).atTime(LocalTime.now()) else LocalDateTime.now(),
        )
    }
    var tagIds by remember { mutableStateOf(if (existing != null) s.tagIdsOf(existing.id) else emptyList()) }

    val tabs = rememberPagerState(initialPage = tab) { BillType.TRANSFER + 1 }
    LaunchedEffect(tabs) { snapshotFlow { tabs.currentPage }.collect { tab = it } }

    var dialog by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch { photo = withContext(Dispatchers.IO) { savePhoto(context, uri) } ?: photo }
        }
    }

    val categories = s.categories.filter { it.type == tab && it.parentId == null && it.active }
    if (tab != BillType.TRANSFER && s.categoryById[categoryId]?.type != tab) categoryId = categories.firstOrNull()?.id

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun save() {
        val cents = Calculator.evaluate(amount)?.let(Money::toCents)
        if (cents == null || cents <= 0) {
            toast("Please enter the amount")
            return
        }
        if (tab == BillType.TRANSFER) {
            if (accountId == null || toAccountId == null) {
                toast("Please select an account")
                return
            }
            if (accountId == toAccountId) {
                toast("Choose two different accounts")
                return
            }
        }
        val rate = if (existing != null && existing.currency == currency) existing.rate else s.rate(currency)
        val bill = Bill(
            id = existing?.id ?: 0,
            ledgerId = ledgerId,
            type = tab,
            categoryId = if (tab == BillType.TRANSFER) null else categoryId,
            accountId = accountId,
            toAccountId = if (tab == BillType.TRANSFER) toAccountId else null,
            amountCents = cents,
            currency = currency,
            rate = rate,
            feeCents = if (tab == BillType.TRANSFER) feeCents else 0,
            note = note.trim(),
            photo = photo,
            atMillis = BillCsv.millisOf(at),
            inBudget = existing?.inBudget ?: true,
            recurringId = existing?.recurringId,
        )
        scope.launch {
            repo.saveBill(bill, tagIds)
            nav.popBackStack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.Filled.Close, "Close") }
            Spacer(Modifier.weight(1f))
            Text(
                s.ledgerById[ledgerId]?.name ?: "No ledger",
                Modifier.clickable { dialog = "ledger" }.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        UnderlineTabs(listOf("Expenses", "Income", "Transfer"), tab, onSelect = { scope.launch { tabs.animateScrollToPage(it) } })
        HorizontalPager(tabs, Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
            if (page == BillType.TRANSFER) {
                TransferForm(
                    from = s.accountById[accountId],
                    to = s.accountById[toAccountId],
                    feeCents = feeCents,
                    onFrom = { dialog = "from" },
                    onTo = { dialog = "to" },
                    onSwap = {
                        val previous = accountId
                        accountId = toAccountId
                        toAccountId = previous
                    },
                    onFee = {
                        if (accountId == null) toast("Please select an account") else dialog = "fee"
                    },
                )
            } else {
                CategoryGrid(
                    categories = s.categories.filter { it.type == page && it.parentId == null && it.active },
                    selected = categoryId?.let { s.topCategory(it)?.id },
                    showEdit = page == BillType.INCOME,
                    onEdit = { nav.navigate("categories") },
                ) { category ->
                    categoryId = category.id
                    if (s.categories.any { it.parentId == category.id && it.active }) dialog = "sub"
                }
            }
        }
        AnimatedVisibility(tab != BillType.TRANSFER) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .widthIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { dialog = "account" }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        s.accountById[accountId]?.name?.trim() ?: "No account",
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (tagIds.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                        .clickable { dialog = "tags" }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Label,
                        "Tags",
                        Modifier.size(20.dp),
                        tint = if (tagIds.isEmpty()) MaterialTheme.colorScheme.onSurface else Color.Black,
                    )
                }
            }
        }
        if (settings.noteSuggestions && tab != BillType.TRANSFER) {
            val notes = s.bills.asSequence()
                .filter { it.categoryId == categoryId && it.note.isNotEmpty() }
                .map { it.note }.distinct().take(6).toList()
            if (notes.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    notes.take(3).forEach { Pill(it, false, Modifier.weight(1f, fill = false)) { note = it } }
                }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        ) {
            Column(Modifier.navigationBarsPadding().padding(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val preview = remember(photo) { photo?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
                    if (preview != null) {
                        Image(
                            preview,
                            "Attached photo. Tap to remove.",
                            Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).clickable { photo = null },
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            Icons.Filled.CameraAlt,
                            "Attach photo",
                            Modifier.size(28.dp).clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                    }
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = "Note" },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (note.isEmpty()) Muted("Add a note")
                            inner()
                        },
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            amount.replace('.', settings.moneyFormat.decimalChar),
                            fontSize = if (amount.length > 12) 18.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            currency,
                            Modifier.clickable { dialog = "currency" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Keypad(
                    amount = amount,
                    decimal = settings.moneyFormat.decimalChar,
                    dateLabel = if (at.toLocalDate() == LocalDate.now()) "Today" else Dates.short(at.toLocalDate()),
                    onAmount = { amount = it },
                    onDate = { dialog = "date" },
                    onSave = ::save,
                )
            }
        }
    }

    when (dialog) {
        "ledger" -> LedgerDialog(s.ledgers, ledgerId, allowAll = false, onDismiss = { dialog = null }) {
            ledgerId = it
            dialog = null
        }
        "account", "from", "to" -> {
            val target = dialog
            AccountDialog(
                accounts = usable,
                allowNone = target == "account",
                onDismiss = { dialog = null },
                onCreate = { dialog = null; nav.navigate("account/edit") },
            ) { picked ->
                when (target) {
                    "to" -> toAccountId = picked
                    else -> {
                        accountId = picked
                        s.accountById[picked]?.let { if (target == "from" || tab != BillType.TRANSFER) currency = it.currency }
                    }
                }
                dialog = null
            }
        }
        "tags" -> TagDialog(s.tags, tagIds, onDismiss = { dialog = null }, onCreate = { dialog = null; nav.navigate("tags") }) {
            tagIds = it
            dialog = null
        }
        "currency" -> CurrencyDialog(onDismiss = { dialog = null }) {
            currency = it
            dialog = null
        }
        "date" -> DateTimeDialog(at, latest = LocalDate.now(), onDismiss = { dialog = null }) {
            at = it
            dialog = null
        }
        "fee" -> InputDialog(
            "Fee",
            initial = if (feeCents == 0L) "" else Calculator.format(Money.fromCents(feeCents)),
            numeric = true,
            onDismiss = { dialog = null },
        ) {
            feeCents = Money.parse(it) ?: 0
            dialog = null
        }
        "sub" -> {
            val parent = s.topCategory(categoryId)
            val options = listOfNotNull(parent) + s.categories.filter { it.parentId == parent?.id && it.active }
            OptionDialog(parent?.name ?: "Category", options, s.categoryById[categoryId], { it.name }, { dialog = null }) {
                categoryId = it.id
                dialog = null
            }
        }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<Category>,
    selected: Long?,
    showEdit: Boolean,
    onEdit: () -> Unit,
    onPick: (Category) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPick(category) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(softTint(category.icon))
                        .then(if (category.id == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Glyph(category.icon, 30.dp) }
                Text(
                    category.name,
                    Modifier.padding(top = 4.dp),
                    // Two lines are for two words. One word on two lines would break in the middle.
                    maxLines = if (' ' in category.name) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (showEdit) {
            item {
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onEdit).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Edit, null) }
                    Text("Edit", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TransferForm(
    from: Account?,
    to: Account?,
    feeCents: Long,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onSwap: () -> Unit,
    onFee: () -> Unit,
) {
    val f = fmt()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            ListRow("Payment account", from?.name?.trim() ?: "Select", onClick = onFrom)
            Divider()
            ListRow("Receive account", to?.name?.trim() ?: "Select", onClick = onTo)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onSwap) { Icon(Icons.Filled.SwapVert, "Swap accounts") }
        }
        Panel { ListRow("Fee", if (feeCents == 0L) "None" else f.money(feeCents, from?.currency ?: f.base), onClick = onFee) }
    }
}

@Composable
private fun Keypad(
    amount: String,
    decimal: Char,
    dateLabel: String,
    onAmount: (String) -> Unit,
    onDate: () -> Unit,
    onSave: () -> Unit,
) {
    val settings = prefs()
    val view = LocalView.current
    fun tick() {
        if (!settings.hapticsOn) return
        view.performHapticFeedback(
            when (settings.hapticStrength) {
                0 -> android.view.HapticFeedbackConstants.CLOCK_TICK
                2 -> android.view.HapticFeedbackConstants.LONG_PRESS
                else -> android.view.HapticFeedbackConstants.KEYBOARD_TAP
            },
        )
    }
    fun press(key: Char) {
        tick()
        onAmount(Calculator.press(amount, key))
    }
    val pending = Calculator.hasPendingSum(amount)
    val height: Dp = 54.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            "123".forEach { Key(it.toString(), Modifier.weight(1f), height) { press(it) } }
            Key(
                "",
                Modifier.weight(1f),
                height,
                icon = { Icon(Icons.AutoMirrored.Filled.Backspace, "Backspace") },
                onLongClick = {
                    tick()
                    onAmount("0")
                },
            ) {
                tick()
                onAmount(Calculator.backspace(amount))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            "456".forEach { Key(it.toString(), Modifier.weight(1f), height) { press(it) } }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("+", Modifier.weight(1f), height) { press('+') }
                Key("×", Modifier.weight(1f), height) { press('×') }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            "789".forEach { Key(it.toString(), Modifier.weight(1f), height) { press(it) } }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("-", Modifier.weight(1f), height) { press('-') }
                Key("÷", Modifier.weight(1f), height) { press('÷') }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(decimal.toString(), Modifier.weight(1f), height) { press('.') }
            Key("0", Modifier.weight(1f), height) { press('0') }
            Key(dateLabel, Modifier.weight(1f), height, onClick = onDate)
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        tick()
                        if (pending) {
                            Calculator.evaluate(amount)?.let { onAmount(Calculator.format(it)) }
                        } else {
                            onSave()
                        }
                    }
                    .semantics { contentDescription = if (pending) "Equals" else "Save" },
                contentAlignment = Alignment.Center,
            ) {
                if (pending) {
                    Text("=", color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Check, null, tint = Color.Black)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Key(
    label: String,
    modifier: Modifier,
    height: Dp,
    icon: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale = pressedScale(source)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(source, ripple(), onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) icon() else Text(label, fontSize = if (label.length > 1) 16.sp else 21.sp, maxLines = 1)
    }
}

@Composable
fun AccountDialog(
    accounts: List<Account>,
    allowNone: Boolean,
    onDismiss: () -> Unit,
    onCreate: (() -> Unit)? = null,
    onPick: (Long?) -> Unit,
) {
    val s = snap()
    val f = fmt()
    DialogCard(onDismiss) {
        Box(Modifier.fillMaxWidth()) {
            Text("Accounts", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
            if (onCreate != null) {
                IconButton(onCreate, Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Filled.Add, "New account") }
            }
        }
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            accounts.forEach { account ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(account.id) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccountBubble(account.name, account.icon, account.color, 40.dp)
                    Text(account.name.trim(), Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(f.money(s.balance(account), account.currency))
                }
            }
            if (allowNone) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(null) }
                        .padding(14.dp),
                ) { Muted("Do not bind personal accounts") }
            }
        }
    }
}

@Composable
fun TagDialog(tags: List<Tag>, selected: List<Long>, onDismiss: () -> Unit, onCreate: (() -> Unit)? = null, onDone: (List<Long>) -> Unit) {
    var chosen by remember { mutableStateOf(selected.toSet()) }
    DialogCard(onDismiss) {
        Box(Modifier.fillMaxWidth()) {
            Text("Tags", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium)
            if (onCreate != null) {
                IconButton(onCreate, Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Filled.Add, "New tag") }
            }
        }
        if (tags.isEmpty()) Muted("No tags yet", Modifier.padding(vertical = 14.dp))
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            tags.forEach { tag ->
                val on = tag.id in chosen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { chosen = if (on) chosen - tag.id else chosen + tag.id }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagChip(tag.name, Color(tag.color))
                    Spacer(Modifier.weight(1f))
                    if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        CancelConfirmRow(onDismiss) { onDone(chosen.toList()) }
    }
}

/** The base currency plus the billing currencies set up in Currencies. */
@Composable
fun CurrencyDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val s = snap()
    val settings = prefs()
    val options = listOf(settings.baseCurrency) + s.rates.map { it.code }.filter { it != settings.baseCurrency }
    OptionDialog("Currency", options, null, { "${Currencies.info(it).flag}  $it  ${Currencies.info(it).name}" }, onDismiss, onPick)
}

@Composable
fun BillDetailScreen(id: Long) {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val bill = s.bills.firstOrNull { it.id == id }
    val undo = LocalBillUndo.current
    var dialog by remember { mutableStateOf<String?>(null) }

    Page("Bill Details") {
        if (bill == null) {
            EmptyNote("This bill no longer exists")
            return@Page
        }
        ScrollBody {
            Panel {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Bubble(if (bill.type == BillType.TRANSFER) "🪙" else s.categoryById[bill.categoryId]?.icon ?: "⭐")
                    Text(
                        when (bill.type) {
                            BillType.TRANSFER -> "Transfer"
                            else -> s.categoryLabel(bill.categoryId)
                        },
                        Modifier.padding(start = 14.dp).weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when (bill.type) {
                            BillType.EXPENSE -> f.signed(-bill.amountCents, bill.currency)
                            BillType.INCOME -> f.signed(bill.amountCents, bill.currency)
                            else -> f.money(bill.amountCents, bill.currency)
                        },
                        color = when (bill.type) {
                            BillType.EXPENSE -> f.expenseColor
                            BillType.INCOME -> f.incomeColor
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Divider()
                if (bill.currency != f.base) ListRow("Base amount", f.money(bill.baseCents))
                ListRow("Notes", bill.note.ifEmpty { " " })
                ListRow("Ledger", s.ledgerById[bill.ledgerId]?.name ?: "None") { dialog = "ledger" }
                if (bill.type == BillType.TRANSFER) {
                    ListRow("Payment account", s.accountById[bill.accountId]?.name?.trim() ?: "None") { dialog = "account" }
                    ListRow("Receive account", s.accountById[bill.toAccountId]?.name?.trim() ?: "None")
                    if (bill.feeCents != 0L) ListRow("Fee", f.money(bill.feeCents, bill.currency))
                } else {
                    ListRow("Accounts", s.accountById[bill.accountId]?.name?.trim() ?: "None") { dialog = "account" }
                    SwitchRow("Included in budget", bill.inBudget) { repo.updateBill(bill.copy(inBudget = it)) }
                }
                val tags = s.tagsOf(bill.id)
                if (tags.isNotEmpty()) ListRow("Tags", tags.joinToString(", ") { it.name })
                ListRow("Date", Dates.full(bill.dateTime))
            }
            val photo = remember(bill.photo) { bill.photo?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
            if (photo != null) {
                Image(photo, "Attached photo", Modifier.fillMaxWidth().clip(CardShape), contentScale = ContentScale.FillWidth)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ undo.copyToToday(bill, s.tagIdsOf(bill.id)) }) {
                    Icon(Icons.Filled.ContentCopy, "Duplicate")
                }
                Spacer(Modifier.weight(1f))
                IconButton({
                    undo.delete(bill, s.tagIdsOf(bill.id))
                    nav.popBackStack()
                }) { Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF3B30)) }
                IconButton({ nav.navigate("edit?id=${bill.id}") }) { Icon(Icons.Filled.Edit, "Edit") }
            }
        }
    }
    if (bill != null) {
        when (dialog) {
            "ledger" -> LedgerDialog(s.ledgers, bill.ledgerId, allowAll = false, onDismiss = { dialog = null }) {
                repo.updateBill(bill.copy(ledgerId = it))
                dialog = null
            }
            "account" -> AccountDialog(s.accounts.filter { !it.archived }, allowNone = bill.type != BillType.TRANSFER, onDismiss = { dialog = null }) {
                repo.updateBill(bill.copy(accountId = it))
                dialog = null
            }
        }
    }
}

@Composable
fun AmountField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Column(modifier) {
        Muted(label, Modifier.padding(bottom = 4.dp))
        WalletField(value, onChange, Modifier.fillMaxWidth(), numeric = true)
    }
}
