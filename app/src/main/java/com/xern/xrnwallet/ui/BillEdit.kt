package com.xern.xrnwallet.ui

import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.DonutLarge
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
import com.xern.xrnwallet.data.Snapshot
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

data class BillStart(val accountId: Long?, val currency: String)

/**
 * Where a new bill starts: on the account the last bill used, or else the first open one, in that
 * account's currency as picking it would set, or in [base] with no account. Otherwise 20 typed for a
 * US card would leave it as A$20.
 */
fun Snapshot.billStart(base: String): BillStart {
    val account = bills.firstOrNull { it.accountId != null && it.type != BillType.TRANSFER }?.accountId?.let(accountById::get)
        ?: accounts.firstOrNull { !it.archived }
    return BillStart(account?.id, account?.currency ?: base)
}

@Composable
fun BillEditScreen(id: Long, date: Long, initialType: Int) {
    val s = snap()
    val settings = prefs()
    val f = fmt()
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
    val start = remember { s.billStart(settings.baseCurrency) }
    var accountId by remember { mutableStateOf(if (existing != null) existing.accountId else start.accountId) }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.let { Calculator.format(Money.fromCents(it.amountCents)) } ?: "0") }
    var currency by remember { mutableStateOf(existing?.currency ?: start.currency) }
    var feeCents by remember { mutableLongStateOf(existing?.feeCents ?: 0) }
    var photo by remember { mutableStateOf(existing?.photo) }
    var at by remember {
        mutableStateOf(
            existing?.dateTime
                ?: if (date != NO_DATE) LocalDate.ofEpochDay(date).atTime(LocalTime.now()) else LocalDateTime.now(),
        )
    }
    var tagIds by remember { mutableStateOf(if (existing != null) s.tagIdsOf(existing.id) else emptyList()) }
    var inBudget by remember { mutableStateOf(existing?.inBudget ?: true) }
    val undo = LocalBillUndo.current
    val budgetTick = rememberTick()

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

    // The page closes only after the write, so a second tap can arrive first and record the bill twice.
    var saving by remember { mutableStateOf(false) }

    fun save() {
        if (saving) return
        val cents = Calculator.cents(amount)
        if (cents == null && Calculator.evaluate(amount) != null) {
            toast("That amount is too large")
            return
        }
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
            inBudget = inBudget,
            recurringId = existing?.recurringId,
        ).let { it.copy(toAmountCents = s.fixedAmount(it, before = existing)) }
        saving = true
        scope.launch {
            repo.saveBill(bill, tagIds)
            nav.leavePage()
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
            IconButton({ nav.leavePage() }) { Icon(Icons.Filled.Close, "Close") }
            Spacer(Modifier.weight(1f))
            // A bill opens straight into this page, so what its details page offered is here: the
            // stored bill again for today, or gone, each with Undo on the page underneath.
            if (existing != null) {
                IconButton({
                    undo.copyToToday(existing, s.tagIdsOf(existing.id))
                    nav.leavePage()
                }) { Icon(Icons.Filled.ContentCopy, "Add again today") }
                IconButton({
                    undo.delete(existing, s.tagIdsOf(existing.id))
                    nav.leavePage()
                }) { Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFFF3B30)) }
            }
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
                val chips = remember { List(3) { MutableInteractionSource() } }
                Box(
                    Modifier
                        .widthIn(max = 200.dp)
                        .springPress(chips[0])
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(chips[0], ripple()) { dialog = "account" }
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
                        .springPress(chips[1])
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (tagIds.isEmpty()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                        .clickable(chips[1], ripple()) { dialog = "tags" }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Label,
                        "Tags",
                        Modifier.size(20.dp),
                        tint = if (tagIds.isEmpty()) MaterialTheme.colorScheme.onSurface else Color.Black,
                    )
                }
                Row(
                    Modifier
                        .padding(start = 8.dp)
                        .springPress(chips[2])
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(chips[2], ripple()) { budgetTick(); inBudget = !inBudget }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics { contentDescription = if (inBudget) "Counted in the budget" else "Left out of the budget" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.DonutLarge, null, Modifier.size(18.dp))
                    Text(
                        "Budget",
                        Modifier.padding(start = 6.dp),
                        color = if (inBudget) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (inBudget) null else TextDecoration.LineThrough,
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
                            "Attached photo",
                            Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).clickable { dialog = "photo" },
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
                        AnimatedContent(
                            amount,
                            transitionSpec = {
                                val up = targetState.length >= initialState.length
                                (slideInVertically(tween(140)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(140)))
                                    .togetherWith(slideOutVertically(tween(110)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(110)))
                                    .using(SizeTransform(clip = false))
                            },
                            contentAlignment = Alignment.CenterEnd,
                            label = "amount",
                        ) { shown ->
                            Text(
                                shown.replace('.', settings.moneyFormat.decimalChar),
                                fontSize = if (shown.length > 12) 18.sp else 26.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
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
        "photo" -> PhotoDialog(photo, onDismiss = { dialog = null }) {
            photo = null
            dialog = null
        }
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
            feeCents = f.parse(it, currency) ?: 0
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
    val tick = rememberHaptic(android.view.HapticFeedbackConstants.CLOCK_TICK)
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            val source = remember { MutableInteractionSource() }
            val chosen = category.id == selected
            val bounce = remember { Animatable(1f) }
            var wasChosen by remember { mutableStateOf(chosen) }
            LaunchedEffect(chosen) {
                // Only a change bounces: a page opening on its category stays still.
                if (chosen && !wasChosen) {
                    bounce.animateTo(1.14f, spring(stiffness = Spring.StiffnessHigh))
                    bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                }
                wasChosen = chosen
            }
            val ring by animateColorAsState(
                if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(160),
                label = "ring",
            )
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(source, null) { tick(); onPick(category) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .springPress(source, depth = 0.88f)
                        .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(bubbleColour(category.icon))
                        .border(2.dp, ring, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Glyph(category.icon, 36.dp) }
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

/**
 * The base currency plus the billing currencies set up in Currencies, for bills and accounts alike.
 * Any other has no rate, so each unit would count as one of the base.
 */
fun Snapshot.currencyChoices(base: String): List<String> = listOf(base) + rates.map { it.code }.filter { it != base }

@Composable
fun CurrencyDialog(onDismiss: () -> Unit, selected: String? = null, onPick: (String) -> Unit) {
    val s = snap()
    val settings = prefs()
    val options = s.currencyChoices(settings.baseCurrency)
    OptionDialog("Currency", options, selected, { "${Currencies.info(it).flag}  $it  ${Currencies.info(it).name}" }, onDismiss, onPick)
}

/** The attached photo, as large as the screen allows, with a way to take it off the bill. */
@Composable
private fun PhotoDialog(path: String?, onDismiss: () -> Unit, onRemove: () -> Unit) {
    val photo = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    DialogCard(onDismiss) {
        if (photo != null) {
            Image(photo, "Attached photo", Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.FillWidth)
        } else {
            Muted("The photo can't be read.")
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onRemove) { Text("Remove photo", color = Color(0xFFFF3B30)) }
            Spacer(Modifier.weight(1f))
            TextButton(onDismiss) { Text("Close") }
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
