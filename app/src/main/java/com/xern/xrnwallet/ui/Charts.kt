package com.xern.xrnwallet.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Insights
import com.xern.xrnwallet.data.Stats
import com.xern.xrnwallet.domain.DateRange
import com.xern.xrnwallet.domain.Dates
import com.xern.xrnwallet.domain.Period
import com.xern.xrnwallet.domain.PeriodRules
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Reports' charts beyond the trend: how spending builds through a period, which days it lands on,
// and the twelve months around it. Figures and short labels only; each series is named on the chart.

/** "1.2k", "540", "-3.9k": a figure short enough to sit on a chart. */
internal fun compact(cents: Long): String {
    val units = cents / 100.0
    val size = abs(units)
    val sign = if (units < 0) "-" else ""
    return when {
        size >= 1_000_000 -> sign + trim(size / 1_000_000) + "m"
        size >= 1_000 -> sign + trim(size / 1_000) + "k"
        else -> sign + Math.round(size).toString()
    }
}

private fun trim(value: Double): String =
    if (value >= 100) Math.round(value).toString() else java.math.BigDecimal(value).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

private fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

private fun textPaint(colour: Color, size: Float, font: Typeface?, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
    color = colour.toArgb()
    textSize = size
    isAntiAlias = true
    textAlign = align
    typeface = font
}

private fun DrawScope.text(value: String, x: Float, y: Float, paint: Paint) = drawContext.canvas.nativeCanvas.drawText(value, x, y, paint)

/** A bar from [base] to [end] (either way up), rounded at the data end and square at the baseline. */
private fun DrawScope.bar(colour: Color, left: Float, width: Float, base: Float, end: Float) {
    val height = abs(end - base)
    if (height < 0.5f) return
    val radius = CornerRadius(min(3.dp.toPx(), min(width / 2, height)))
    val rect = Rect(left, min(base, end), left + width, max(base, end))
    val shape = if (end < base) RoundRect(rect, radius, radius, CornerRadius.Zero, CornerRadius.Zero)
    else RoundRect(rect, CornerRadius.Zero, CornerRadius.Zero, radius, radius)
    drawPath(Path().apply { addRoundRect(shape) }, colour)
}

/** A figure with its label underneath, and a short line in its series' colour beside the label. */
@Composable
private fun Figure(value: String, label: String, mark: Color?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mark != null) Box(Modifier.padding(end = 5.dp).size(10.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(mark))
            Muted(label, maxLines = 1)
        }
    }
}

@Composable
private fun ChartCard(title: String, aside: String?, content: @Composable () -> Unit) {
    Panel {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (aside != null) Muted(aside, maxLines = 1)
            }
            content()
        }
    }
}

/** The top of a scale from 0 just past [maximum], whose half is a round figure too: 4k, 5k, 3k. */
internal fun scaleTop(maximum: Long): Long {
    if (maximum <= 0) return 100_00
    val rough = maximum / 2.0
    val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(rough)))
    val half = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { it * magnitude >= rough } * magnitude
    return (ceil(maximum / half) * half).toLong()
}

/** The scale's two lines, at half and full, figures to their left; returns where the plot starts. */
private fun DrawScope.scaleLines(top: Long, right: Float, y: (Long) -> Float, grid: Color, paint: Paint): Float {
    val gutter = 26.dp.toPx()
    drawLine(grid, Offset(gutter, y(0)), Offset(right, y(0)), 1.dp.toPx())
    listOf(top / 2, top).forEach { level ->
        drawLine(grid, Offset(gutter, y(level)), Offset(right, y(level)), 1.dp.toPx())
        text(compact(level), gutter - 5.dp.toPx(), y(level) + 4.dp.toPx(), paint)
    }
    return gutter
}

// Spending pace

/**
 * Expenses added up day by day (month by month for a year) against the period before, and against an
 * even spread of the monthly [budgetCents] for a month. Tapping a day shows both totals at that day.
 */
