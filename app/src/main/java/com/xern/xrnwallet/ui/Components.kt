package com.xern.xrnwallet.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Repository
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.data.totals
import com.xern.xrnwallet.domain.Dates
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val CardShape = RoundedCornerShape(18.dp)

/**
 * Closes the current page. A second tap lands while the page is still sliding away, and must not close
 * Home as well, which would leave a blank app with no way back.
 */
fun NavHostController.leavePage() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
fun Page(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val nav = LocalNav.current
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        TopBar(title, onBack ?: { nav.leavePage() }, actions)
        content()
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Back", Modifier.size(22.dp))
            }
        }
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** A scrolling body with the standard side gutter. */
@Composable
fun ColumnScope.ScrollBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Column(content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 6.dp, bottom = 2.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text,
        modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Divider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
}

@Composable
fun ListRow(
    title: String,
    value: String? = null,
    subtitle: String? = null,
    chevron: Boolean = true,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Muted(subtitle, Modifier.padding(top = 3.dp))
        }
        if (value != null) {
            Text(
                value,
                Modifier.padding(start = 12.dp).widthIn(max = 200.dp),
                color = valueColor,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (chevron && onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp))
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Muted(subtitle, Modifier.padding(top = 3.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.Black,
            ),
        )
    }
}

/** Draws a stored icon: the app's artwork when it has a drawing, otherwise the text itself. */
@Composable
fun Glyph(icon: String, size: Dp, tint: Color? = null) {
    val vector = Art.vector(icon)
    if (vector != null) {
        Icon(vector, null, Modifier.size(size), tint = tint ?: Art.tint(icon) ?: LocalContentColor.current)
    } else {
        Text(Art.text(icon), fontSize = with(LocalDensity.current) { size.toSp() }, textAlign = TextAlign.Center)
    }
}

/** The icon's own colour, faintly, so a list of categories reads by colour as well as by shape. */
@Composable
fun softTint(icon: String): Color =
    Art.tint(icon)?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceVariant

/** An icon in a circle. An account passes its own [background], and its drawing turns white to suit. */
@Composable
fun Bubble(icon: String, size: Dp = 56.dp, background: Color? = null) {
    Box(
        Modifier.size(size).clip(CircleShape).background(background ?: softTint(icon)),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, size * 0.56f, if (background != null) Color.White else null)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier.height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier.height(50.dp),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(text, fontSize = 17.sp)
    }
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

