package com.xern.xrnwallet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.data.Stats
import com.xern.xrnwallet.data.inRange
import com.xern.xrnwallet.data.totals
import com.xern.xrnwallet.domain.Period
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

val pieColors: List<Color> = listOf(
    Color(0xFF5B9BEB), Color(0xFFF29048), Color(0xFFEE4C5F), Color(0xFFFFD61F),
    Color(0xFFD94CB5), Color(0xFF83D12F), Color(0xFF2FD9C7), Color(0xFF8E7CF0),
    Color(0xFFB08968), Color(0xFF9AA0A6),
)

data class ReportFilter(
    val tags: Set<Long> = emptySet(),
    val ledgers: Set<Long> = emptySet(),
    val accounts: Set<Long> = emptySet(),
) {
    val active: Boolean get() = tags.isNotEmpty() || ledgers.isNotEmpty() || accounts.isNotEmpty()

    fun matches(bill: Bill, snapshot: Snapshot): Boolean =
        (ledgers.isEmpty() || bill.ledgerId in ledgers) &&
            (accounts.isEmpty() || bill.accountId in accounts || bill.toAccountId in accounts) &&
            (tags.isEmpty() || snapshot.tagIdsOf(bill.id).any { it in tags })
}

@Composable
fun SwitchPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text("$text ⇌", color = Color.Black, fontSize = 14.sp) }
}

/** Which way round each card is turned. Kept outside the pages, so turning a page leaves them as they are. */
@Stable
private class ReportView {
    var trendType by mutableIntStateOf(BillType.EXPENSE)
    var rankType by mutableIntStateOf(BillType.EXPENSE)
    var topLevel by mutableStateOf(true)
    var topType by mutableIntStateOf(BillType.EXPENSE)
    var expanded by mutableStateOf(false)
}