@Composable
fun PaceCard(period: Period, bills: List<Bill>, rules: PeriodRules, today: LocalDate, budgetCents: Long?) {
    val f = fmt()
    val range = period.range(rules)
    val before = period.shifted(-1)
    val beforeRange = before.range(rules)
    val now = remember(bills, range) { Insights.running(Stats.trend(bills, period, range, BillType.EXPENSE).map { it.cents }) }
    val then = remember(bills, beforeRange) { Insights.running(Stats.trend(bills, before, beforeRange, BillType.EXPENSE).map { it.cents }) }
    val elapsed = Insights.elapsed(period, range, today, now.size)
    if (elapsed == 0 || (now.lastOrNull() ?: 0L) + (then.lastOrNull() ?: 0L) == 0L) return
    val budget = budgetCents?.takeIf { period is Period.Month && it > 0 }
    var chosen by remember(period) { mutableStateOf<Int?>(null) }
    val at = (chosen ?: (elapsed - 1)).coerceIn(0, elapsed - 1)
    val monthly = period is Period.Year
    val stepLabel = if (monthly) monthName(YearMonth.of((period as Period.Year).year, at + 1)) else Dates.short(range.start.plusDays(at.toLong()))
    val nowLabel = period.label(rules, today)
    val thenLabel = before.label(rules, today)

    ChartCard("Spending pace", stepLabel) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure(f.money(now[at]), nowLabel, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            Figure(f.money(then.getOrElse(at) { then.lastOrNull() ?: 0L }), thenLabel, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            if (budget != null) Figure(f.money(budget), "Budget", MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), Modifier.weight(1f))
        }
        PaceChart(now.take(elapsed), then, now.size, budget, at, nowLabel, thenLabel) { chosen = it }
    }
}

@Composable
private fun PaceChart(now: List<Long>, then: List<Long>, steps: Int, budget: Long?, at: Int, nowLabel: String, thenLabel: String, onPick: (Int) -> Unit) {
    val font = rememberCanvasTypeface()
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outline
    val count = max(steps, then.size).coerceAtLeast(2)
    val top = scaleTop(maxOf(now.maxOrNull() ?: 0L, then.maxOrNull() ?: 0L, budget ?: 0L))
    val right = 52.dp
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(150.dp)
            .pointerInput(count, now.size) {
                detectTapGestures { tap ->
                    val left = 26.dp.toPx()
                    val width = size.width - right.toPx() - left
                    val step = Math.round((tap.x - left) / width * (count - 1)).coerceIn(0, now.size - 1)
                    onPick(step)
                }
            },
    ) {
        val width = size.width - right.toPx()
        val bottom = size.height - 4.dp.toPx()
        val chartTop = 8.dp.toPx()
        fun y(cents: Long) = bottom - (bottom - chartTop) * (cents.toFloat() / top)
        val left = scaleLines(top, width, ::y, grid, textPaint(muted, 10.sp.toPx(), font, Paint.Align.RIGHT))
        fun x(step: Int) = left + (width - left) * step / (count - 1)
        val labels = mutableListOf<Pair<String, Float>>()
        if (budget != null) {
            drawLine(
                ink.copy(alpha = 0.5f), Offset(x(0), y(0)), Offset(x(steps - 1), y(budget)), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            )
            labels += "Budget" to y(budget)
        }
        fun line(values: List<Long>, colour: Color, stroke: Float) {
            if (values.isEmpty()) return
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, colour, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        line(then, muted.copy(alpha = 0.75f), 2.dp.toPx())
        if (then.isNotEmpty()) labels += thenLabel to y(then.last())
        line(now, accent, 2.5.dp.toPx())
        // The day picked, with both totals marked on it.
        drawLine(ink.copy(alpha = 0.3f), Offset(x(at), chartTop), Offset(x(at), bottom), 1.dp.toPx())
        then.getOrNull(at)?.let { drawCircle(surface, 5.dp.toPx(), Offset(x(at), y(it))); drawCircle(muted, 3.5.dp.toPx(), Offset(x(at), y(it))) }
        drawCircle(surface, 6.dp.toPx(), Offset(x(at), y(now[at])))
        drawCircle(accent, 4.dp.toPx(), Offset(x(at), y(now[at])))
        // The current line is named beside its end, which can be part way across.
        val label = textPaint(ink, 11.sp.toPx(), font)
        val end = now.lastIndex
        if (end == count - 1) labels += nowLabel to y(now[end])
        else {
            val name = textPaint(ink, 11.sp.toPx(), font, Paint.Align.CENTER)
            val half = name.measureText(nowLabel) / 2
            text(nowLabel, x(end).coerceIn(left + half, width - half), (y(now[end]) - 9.dp.toPx()).coerceAtLeast(10.dp.toPx()), name)
        }
        // Names at the right-hand end, nudged apart where lines finish close together.
        val gap = 13.dp.toPx()
        var last = -Float.MAX_VALUE
        labels.sortedBy { it.second }.forEach { (name, at) ->
            val placed = max(at, last + gap)
            text(name, width + 6.dp.toPx(), min(placed, bottom) + 4.dp.toPx(), label)
            last = placed
        }
    }
}