/** Underlined tab label used for Expenses / Income / Transfer style switches. */
@Composable
fun UnderlineTabs(tabs: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, label ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(44.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index == selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

@Composable
fun Ring(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 11.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tint = MaterialTheme.colorScheme.primary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val arc = Size(this.size.width - width, this.size.height - width)
            val origin = Offset(width / 2, width / 2)
            drawArc(track, 0f, 360f, false, origin, arc, style = Stroke(width))
            drawArc(
                tint, -90f, 360f * fraction.coerceIn(0f, 1f), false, origin, arc,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
        center()
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Muted(text)
    }
}

/** Category, account and note on one row; the same row is used by every list of bills. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun BillRow(bill: Bill, snapshot: Snapshot, showDate: Boolean = false, showAccount: Boolean? = null, rank: Int? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val settings = prefs()
    val f = fmt()
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Bubble(if (bill.type == BillType.TRANSFER) "🪙" else snapshot.categoryById[bill.categoryId]?.icon ?: "⭐", 50.dp)
            if (rank != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Text(rank.toString(), color = Color.Black, fontSize = 11.sp) }
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (bill.type == BillType.TRANSFER) {
                val from = snapshot.accountById[bill.accountId]?.name ?: "No account"
                val to = snapshot.accountById[bill.toAccountId]?.name ?: "No account"
                Text("$from » $to", style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (bill.note.isNotEmpty()) Muted(bill.note, maxLines = 1)
            } else {
                Text(snapshot.categoryLabel(bill.categoryId), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                val account = snapshot.accountById[bill.accountId]?.name?.trim()
                    .takeIf { showAccount ?: settings.showAccountOnCard }
                val detail = listOfNotNull(account, bill.note.ifEmpty { null }).joinToString("  ")
                if (detail.isNotEmpty()) Muted(detail, maxLines = 2)
            }
            val tags = snapshot.tagsOf(bill.id)
            if (settings.showTagsOnCard && tags.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { TagChip(it.name, Color(it.color)) }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(start = 12.dp),
        ) {
            val color = when (bill.type) {
                BillType.EXPENSE -> f.expenseColor
                BillType.INCOME -> f.incomeColor
                else -> MaterialTheme.colorScheme.onSurface
            }
            val cents = when (bill.type) {
                BillType.EXPENSE -> -bill.amountCents
                else -> bill.amountCents
            }
            Text(
                if (bill.type == BillType.TRANSFER) f.money(cents, bill.currency) else f.signed(cents, bill.currency),
                color = color,
                style = MaterialTheme.typography.bodyLarge,
            )
            Muted(
                if (showDate) Dates.full(bill.dateTime) else Dates.time(bill.dateTime),
            )
        }
    }
}

@Composable
fun TagChip(name: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.22f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(name, color = color, fontSize = 13.sp) }
}

@Composable
fun DayHeader(date: LocalDate, bills: List<Bill>) {
    val f = fmt()
    val totals = bills.totals()
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val prefix = if (date == LocalDate.now()) "Today " else ""
        Text(prefix + Dates.dayHeader(date), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (totals.expense != 0L) {
            Arrow(f.expenseArrow, f.expenseArrowColor)
            Text(" ${f.money(totals.expense)}")
        }
        if (totals.income != 0L) {
            Spacer(Modifier.width(8.dp))
            Arrow(f.incomeArrow, f.incomeArrowColor)
            Text(" ${f.money(totals.income)}")
        }
    }
}

/** Deletes and copies that can be taken back for a few seconds, instead of asking first. */
class BillUndo(private val repo: Repository, private val scope: CoroutineScope) {
    val host = SnackbarHostState()

    fun delete(bill: Bill, tagIds: List<Long>) {
        repo.deleteBill(bill.id)
        offer("Bill deleted") { repo.saveBill(bill, tagIds) }
    }

    fun copyToToday(bill: Bill, tagIds: List<Long>) {
        scope.launch {
            val copy = bill.copy(id = 0, atMillis = System.currentTimeMillis(), recurringId = null)
            val id = repo.saveBill(copy, tagIds)
            offer("Added again for today") { repo.deleteBill(id) }
        }
    }

    private fun offer(message: String, undo: suspend () -> Unit) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            if (host.showSnackbar(message, "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo()
        }
    }
}

val LocalBillUndo = staticCompositionLocalOf<BillUndo> { error("No undo") }

@Composable
fun UndoBar(undo: BillUndo, modifier: Modifier = Modifier) {
    SnackbarHost(undo.host, modifier) {
        Snackbar(
            it,
            shape = RoundedCornerShape(14.dp),
            containerColor = Color(0xFF2C2C2F),
            contentColor = Color.White,
            actionColor = MaterialTheme.colorScheme.primary,
        )
    }
}

/** A small buzz, because a gesture has no button to show it worked. Follows the Haptic feedback setting. */
@Composable
fun rememberTick(): () -> Unit {
    val view = LocalView.current
    val on = prefs().hapticsOn
    return remember(view, on) { { if (on) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) } }
}

/** Keys and buttons sink slightly under a finger. */
@Composable
fun pressedScale(source: InteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) 0.94f else 1f, tween(90), label = "press").value
}

private val DeleteRed = Color(0xFFE5342B)

/** A bill that answers to a long press as well as a tap. A swipe belongs to the page around it. */
@Composable
private fun ActionBillRow(bill: Bill, snapshot: Snapshot, showAccount: Boolean?, onOpen: (Bill) -> Unit) {
    val undo = LocalBillUndo.current
    val nav = LocalNav.current
    val tick = rememberTick()
    var menu by remember { mutableStateOf(false) }
    val tags = snapshot.tagIdsOf(bill.id)
    Box {
        BillRow(bill, snapshot, showAccount = showAccount, onLongClick = { tick(); menu = true }) { onOpen(bill) }
        DropdownMenu(menu, { menu = false }, offset = DpOffset(16.dp, 0.dp)) {
            DropdownMenuItem(
                { Text("Edit") },
                { menu = false; nav.navigate("edit?id=${bill.id}") },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
            )
            DropdownMenuItem(
                { Text("Add again today") },
                { menu = false; undo.copyToToday(bill, tags) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
            )
            DropdownMenuItem(
                { Text("Delete", color = DeleteRed) },
                { menu = false; undo.delete(bill, tags) },
                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = DeleteRed) },
            )
        }
    }
}

/** Bills grouped into day cards, newest first. */
@Composable
fun BillDays(bills: List<Bill>, snapshot: Snapshot, showAccount: Boolean? = null, onOpen: (Bill) -> Unit) {
    bills.groupBy { it.date }.forEach { (date, group) ->
        Column {
            DayHeader(date, group)
            Panel {
                group.forEachIndexed { index, bill ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                    key(bill.id) { ActionBillRow(bill, snapshot, showAccount, onOpen) }
                }
            }
        }
    }
}