@Composable
fun ReportsScreen() {
    val s = snap()
    val settings = prefs()
    val ui = LocalAppUi.current
    val today = LocalDate.now()
    val rules = settings.periodRules
    val pages = rememberEndlessPages(ui.reportPeriod, Period::shifted)
    val period = pages.current
    LaunchedEffect(period) { ui.reportPeriod = period }
    var pick by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(ReportFilter()) }
    val view = remember { ReportView() }
    val bills = remember(s, filter) { s.bills.filter { filter.matches(it, s) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp)) {
            PeriodSwitcher(
                period.label(rules, today),
                onPrevious = { pages.turn(-1) },
                onNext = { pages.turn(1) },
                onPick = { pick = true },
                modifier = Modifier.align(Alignment.Center).widthIn(max = 250.dp),
            )
            Text(
                "Filter",
                Modifier.align(Alignment.CenterEnd).clickable { showFilter = true }.padding(8.dp),
                color = if (filter.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        EndlessPager(pages, Modifier.weight(1f)) { ReportPage(it, bills, view) }
    }

    if (pick) {
        PeriodDialog(period, allowCustom = true, onDismiss = { pick = false }) {
            pages.jumpTo(it)
            pick = false
        }
    }
    if (showFilter) {
        ReportFilterDialog(filter, onDismiss = { showFilter = false }) {
            filter = it
            showFilter = false
        }
    }
}

@Composable
private fun ReportPage(period: Period, bills: List<Bill>, view: ReportView) {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    val today = LocalDate.now()
    val rules = prefs().periodRules
    val range = period.range(rules)
    val inPeriod = remember(bills, range) { bills.inRange(range) }
    val totals = remember(inPeriod) { inPeriod.totals() }
    val total = if (view.trendType == BillType.EXPENSE) totals.expense else totals.income
    val units = Stats.averageUnits(period, range, today)
    val average = Stats.average(total, units)
    val points = remember(bills, period, range, view.trendType) { Stats.trend(bills, period, range, view.trendType) }
    val ranks = remember(s, inPeriod, view.rankType, view.topLevel) { Stats.ranking(s, inPeriod, view.rankType, view.topLevel) }
    val top = remember(inPeriod, view.topType) { Stats.top(inPeriod, view.topType) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Panel {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${if (view.trendType == BillType.EXPENSE) "Expenses" else "Income"}: ${f.money(total)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Muted("Average: ${if (total == 0L) "" else f.money(average)}", Modifier.padding(top = 2.dp))
                    }
                    SwitchPill(if (view.trendType == BillType.EXPENSE) "Expenses" else "Income") {
                        view.trendType = if (view.trendType == BillType.EXPENSE) BillType.INCOME else BillType.EXPENSE
                    }
                }
                LineChart(points, average.takeIf { total != 0L }, Modifier.padding(top = 12.dp))
            }
        }
        Panel {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Category Ranking", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    SwitchPill(if (view.rankType == BillType.EXPENSE) "Expenses" else "Income") {
                        view.rankType = if (view.rankType == BillType.EXPENSE) BillType.INCOME else BillType.EXPENSE
                        view.expanded = false
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    SwitchPill(if (view.topLevel) "Top level category" else "All categories") { view.topLevel = !view.topLevel }
                }
                if (ranks.isEmpty()) {
                    EmptyNote("Please add a bill")
                } else {
                    Pie(ranks.map { it.share.toFloat() }, Modifier.fillMaxWidth().height(230.dp))
                    val shown = if (view.expanded) ranks else ranks.take(5)
                    shown.forEachIndexed { index, rank ->
                        RankRow(index, rank, view.rankType) {
                            val id = rank.category?.id ?: 0
                            nav.navigate(
                                "filter?category=$id&start=${range.start.toEpochDay()}" +
                                    "&end=${range.endInclusive.toEpochDay()}&type=$view.rankType",
                            )
                        }
                    }
                    if (ranks.size > 5) {
                        Box(Modifier.fillMaxWidth().clickable { view.expanded = !view.expanded }.padding(8.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                if (view.expanded) Icons.Filled.KeyboardDoubleArrowUp else Icons.Filled.KeyboardDoubleArrowDown,
                                if (view.expanded) "Show fewer" else "Show all",
                            )
                        }
                    }
                }
            }
        }
        Panel {
            Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bill Amount Ranking TOP 10", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    SwitchPill(if (view.topType == BillType.EXPENSE) "Expenses" else "Income") {
                        view.topType = if (view.topType == BillType.EXPENSE) BillType.INCOME else BillType.EXPENSE
                    }
                }
                if (top.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Please add a bill",
                            Modifier.clickable { nav.addBill() },
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                } else {
                    top.forEachIndexed { index, bill ->
                        BillRow(bill, s, showDate = true, rank = index + 1) { nav.openBill(bill.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(index: Int, rank: Stats.Rank, type: Int, onClick: () -> Unit) {
    val f = fmt()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bubble(rank.category?.icon ?: "⭐", 48.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row {
                Text("${index + 1} ${rank.category?.name ?: "Uncategorised"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(f.money(if (type == BillType.EXPENSE) -rank.cents else rank.cents), style = MaterialTheme.typography.bodyLarge)
            }
            Row {
                Muted(percent(rank.share), Modifier.weight(1f))
                Muted(billCount(rank.count))
            }
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((rank.share / 100).toFloat().coerceIn(0.01f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(pieColors[index % pieColors.size]),
                )
            }
        }
    }
}

/** One decimal place, with a trailing zero dropped: 15%, 34.9%. */
fun percent(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded == floor(rounded)) "${rounded.toLong()}%" else "$rounded%"
}

private fun niceStep(maximum: Double): Double {
    if (maximum <= 0) return 100.0
    val rough = maximum / 5
    val magnitude = 10.0.pow(floor(log10(rough)))
    val scaled = rough / magnitude
    val nice = when {
        scaled <= 1 -> 1.0
        scaled <= 2 -> 2.0
        scaled <= 5 -> 5.0
        else -> 10.0
    }
    return nice * magnitude
}

@Composable
private fun LineChart(points: List<Stats.Point>, averageCents: Long?, modifier: Modifier = Modifier) {
    val font = rememberCanvasTypeface()
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val maxUnits = max((points.maxOfOrNull { it.cents } ?: 0L) / 100.0, 1.0)
    val step = niceStep(maxUnits)
    val rows = kotlin.math.ceil(maxUnits / step).toInt().coerceAtLeast(5)
    val top = step * rows
    val height = 200.dp
    Row(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.width(44.dp).fillMaxHeight()) {
            val paint = android.graphics.Paint().apply {
                color = label.toArgb()
                textSize = 11.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
                typeface = font
            }
            val chart = size.height - 26.dp.toPx()
            (0..rows).forEach { row ->
                val y = chart - chart * row / rows + 4.dp.toPx()
                val value = step * row
                val text = if (value >= 1000) "${(value / 1000).toBigDecimal().stripTrailingZeros().toPlainString()}k" else value.toLong().toString()
                drawContext.canvas.nativeCanvas.drawText(text, size.width - 6.dp.toPx(), y + 4.dp.toPx(), paint)
            }
        }
        val slot = 38.dp
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(slot * points.size.coerceAtLeast(9)).fillMaxHeight()) {
                val chart = size.height - 26.dp.toPx()
                val offset = 4.dp.toPx()
                val slotPx = slot.toPx()
                fun yOf(units: Double) = chart - (units / top * chart).toFloat() + offset
                (0..rows).forEach { row ->
                    val y = chart - chart * row / rows + offset
                    drawLine(
                        grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                    )
                }
                if (averageCents != null) {
                    val y = yOf(averageCents / 100.0)
                    drawLine(
                        line.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 8f)),
                    )
                }
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = slotPx * index + slotPx / 2
                    val y = yOf(point.cents / 100.0)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, line, style = Stroke(2.dp.toPx()))
                val paint = android.graphics.Paint().apply {
                    color = label.toArgb()
                    textSize = 11.sp.toPx()
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = font
                }
                points.forEachIndexed { index, point ->
                    val x = slotPx * index + slotPx / 2
                    if (point.cents > 0) drawCircle(line, 3.dp.toPx(), Offset(x, yOf(point.cents / 100.0)))
                    drawContext.canvas.nativeCanvas.drawText(point.label, x, size.height - 6.dp.toPx(), paint)
                }
            }
        }
    }
}