// Daily spending

/**
 * Each day of the period shaded by what was spent on it, in four steps set by the days that had any
 * spending (see [Insights.shadeCuts]), and the average for each weekday underneath. A month or less
 * reads as a calendar; longer, each column is a week. Tapping a day shows its date and figure.
 */
@Composable
fun DailyCard(range: DateRange, bills: List<Bill>, today: LocalDate, firstDay: DayOfWeek) {
    val f = fmt()
    val daily = remember(bills, range) { Insights.daily(bills, range) }
    if (daily.isEmpty()) return
    val cuts = remember(daily) { Insights.shadeCuts(daily.values) }
    val averages = remember(bills, range, today) { Insights.weekdayAverages(bills, range, today) }
    val busiest = remember(daily) { daily.maxBy { it.value }.key }
    var chosen by remember(range) { mutableStateOf(busiest) }
    val weekdays = (0..6).map { firstDay.plus(it.toLong()) }
    val calendar = range.days <= 42

    ChartCard("Daily spending", "${Dates.dayHeader(chosen)}  ${f.money(daily[chosen] ?: 0L)}") {
        if (calendar) CalendarHeat(range, daily, cuts, today, weekdays, chosen) { chosen = it }
        else StripHeat(range, daily, cuts, today, weekdays, chosen) { chosen = it }
        Muted("Average", Modifier.padding(top = 14.dp))
        WeekdayBars(weekdays, averages, letters = !calendar)
    }
}

@Composable
private fun heatColours(): List<Color> {
    val accent = MaterialTheme.colorScheme.primary
    return listOf(MaterialTheme.colorScheme.surfaceVariant) + listOf(0.25f, 0.45f, 0.7f, 1f).map { accent.copy(alpha = it) }
}

/** Weeks as rows, weekdays as columns, each day numbered. */
@Composable
private fun CalendarHeat(
    range: DateRange,
    daily: Map<LocalDate, Long>,
    cuts: List<Long>,
    today: LocalDate,
    weekdays: List<DayOfWeek>,
    chosen: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val font = rememberCanvasTypeface()
    val shades = heatColours()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val first = range.start.minusDays(weekdays.indexOf(range.start.dayOfWeek).toLong())
    val rows = ((ChronoUnit.DAYS.between(first, range.endInclusive) / 7) + 1).toInt()
    val head = 18.dp
    val cell = 36.dp
    fun dateAt(row: Int, column: Int): LocalDate = first.plusDays(row * 7L + column)
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(head + cell * rows)
            .pointerInput(range, rows) {
                detectTapGestures { tap ->
                    val row = ((tap.y - head.toPx()) / cell.toPx()).toInt()
                    val column = (tap.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                    if (row in 0 until rows) dateAt(row, column).takeIf { it in range && it <= today }?.let(onPick)
                }
            },
    ) {
        val width = size.width / 7f
        val gap = 2.dp.toPx()
        val letters = textPaint(muted, 11.sp.toPx(), font, Paint.Align.CENTER)
        weekdays.forEachIndexed { i, day ->
            text(day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), width * i + width / 2, 12.dp.toPx(), letters)
        }
        val number = textPaint(ink, 11.sp.toPx(), font, Paint.Align.CENTER)
        val dark = textPaint(Color.Black, 11.sp.toPx(), font, Paint.Align.CENTER)
        for (row in 0 until rows) for (column in 0..6) {
            val date = dateAt(row, column)
            if (date !in range) continue
            val left = width * column + gap / 2
            val top = head.toPx() + cell.toPx() * row + gap / 2
            val box = Size(width - gap, cell.toPx() - gap)
            val shade = Insights.shade(daily[date] ?: 0L, cuts)
            val corner = CornerRadius(6.dp.toPx())
            if (date > today) {
                drawRoundRect(shades[0].copy(alpha = 0.35f), Offset(left, top), box, corner)
            } else {
                drawRoundRect(shades[shade], Offset(left, top), box, corner)
            }
            if (date == chosen) drawRoundRect(ink, Offset(left, top), box, corner, style = Stroke(1.5.dp.toPx()))
            text(date.dayOfMonth.toString(), left + box.width / 2, top + box.height / 2 + 4.dp.toPx(), if (shade >= 3 && date <= today) dark else number)
        }
    }
}