@Composable
fun DialogCard(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

@Composable
fun CancelConfirmRow(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        IconAction(Icons.Filled.Close, "Cancel", onCancel)
        IconAction(Icons.Filled.Check, "Confirm", onConfirm)
    }
}

@Composable
fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, Modifier.size(26.dp)) }
}

@Composable
fun InputDialog(
    title: String,
    initial: String = "",
    numeric: Boolean = false,
    placeholder: String = "",
    below: (@Composable () -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    DialogCard(onDismiss) {
        Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        WalletField(text, { text = it }, Modifier.fillMaxWidth(), placeholder, numeric)
        below?.invoke()
        CancelConfirmRow(onDismiss) { onConfirm(text.trim()) }
    }
}

@Composable
fun WalletField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    singleLine: Boolean = true,
    password: Boolean = false,
    plain: KeyboardType? = null,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        shape = RoundedCornerShape(10.dp),
        keyboardOptions = when {
            numeric -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
            password -> KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
            plain != null -> KeyboardOptions(keyboardType = plain, autoCorrectEnabled = false)
            else -> KeyboardOptions.Default
        },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirm: String = "Delete",
    danger: Boolean = true,
    dismiss: String = "Cancel",
    onOutside: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onOutside ?: onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) Color(0xFFFF3B30) else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss, color = MaterialTheme.colorScheme.onSurface) }
        },
    )
}

@Composable
fun <T> OptionDialog(
    title: String,
    options: List<T>,
    selected: T? = null,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    DialogCard(onDismiss) {
        Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (option == selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { onPick(option) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label(option), Modifier.weight(1f))
                    if (option == selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * Long press and drag to reorder. All cells share one parent, so a cell keeps its gesture while it
 * moves between rows of the grid.
 */
@Composable
fun <T> ReorderGrid(
    items: List<T>,
    columns: Int,
    keyOf: (T) -> Any,
    onMove: (List<T>) -> Unit,
    /** The item whose row has [expansion] opened beneath it. */
    expandedKey: Any? = null,
    expansion: @Composable () -> Unit = {},
    cell: @Composable (T) -> Unit,
) {
    var order by remember(items) { mutableStateOf(items) }
    var dragged by remember { mutableStateOf<Any?>(null) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var cellSize by remember { mutableStateOf(IntSize(1, 1)) }
    Layout(
        content = {
            order.forEach { item ->
                val id = keyOf(item)
                key(id) {
                    Box(
                        Modifier
                            .onSizeChanged { cellSize = it }
                            .zIndex(if (dragged == id) 1f else 0f)
                            .graphicsLayer {
                                if (dragged == id) {
                                    translationX = dx
                                    translationY = dy
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                }
                            }
                            .pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragged = id
                                        dx = 0f
                                        dy = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dx += amount.x
                                        dy += amount.y
                                        val index = order.indexOfFirst { keyOf(it) == id }
                                        val column = index % columns
                                        val row = index / columns
                                        val nextColumn = (column + (dx / cellSize.width).roundToInt()).coerceIn(0, columns - 1)
                                        val nextRow = (row + (dy / cellSize.height).roundToInt()).coerceAtLeast(0)
                                        val target = (nextRow * columns + nextColumn).coerceIn(order.indices)
                                        if (target != index) {
                                            order = order.toMutableList().apply { add(target, removeAt(index)) }
                                            dx -= (target % columns - column) * cellSize.width
                                            dy -= (target / columns - row) * cellSize.height
                                        }
                                    },
                                    onDragEnd = {
                                        dragged = null
                                        onMove(order)
                                    },
                                    onDragCancel = {
                                        dragged = null
                                        order = items
                                    },
                                )
                            },
                    ) { cell(item) }
                }
            }
            if (expandedKey != null) Box { expansion() }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth / columns
        val cells = if (expandedKey != null) measurables.dropLast(1) else measurables
        val panel = if (expandedKey != null) measurables.last().measure(Constraints.fixedWidth(constraints.maxWidth)) else null
        val panelRow = order.indexOfFirst { keyOf(it) == expandedKey } / columns
        val rows = cells.map { it.measure(Constraints.fixedWidth(width)) }.chunked(columns)
        val heights = rows.map { row -> row.maxOf { it.height } }
        layout(constraints.maxWidth, heights.sum() + (panel?.height ?: 0)) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { column, placeable -> placeable.placeRelative(column * width, y) }
                y += heights[rowIndex]
                if (panel != null && rowIndex == panelRow) {
                    panel.placeRelative(0, y)
                    y += panel.height
                }
            }
        }
    }
}

@Composable
fun Selectable(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .clip(CardShape)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardShape) else Modifier,
            )
            .clickable(onClick = onClick),
        content = content,
    )
}

fun billCount(count: Int): String = if (count == 1) "1 bill" else "$count bills"
