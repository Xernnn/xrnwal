package com.xern.xrnwallet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.runtime.MutableState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.xern.xrnwallet.domain.Dates
import java.time.format.TextStyle
import java.util.Locale
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Insights
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

/**
 * The colours categories are drawn in, by rank: eight hues in an order that keeps neighbours apart, for
 * colour-blind eyes too, each stepped for its theme. They are the dataviz reference palette's, checked
 * with its validator in both themes: lightness and colour, neighbour separation, and contrast with the
 * page (three light-theme hues sit below 3:1 on white, which every chart offsets by naming its bands).
 * A ninth category on takes the grey of "Other" rather than repeating a hue.
 */
class CategoryColours(val hues: List<Color>, val other: Color) {
    fun ranked(index: Int): Color = hues.getOrElse(index) { other }
}

val categoryColoursDark = CategoryColours(
    listOf(
        Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
        Color(0xFFD55181), Color(0xFF0CA30C), Color(0xFF9085E9), Color(0xFFE66767),
    ),
    Color(0xFF8E9399),
)

val categoryColoursLight = CategoryColours(
    listOf(
        Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
        Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF6250D6), Color(0xFFE34948),
    ),
    Color(0xFFA3A8AE),
)

@Composable
fun categoryColours(): CategoryColours =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) categoryColoursDark else categoryColoursLight

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
        // The monthly budget, when it applies to what's shown: not to a filtered few.
        val budget = if (filter.active || !settings.budgetEnabled) null else s.budgets.firstOrNull { it.categoryId == null }?.amountCents
        EndlessPager(pages, Modifier.weight(1f)) { ReportPage(it, bills, view, budget) }
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
private fun ReportPage(period: Period, bills: List<Bill>, view: ReportView, budget: Long?) {
    val s = snap()
    val f = fmt()
    val nav = LocalNav.current
    val repo = LocalRepo.current
    val settings = prefs()
    val today = LocalDate.now()
    val rules = settings.periodRules
    val range = period.range(rules)
    val inPeriod = remember(bills, range) { bills.inRange(range) }
    val totals = remember(inPeriod) { inPeriod.totals() }
    val total = if (view.trendType == BillType.EXPENSE) totals.expense else totals.income
    val units = Stats.averageUnits(period, range, today)
    val average = Stats.average(total, units)
    val points = remember(bills, period, range, view.trendType) { Stats.trend(bills, period, range, view.trendType) }
    val ranks = remember(s, inPeriod, view.rankType, view.topLevel) { Stats.ranking(s, inPeriod, view.rankType, view.topLevel) }
    val top = remember(inPeriod, view.topType) { Stats.top(inPeriod, view.topType) }
    val months = remember(period, range, rules) { Insights.window(period, range, rules) }
    val flows = remember(bills, months, rules) { Insights.flows(bills, months, rules) }
    val mix = remember(s, bills, months, rules) { Insights.mix(s, bills, months, rules) }
    val focus = when {
        period !is Period.Year -> months.last()
        period.year == today.year -> rules.monthOf(today).let { if (it.year == period.year) it else months.last() }
        else -> months.last()
    }

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
                    FitToggle(settings.trendFit) { repo.store.update { it.copy(trendFit = !it.trendFit) } }
                    SwitchPill(if (view.trendType == BillType.EXPENSE) "Expenses" else "Income") {
                        view.trendType = if (view.trendType == BillType.EXPENSE) BillType.INCOME else BillType.EXPENSE
                    }
                }
                LineChart(
                    points,
                    average.takeIf { total != 0L },
                    monthly = period is Period.Year,
                    fit = settings.trendFit,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (period !is Period.Custom) PaceCard(period, bills, rules, today, budget)
        DailyCard(range, bills, today, rules.firstDayOfWeek)
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
                    EmptyNote("No bills")
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
        FlowCard(flows, focus)
        MixCard(mix, months, focus)
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
                        .background(categoryColours().ranked(index)),
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

/**
 * The trend card's switch between its two looks: lit when the chart is squeezed to fit the card,
 * dark when it scrolls sideways at a readable width per point.
 */
@Composable
private fun FitToggle(fit: Boolean, onToggle: () -> Unit) {
    val lit = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(if (fit) lit else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.UnfoldLess,
            if (fit) "Scroll the chart" else "Fit the chart",
            Modifier.size(20.dp).rotate(90f),
            tint = if (fit) Color.Black else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A squeezed chart's label: a day's number, or its month's name on the 1st; a month's name. */
private fun fitLabel(point: Stats.Point, monthly: Boolean): String =
    if (!monthly && point.date.dayOfMonth == 1) point.date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) else point.label

/** What a chosen point reads: "Nov 12" or "Oct 2026", and its figure. */
private fun pointTitle(point: Stats.Point, monthly: Boolean): String =
    if (monthly) "${point.label} ${point.date.year}" else Dates.short(point.date)

/**
 * The trend, as a line over a scale of [rows] steps. Scrolling, each point gets a column wide enough
 * for its label. Fitted, every point shares the card's width and only some are labelled; tapping a
 * point, or holding and dragging along the line, shows its figure either way.
 */
@Composable
private fun LineChart(points: List<Stats.Point>, averageCents: Long?, monthly: Boolean, fit: Boolean, modifier: Modifier = Modifier) {
    val font = rememberCanvasTypeface()
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val maxUnits = max((points.maxOfOrNull { it.cents } ?: 0L) / 100.0, 1.0)
    val step = niceStep(maxUnits)
    val rows = kotlin.math.ceil(maxUnits / step).toInt().coerceAtLeast(5)
    val scale = Scale(step, rows)
    val selected = remember(points) { mutableStateOf<Int?>(null) }
    Row(modifier.fillMaxWidth().height(200.dp)) {
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
        if (fit) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val room = (maxWidth / if (monthly) 24.dp else 26.dp).toInt()
                val shown = remember(points, room) { Stats.fitLabels(points.map { it.date }, monthly, room).toSet() }
                Plot(
                    points, averageCents, scale, monthly, selected,
                    slot = maxWidth / points.size.coerceAtLeast(1),
                    labelOf = { if (it in shown) fitLabel(points[it], monthly) else null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            val slot = 38.dp
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Plot(
                    points, averageCents, scale, monthly, selected,
                    slot = slot,
                    labelOf = { points[it].label },
                    modifier = Modifier.width(slot * points.size.coerceAtLeast(9)).fillMaxHeight(),
                )
            }
        }
    }
}

private class Scale(val step: Double, val rows: Int) {
    val top: Double get() = step * rows
}

@Composable
private fun Plot(
    points: List<Stats.Point>,
    averageCents: Long?,
    scale: Scale,
    monthly: Boolean,
    selected: MutableState<Int?>,
    slot: Dp,
    labelOf: (Int) -> String?,
    modifier: Modifier,
) {
    val f = fmt()
    val font = rememberCanvasTypeface()
    val line = MaterialTheme.colorScheme.primary
    val grid = line.copy(alpha = 0.28f)
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val bubble = MaterialTheme.colorScheme.surfaceVariant
    val density = LocalDensity.current
    val slotPx = with(density) { slot.toPx() }
    fun indexAt(x: Float) = (x / slotPx).toInt().coerceIn(0, (points.size - 1).coerceAtLeast(0))
    Canvas(
        modifier
            .pointerInput(points, slotPx) {
                detectTapGestures { at ->
                    val index = indexAt(at.x)
                    selected.value = if (selected.value == index) null else index
                }
            }
            .pointerInput(points, slotPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { selected.value = indexAt(it.x) },
                    onDrag = { change, _ -> selected.value = indexAt(change.position.x) },
                )
            },
    ) {
        val chart = size.height - 26.dp.toPx()
        val offset = 4.dp.toPx()
        fun yOf(units: Double) = chart - (units / scale.top * chart).toFloat() + offset
        fun xOf(index: Int) = slotPx * index + slotPx / 2
        (0..scale.rows).forEach { row ->
            val y = chart - chart * row / scale.rows + offset
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
            val x = xOf(index)
            val y = yOf(point.cents / 100.0)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(if (slot < 4.dp) 1.5.dp.toPx() else 2.dp.toPx()))
        val paint = android.graphics.Paint().apply {
            color = label.toArgb()
            textSize = 11.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = font
        }
        val dot = if (slot >= 7.dp) minOf(3.dp.toPx(), slotPx * 0.3f) else 0f
        points.forEachIndexed { index, point ->
            val x = xOf(index)
            if (point.cents > 0 && dot > 0f) drawCircle(line, dot, Offset(x, yOf(point.cents / 100.0)))
            val text = labelOf(index) ?: return@forEachIndexed
            val half = paint.measureText(text) / 2
            drawContext.canvas.nativeCanvas.drawText(text, x.coerceIn(half, size.width - half), size.height - 6.dp.toPx(), paint)
        }
        val chosen = selected.value?.takeIf { it in points.indices } ?: return@Canvas
        val point = points[chosen]
        val x = xOf(chosen)
        val y = yOf(point.cents / 100.0)
        drawLine(ink.copy(alpha = 0.35f), Offset(x, offset), Offset(x, chart + offset), 1.dp.toPx())
        drawCircle(ink, 5.dp.toPx(), Offset(x, y))
        drawCircle(line, 3.5.dp.toPx(), Offset(x, y))
        val text = "${pointTitle(point, monthly)}  ${f.money(point.cents)}"
        val title = android.graphics.Paint(paint).apply { color = ink.toArgb(); textSize = 12.sp.toPx(); textAlign = android.graphics.Paint.Align.LEFT }
        val padding = 8.dp.toPx()
        val width = title.measureText(text) + padding * 2
        val height = 22.dp.toPx()
        val left = (x - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
        // Above the line, unless the point is up there too.
        val top = if (y < offset + height + 8.dp.toPx()) y + 10.dp.toPx() else offset
        drawRoundRect(bubble, Offset(left, top), Size(width, height), CornerRadius(height / 2))
        drawContext.canvas.nativeCanvas.drawText(text, left + padding, top + height / 2 + 4.dp.toPx(), title)
    }
}

@Composable
private fun Pie(shares: List<Float>, modifier: Modifier = Modifier) {
    val font = rememberCanvasTypeface()
    val label = MaterialTheme.colorScheme.onSurface
    val colours = categoryColours()
    val gap = MaterialTheme.colorScheme.surface
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
            drawArc(colours.ranked(index), angle, sweep, true, origin, box)
            if (share >= 2f) {
                val middle = Math.toRadians((angle + sweep / 2).toDouble())
                val x = center.x + (radius + 26.dp.toPx()) * cos(middle).toFloat()
                val y = center.y + (radius + 18.dp.toPx()) * sin(middle).toFloat() + 4.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(percent(share.toDouble()), x, y, paint)
            }
            angle += sweep
        }
        // A thin line of the card's colour between slices, so neighbours read apart.
        if (shares.count { it > 0f } > 1) {
            var edge = 0f
            shares.forEach { share ->
                val at = Math.toRadians(edge.toDouble())
                drawLine(gap, center, Offset(center.x + radius * cos(at).toFloat(), center.y + radius * sin(at).toFloat()), 1.5.dp.toPx())
                edge += share / total * 360f
            }
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