/**
 * A long period: each column a week, each row a weekday, months named along the top. A year's
 * columns are narrow, so its cells stand taller than they are wide.
 */
@Composable
private fun StripHeat(
    range: DateRange,
    daily: Map<LocalDate, Long>,
    cuts: List<Long>,
    today: LocalDate,
    weekdays: List<DayOfWeek>,
    chosen: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val font = rememberCanvasTypeface()
    val shades = heatColours()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val first = range.start.minusDays(weekdays.indexOf(range.start.dayOfWeek).toLong())
    val columns = ((ChronoUnit.DAYS.between(first, range.endInclusive) / 7) + 1).toInt()
    val head = 16.dp
    val side = 14.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        val pitch = minOf((maxWidth - side) / columns, 14.dp)
        val rowPitch = (pitch * 1.6f).coerceIn(8.dp, 14.dp).coerceAtLeast(pitch)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(head + rowPitch * 7)
                .pointerInput(range, columns) {
                    detectTapGestures { tap ->
                        val column = ((tap.x - side.toPx()) / pitch.toPx()).toInt()
                        val row = ((tap.y - head.toPx()) / rowPitch.toPx()).toInt()
                        if (column in 0 until columns && row in 0..6) {
                            first.plusDays(column * 7L + row).takeIf { it in range && it <= today }?.let(onPick)
                        }
                    }
                },
        ) {
            val across = pitch.toPx()
            val down = rowPitch.toPx()
            val gap = (across * 0.2f).coerceIn(1.dp.toPx(), 2.dp.toPx())
            val corner = CornerRadius(min(2.dp.toPx(), (across - gap) / 3))
            val small = textPaint(muted, 10.sp.toPx(), font)
            listOf(0, 2, 4, 6).forEach { row ->
                text(weekdays[row].getDisplayName(TextStyle.NARROW, Locale.ENGLISH), 0f, head.toPx() + down * row + down / 2 + 4.dp.toPx(), small)
            }
            var lastLabel = -Float.MAX_VALUE
            for (column in 0 until columns) {
                val weekStart = first.plusDays(column * 7L)
                val x = side.toPx() + across * column
                val monthStart = (0..6).map { weekStart.plusDays(it.toLong()) }.firstOrNull { it.dayOfMonth == 1 && it in range }
                if (monthStart != null && x - lastLabel > 26.dp.toPx()) {
                    text(monthName(YearMonth.from(monthStart)), x, 11.dp.toPx(), small)
                    lastLabel = x
                }
                for (row in 0..6) {
                    val date = weekStart.plusDays(row.toLong())
                    if (date !in range) continue
                    val top = head.toPx() + down * row
                    val box = Size(across - gap, down - gap)
                    val colour = if (date > today) shades[0].copy(alpha = 0.35f) else shades[Insights.shade(daily[date] ?: 0L, cuts)]
                    drawRoundRect(colour, Offset(x, top), box, corner)
                    if (date == chosen) drawRoundRect(ink, Offset(x, top), box, corner, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

/** The average per weekday as seven bars, the highest in full colour, each with its figure on top. */
@Composable
private fun WeekdayBars(weekdays: List<DayOfWeek>, averages: Map<DayOfWeek, Long>, letters: Boolean) {
    val font = rememberCanvasTypeface()
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val highest = averages.values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Canvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(if (letters) 70.dp else 56.dp)) {
        val width = size.width / 7f
        val figure = textPaint(ink, 11.sp.toPx(), font, Paint.Align.CENTER)
        val letter = textPaint(muted, 11.sp.toPx(), font, Paint.Align.CENTER)
        val base = size.height - (if (letters) 18.dp.toPx() else 2.dp.toPx())
        val tallest = base - 16.dp.toPx()
        weekdays.forEachIndexed { i, day ->
            val value = averages[day] ?: 0L
            val centre = width * i + width / 2
            val barWidth = min(width * 0.5f, 22.dp.toPx())
            val end = base - tallest * value / highest
            bar(if (value == highest) accent else accent.copy(alpha = 0.45f), centre - barWidth / 2, barWidth, base, min(end, base - 1.dp.toPx()))
            text(compact(value), centre, end - 4.dp.toPx(), figure)
            if (letters) text(day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), centre, size.height - 3.dp.toPx(), letter)
        }
    }
}

// Twelve months

/**
 * Income above the line and expenses below it for each of twelve months, with what was left (the
 * net) as a line across them. Tapping a month shows its three figures.
 */
@Composable
fun FlowCard(flows: List<Insights.Flow>, focus: YearMonth) {
    val f = fmt()
    if (flows.all { it.income == 0L && it.expense == 0L }) return
    var chosen by remember(flows) { mutableStateOf(flows.indexOfFirst { it.month == focus }.coerceAtLeast(0)) }
    val month = flows[chosen]
    ChartCard("Income and expenses", "${monthName(month.month)} ${month.month.year}") {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Figure(f.money(month.income), "Income", f.incomeColor, Modifier.weight(1f))
            Figure(f.money(month.expense), "Expenses", f.expenseColor, Modifier.weight(1f))
            Figure(f.money(month.net), "Net", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
        FlowChart(flows, chosen, f.incomeColor, f.expenseColor) { chosen = it }
    }
}

@Composable
private fun FlowChart(flows: List<Insights.Flow>, chosen: Int, income: Color, expense: Color, onPick: (Int) -> Unit) {
    val font = rememberCanvasTypeface()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outline
    val up = max(flows.maxOf { max(it.income, it.net) }, 1L)
    val down = max(flows.maxOf { max(it.expense, -it.net) }, 1L)
    val right = 30.dp
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(180.dp)
            .pointerInput(flows) {
                detectTapGestures { tap ->
                    val slot = (size.width - right.toPx()) / flows.size
                    onPick((tap.x / slot).toInt().coerceIn(0, flows.size - 1))
                }
            },
    ) {
        val slot = (size.width - right.toPx()) / flows.size
        val top = 4.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val zero = top + (bottom - top) * up / (up + down)
        fun y(cents: Long) = zero - (zero - top) * cents.toFloat() / up
        fun yDown(cents: Long) = zero + (bottom - zero) * cents.toFloat() / down
        fun yNet(cents: Long) = if (cents >= 0) y(cents) else yDown(-cents)
        val barWidth = min(slot * 0.55f, 16.dp.toPx())
        flows.forEachIndexed { i, flow ->
            val left = slot * i + (slot - barWidth) / 2
            val alpha = if (i == chosen) 1f else 0.5f
            bar(income.copy(alpha = alpha), left, barWidth, zero - 1.dp.toPx(), y(flow.income))
            bar(expense.copy(alpha = alpha), left, barWidth, zero + 1.dp.toPx(), yDown(flow.expense))
        }
        drawLine(grid, Offset(0f, zero), Offset(size.width - right.toPx(), zero), 1.dp.toPx())
        val net = Path()
        flows.forEachIndexed { i, flow ->
            val x = slot * i + slot / 2
            if (i == 0) net.moveTo(x, yNet(flow.net)) else net.lineTo(x, yNet(flow.net))
        }
        drawPath(net, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        flows.forEachIndexed { i, flow ->
            val centre = Offset(slot * i + slot / 2, yNet(flow.net))
            drawCircle(surface, if (i == chosen) 6.dp.toPx() else 4.dp.toPx(), centre)
            drawCircle(ink, if (i == chosen) 4.dp.toPx() else 2.5.dp.toPx(), centre)
        }
        val label = textPaint(ink, 11.sp.toPx(), font)
        text("Net", slot * flows.size + 4.dp.toPx(), yNet(flows.last().net) + 4.dp.toPx(), label)
        monthLabels(flows.map { it.month }, chosen, slot, size.height - 4.dp.toPx(), textPaint(muted, 10.sp.toPx(), font, Paint.Align.CENTER), textPaint(ink, 10.sp.toPx(), font, Paint.Align.CENTER))
    }
}

/** Each month's name under its column, or its first letter where three don't fit. */
private fun DrawScope.monthLabels(months: List<YearMonth>, chosen: Int, slot: Float, baseline: Float, paint: Paint, chosenPaint: Paint, left: Float = 0f) {
    val short = slot < paint.measureText("May") + 4.dp.toPx()
    months.forEachIndexed { i, month ->
        val name = month.month.getDisplayName(if (short) TextStyle.NARROW else TextStyle.SHORT, Locale.ENGLISH)
        text(name, left + slot * i + slot / 2, baseline, if (i == chosen) chosenPaint else paint)
    }
}

/**
 * Twelve months of expenses stacked by category: the five largest over the twelve and the rest
 * together. The month picked has its shares named beside the bars.
 */
@Composable
fun MixCard(slices: List<Insights.Slice>, months: List<YearMonth>, focus: YearMonth) {
    val f = fmt()
    if (slices.isEmpty()) return
    var chosen by remember(months) { mutableStateOf(months.indexOf(focus).coerceAtLeast(0)) }
    val total = slices.sumOf { it.perMonth[chosen] }
    ChartCard("Categories by month", "${monthName(months[chosen])}  ${f.money(total)}") {
        MixChart(slices, months, chosen) { chosen = it }
    }
}

@Composable
private fun MixChart(slices: List<Insights.Slice>, months: List<YearMonth>, chosen: Int, onPick: (Int) -> Unit) {
    val font = rememberCanvasTypeface()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outline
    val palette = categoryColours()
    val colours = slices.mapIndexed { i, slice -> if (slice.category == null) palette.other else palette.ranked(i) }
    val totals = months.indices.map { m -> slices.sumOf { it.perMonth[m] } }
    val top = scaleTop(totals.maxOrNull() ?: 0L)
    val right = 112.dp
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .height(190.dp)
            .pointerInput(months) {
                detectTapGestures { tap ->
                    val left = 26.dp.toPx()
                    val slot = (size.width - right.toPx() - left) / months.size
                    if (tap.x in left..(left + slot * months.size)) onPick(((tap.x - left) / slot).toInt().coerceIn(0, months.size - 1))
                }
            },
    ) {
        val chartTop = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        fun height(cents: Long) = (bottom - chartTop) * cents.toFloat() / top
        val gutter = scaleLines(top, size.width - right.toPx(), { bottom - height(it) }, grid, textPaint(muted, 10.sp.toPx(), font, Paint.Align.RIGHT))
        val slot = (size.width - right.toPx() - gutter) / months.size
        val barWidth = min(slot * 0.6f, 18.dp.toPx())
        val gap = 2.dp.toPx()
        val middles = mutableListOf<Pair<Int, Float>>()
        months.indices.forEach { m ->
            val left = gutter + slot * m + (slot - barWidth) / 2
            var base = bottom
            val alpha = if (m == chosen) 1f else 0.55f
            val shown = slices.indices.filter { slices[it].perMonth[m] > 0 }
            shown.forEachIndexed { k, s ->
                val h = height(slices[s].perMonth[m])
                val end = base - h
                val last = k == shown.lastIndex
                if (h > gap) {
                    if (last) bar(colours[s].copy(alpha = alpha), left, barWidth, base, end)
                    else drawRect(colours[s].copy(alpha = alpha), Offset(left, end + gap), Size(barWidth, h - gap))
                }
                if (m == chosen) middles += s to (base - h / 2)
                base = end
            }
        }
        monthLabels(months, chosen, slot, size.height - 4.dp.toPx(), textPaint(muted, 10.sp.toPx(), font, Paint.Align.CENTER), textPaint(ink, 10.sp.toPx(), font, Paint.Align.CENTER), gutter)
        // The month picked: each category's name and share, beside the chart, top band first.
        val total = totals[chosen].coerceAtLeast(1L)
        val name = textPaint(ink, 11.sp.toPx(), font)
        val share = textPaint(muted, 11.sp.toPx(), font, Paint.Align.RIGHT)
        val x = gutter + slot * months.size + 10.dp.toPx()
        val step = 15.dp.toPx()
        var last = -Float.MAX_VALUE
        middles.sortedBy { it.second }.forEach { (s, middle) ->
            val y = max(middle, last + step)
            last = y
            drawCircle(colours[s], 3.5.dp.toPx(), Offset(x + 3.5.dp.toPx(), y))
            val title = slices[s].category?.name ?: "Other"
            val room = size.width - x - 12.dp.toPx() - 28.dp.toPx()
            text(fitText(title, name, room), x + 12.dp.toPx(), y + 4.dp.toPx(), name)
            text("${Math.round(slices[s].perMonth[chosen] * 100.0 / total)}%", size.width, y + 4.dp.toPx(), share)
        }
    }
}

/** [value] cut with an ellipsis to fit [width]. */
private fun fitText(value: String, paint: Paint, width: Float): String {
    if (paint.measureText(value) <= width) return value
    var cut = value
    while (cut.length > 1 && paint.measureText("$cut…") > width) cut = cut.dropLast(1)
    return "$cut…"
}