@Composable
private fun Pie(shares: List<Float>, modifier: Modifier = Modifier) {
    val font = rememberCanvasTypeface()
    val label = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val radius = size.minDimension * 0.38f
        val center = Offset(size.width / 2, size.height / 2)
        val box = Size(radius * 2, radius * 2)
        val origin = Offset(center.x - radius, center.y - radius)
        val total = shares.sum().coerceAtLeast(0.01f)
        var angle = 0f
        val paint = android.graphics.Paint().apply {
            color = label.toArgb()
            textSize = 12.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = font
        }
        shares.forEachIndexed { index, share ->
            val sweep = share / total * 360f
            drawArc(pieColors[index % pieColors.size], angle, sweep, true, origin, box)
            if (share >= 2f) {
                val middle = Math.toRadians((angle + sweep / 2).toDouble())
                val x = center.x + (radius + 26.dp.toPx()) * cos(middle).toFloat()
                val y = center.y + (radius + 18.dp.toPx()) * sin(middle).toFloat() + 4.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(percent(share.toDouble()), x, y, paint)
            }
            angle += sweep
        }
    }
}

@Composable
private fun ReportFilterDialog(initial: ReportFilter, onDismiss: () -> Unit, onApply: (ReportFilter) -> Unit) {
    val s = snap()
    var filter by remember { mutableStateOf(initial) }
    DialogCard(onDismiss) {
        Text("Filter", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            ChipGroup("Tags", s.tags.map { it.id to it.name }, filter.tags) { filter = filter.copy(tags = it) }
            ChipGroup("Ledgers", s.ledgers.map { it.id to it.name }, filter.ledgers) { filter = filter.copy(ledgers = it) }
            ChipGroup("Accounts", s.accounts.map { it.id to it.name.trim() }, filter.accounts) { filter = filter.copy(accounts = it) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GhostButton("Reset", Modifier.weight(1f)) { filter = ReportFilter() }
            PrimaryButton("Confirm", Modifier.weight(1f)) { onApply(filter) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(title: String, options: List<Pair<Long, String>>, chosen: Set<Long>, onChange: (Set<Long>) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(title, Modifier.padding(bottom = 6.dp))
        if (options.isEmpty()) Muted("None yet")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (id, name) ->
                Pill(name, id in chosen) { onChange(if (id in chosen) chosen - id else chosen + id) }
            }
        }
    }
}
