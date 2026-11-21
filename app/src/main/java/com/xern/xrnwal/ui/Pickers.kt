package com.xern.xrnwal.ui

import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xern.xrnwal.domain.Period
import com.xern.xrnwal.domain.PeriodRules
import com.xern.xrnwal.domain.RepeatRule
import com.xern.xrnwal.domain.RepeatUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun ArrowHeader(title: String, onPrevious: () -> Unit, onNext: () -> Unit, onTitle: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        RoundArrow(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous", onPrevious)
        Text(
            title,
            Modifier
                .padding(horizontal = 18.dp)
                .then(if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier),
            style = MaterialTheme.typography.titleMedium,
        )
        RoundArrow(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next", onNext)
    }
}

@Composable
private fun RoundArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(38.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp))
        }
    }
}

fun monthTitle(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

/** Weeks of a month as seven-slot rows, with nulls padding the first and last week. */
fun monthCells(month: YearMonth, firstDay: DayOfWeek): List<List<LocalDate?>> {
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val cells = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
}

fun weekdayLabels(firstDay: DayOfWeek): List<String> =
    (0L until 7L).map { firstDay.plus(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }

@Composable
private fun DateGrid(month: YearMonth, selected: LocalDate?, latest: LocalDate?, firstDay: DayOfWeek, onPick: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        weekdayLabels(firstDay).forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
    monthCells(month, firstDay).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { date ->
                Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                    if (date != null) {
                        val enabled = latest == null || date <= latest
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .then(if (date == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .clickable(enabled) { onPick(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateDialog(
    title: String = "Select date",
    initial: LocalDate,
    latest: LocalDate? = null,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val settings = prefs()
    val months = rememberEndlessPages(YearMonth.from(initial)) { month, steps -> month.plusMonths(steps.toLong()) }
    var date by remember { mutableStateOf(initial) }
    DialogCard(onDismiss) {
        Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        ArrowHeader(monthTitle(months.current), { months.turn(-1) }, { months.turn(1) })
        EndlessPager(months) { month ->
            Column(Modifier.heightIn(min = DATE_GRID_HEIGHT)) {
                DateGrid(month, date, latest, DayOfWeek.of(settings.firstDayOfWeek)) { date = it }
            }
        }
        CancelConfirmRow(onDismiss) { onPick(date) }
    }
}

@Composable
fun DateTimeDialog(
    initial: LocalDateTime,
    latest: LocalDate? = null,
    onDismiss: () -> Unit,
    onPick: (LocalDateTime) -> Unit,
) {
    val settings = prefs()
    val months = rememberEndlessPages(YearMonth.from(initial)) { month, steps -> month.plusMonths(steps.toLong()) }
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var time by remember { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0)) }
    DialogCard(onDismiss) {
        Text("Select date", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        ArrowHeader(monthTitle(months.current), { months.turn(-1) }, { months.turn(1) })
        EndlessPager(months) { month ->
            Column(Modifier.heightIn(min = DATE_GRID_HEIGHT)) {
                DateGrid(month, date, latest, DayOfWeek.of(settings.firstDayOfWeek)) { date = it }
            }
        }
        // The time turns right here, as the hours and minutes of a clock do, rather than in a second
        // dialog with the keyboard up over this one.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Time", Modifier.weight(1f))
            TimeWheels(time) { time = it }
        }
        CancelConfirmRow(onDismiss) { onPick(date.atTime(time)) }
    }
}

@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    var time by remember { mutableStateOf(initial.withSecond(0).withNano(0)) }
    DialogCard(onDismiss) {
        Text("Time", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimeWheels(time) { time = it } }
        CancelConfirmRow(onDismiss) { onPick(time) }
    }
}

private val TIME_ROW = 40.dp
private const val TIME_ROWS_SHOWN = 3

/** Copies of the hours or the minutes, end to end, so a wheel turns on past 59 to 00 and back. */
private const val TIME_LOOPS = 400

/**
 * Hours and minutes on wheels, as the phone writes times: 0 to 23, or 1 to 12 with AM and PM. Each
 * number that passes the middle ticks, if haptic feedback is on.
 */
@Composable
fun TimeWheels(time: LocalTime, onChange: (LocalTime) -> Unit) {
    val h24 = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    var hour by remember { mutableIntStateOf(time.hour) }
    var minute by remember { mutableIntStateOf(time.minute) }
    fun send() = onChange(LocalTime.of(hour, minute))
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = TIME_ROW)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (h24) {
                LoopWheel(24, 0, hour, { "%02d".format(it) }) { hour = it; send() }
            } else {
                val twelve = if (hour % 12 == 0) 12 else hour % 12
                LoopWheel(12, 1, twelve, { it.toString() }) { picked -> hour = hourOf(picked, pm = hour >= 12); send() }
            }
            Text(":", Modifier.padding(horizontal = 2.dp), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            LoopWheel(60, 0, minute, { "%02d".format(it) }) { minute = it; send() }
            if (!h24) {
                LoopWheel(2, 0, if (hour >= 12) 1 else 0, { if (it == 0) "AM" else "PM" }, loops = false) { pm ->
                    hour = hourOf(if (hour % 12 == 0) 12 else hour % 12, pm = pm == 1)
                    send()
                }
            }
        }
    }
}

/** The hour of the day, 0 to 23, for [twelve] o'clock (1 to 12) in the morning or, with [pm], after noon. */
internal fun hourOf(twelve: Int, pm: Boolean): Int = twelve % 12 + if (pm) 12 else 0

/** One column of [TimeWheels]: [count] values from [first], round and round unless [loops] is false. */
@Composable
private fun LoopWheel(count: Int, first: Int, selected: Int, label: (Int) -> String, loops: Boolean = true, onChange: (Int) -> Unit) {
    val copies = if (loops) TIME_LOOPS else 1
    val state = rememberLazyListState(count * (copies / 2) + (selected - first))
    val scope = rememberCoroutineScope()
    val tick = rememberHaptic(android.view.HapticFeedbackConstants.CLOCK_TICK)
    val middle by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index ?: state.firstVisibleItemIndex
        }
    }
    LaunchedEffect(state) {
        var last = -1
        snapshotFlow { middle }.collect {
            if (last != -1 && it != last) {
                tick()
                onChange(first + it % count)
            }
            last = it
        }
    }
    LazyColumn(
        Modifier.width(56.dp).height(TIME_ROW * TIME_ROWS_SHOWN),
        state = state,
        contentPadding = PaddingValues(vertical = TIME_ROW * (TIME_ROWS_SHOWN / 2)),
        flingBehavior = rememberSnapFlingBehavior(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count * copies) { index ->
            val chosen = index == middle
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(TIME_ROW)
                    .clickable { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(first + index % count),
                    color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = if (chosen) 22.sp else 15.sp,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Week, Month, Year and optionally Custom. Used by Home, Budget and Reports.
 */
@Composable
fun PeriodDialog(
    current: Period,
    allowCustom: Boolean = false,
    monthOnly: Boolean = false,
    onDismiss: () -> Unit,
    onPick: (Period) -> Unit,
) {
    val today = LocalDate.now()
    val settings = prefs()
    val rules = settings.periodRules
    val tabs = buildList {
        add("Week"); add("Month"); add("Year")
        if (allowCustom) add("Custom")
    }
    var tab by remember {
        mutableIntStateOf(
            when (current) {
                is Period.Week -> 0
                is Period.Month -> 1
                is Period.Year -> 2
                is Period.Custom -> 3
            },
        )
    }
    val years = rememberEndlessPages(
        when (current) {
            is Period.Month -> current.month.year
            is Period.Year -> current.year
            else -> today.year
        },
    ) { year, steps -> year + steps }
    val week = if (current is Period.Week) current.anyDay else today
    var start by remember { mutableStateOf(if (current is Period.Custom) current.start else today.minusDays(30)) }
    var end by remember { mutableStateOf(if (current is Period.Custom) current.end else today) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    val snapshot = snap()
    val months = remember(snapshot.bills, rules) { snapshot.bills.mapTo(HashSet()) { rules.monthOf(it.date) } }
    val firstYear = remember(snapshot.bills) { snapshot.bills.minOfOrNull { it.date.year } ?: today.year }

    TopSheet(onDismiss) {
        if (!monthOnly) UnderlineTabs(tabs, tab, onSelect = { tab = it })
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> WeekList(week, today, rules) { onPick(Period.Week(it)) }
            1 -> {
                ArrowHeader(years.current.toString(), { years.turn(-1) }, { years.turn(1) })
                Spacer(Modifier.height(10.dp))
                EndlessPager(years) { year ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        (1..12).chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { value ->
                                    val shown = YearMonth.of(year, value)
                                    PeriodCell(
                                        shown.format(MONTH_NAME),
                                        selected = current is Period.Month && current.month == shown,
                                        now = shown == rules.monthOf(today),
                                        quiet = shown !in months,
                                        modifier = Modifier.weight(1f),
                                    ) { onPick(Period.Month(shown)) }
                                }
                            }
                        }
                    }
                }
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (firstYear..today.year).toList().reversed().chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { value ->
                            PeriodCell(
                                value.toString(),
                                selected = current is Period.Year && current.year == value,
                                now = value == today.year,
                                quiet = false,
                                modifier = Modifier.weight(1f),
                            ) { onPick(Period.Year(value)) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            else -> {
                ListRow("From", start.format(FULL_DATE)) { pickStart = true }
                ListRow("To", end.format(FULL_DATE)) { pickEnd = true }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Show this range", Modifier.fillMaxWidth()) {
                    onPick(Period.Custom(minOf(start, end), maxOf(start, end)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val readable = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            if (tab == 1) {
                TextButton({ onPick(Period.Month(rules.monthOf(today))) }, colors = readable) { Text("This month") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onDismiss, colors = readable) { Text("Close") }
        }
    }
    if (pickStart) DateDialog("From", start, onDismiss = { pickStart = false }) { start = it; pickStart = false }
    if (pickEnd) DateDialog("To", end, onDismiss = { pickEnd = false }) { end = it; pickEnd = false }
}

/** Drops from just under the title that opened it, so the eye doesn't have to travel. */
@Composable
private fun TopSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(170), label = "sheet")
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                .padding(start = 12.dp, end = 12.dp, top = 72.dp),
        ) {
            Surface(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress
                        translationY = -24.dp.toPx() * (1 - progress)
                    }
                    .clickable(remember { MutableInteractionSource() }, null) {},
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
            ) { Column(Modifier.padding(16.dp), content = content) }
        }
    }
}

@Composable
private fun PeriodCell(text: String, selected: Boolean, now: Boolean, quiet: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(54.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .then(if (now && !selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                selected -> Color.Black
                quiet -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected || now) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Weeks in order with their dates beside them, opening on the chosen one. */
@Composable
private fun WeekList(selected: LocalDate, today: LocalDate, rules: PeriodRules, onPick: (LocalDate) -> Unit) {
    val thisWeek = rules.weekRange(today).start
    val chosen = rules.weekRange(selected).start
    val weeks = remember(thisWeek) { (-PAST_WEEKS..FUTURE_WEEKS).map { thisWeek.plusWeeks(it.toLong()) } }
    val list = rememberLazyListState((weeks.indexOf(chosen) - 2).coerceAtLeast(0))
    LazyColumn(Modifier.height(316.dp), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(weeks) { start ->
            val isSelected = start == chosen
            val range = rules.weekRange(start)
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(shape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (start == thisWeek && !isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .clickable { onPick(start) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Week ${Period.Week(start).number(rules)}",
                    Modifier.weight(1f),
                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected || start == thisWeek) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    com.xern.xrnwal.domain.Dates.span(range.start, range.endInclusive, today),
                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private const val FUTURE_WEEKS = 4
private const val PAST_WEEKS = 104

/** Six rows of days and the weekday names, so a page keeps its height whatever the month. */
private val DATE_GRID_HEIGHT = 284.dp

private val MONTH_NAME = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val FULL_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

/**
 * The period a page shows, with arrows for the next step in either direction and the name opening
 * the full picker. Long names shrink and may wrap, so an old week still fits beside the page's icons.
 */
@Composable
fun PeriodSwitcher(label: String, onPrevious: () -> Unit, onNext: () -> Unit, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onPrevious, Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous period", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A steady width keeps the arrows still under a finger pressing them again and again.
        Crossfade(
            label,
            Modifier
                .width(124.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = "Choose period", onClick = onPick)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            animationSpec = tween(140),
            label = "period",
        ) { shown ->
            // The ▾ says the name opens a picker, as the reference's month does.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                shown,
                Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleLarge,
                fontSize = when {
                    shown.length <= 9 -> 22.sp
                    shown.length <= 11 -> 19.sp
                    shown.length <= 14 -> 16.sp
                    else -> 14.sp
                },
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(22.dp))
            }
        }
        IconButton(onNext, Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next period", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val WHEEL_ROW = 44.dp
private const val WHEEL_ROWS_SHOWN = 5

/** A scrolling picker whose middle row is the choice. Tapping a row brings it to the middle. */
@Composable
fun <T> Wheel(items: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onChange: (T) -> Unit) {
    val state = rememberLazyListState(items.indexOf(selected).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val middle by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index ?: state.firstVisibleItemIndex
        }
    }
    LaunchedEffect(state) { snapshotFlow { middle }.collect { items.getOrNull(it)?.let(onChange) } }
    Box(modifier.height(WHEEL_ROW * WHEEL_ROWS_SHOWN), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(WHEEL_ROW)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(vertical = WHEEL_ROW * (WHEEL_ROWS_SHOWN / 2)),
            flingBehavior = rememberSnapFlingBehavior(state),
        ) {
            itemsIndexed(items) { index, item ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ROW)
                        .clickable { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(item),
                        color = if (index == middle) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
fun <T> WheelDialog(title: String, items: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    var choice by remember { mutableStateOf(selected) }
    DialogCard(onDismiss) {
        Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Wheel(items, selected, label, Modifier.fillMaxWidth()) { choice = it }
        CancelConfirmRow(onDismiss) { onPick(choice) }
    }
}

/** "Every N Days, Weeks, Months or Years". */
@Composable
fun RepeatDialog(rule: RepeatRule, onDismiss: () -> Unit, onPick: (Int, RepeatUnit) -> Unit) {
    var count by remember { mutableIntStateOf(rule.wheelCount.coerceIn(1, REPEAT_COUNT_MAX)) }
    var unit by remember { mutableStateOf(rule.wheelUnit) }
    DialogCard(onDismiss) {
        Text("Repeat", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Every", Modifier.padding(end = 12.dp), style = MaterialTheme.typography.bodyLarge)
            Wheel((1..REPEAT_COUNT_MAX).toList(), count, { it.toString() }, Modifier.weight(1f)) { count = it }
            Spacer(Modifier.width(8.dp))
            Wheel(RepeatUnit.entries.toList(), unit, { it.label }, Modifier.weight(1.4f)) { unit = it }
        }
        CancelConfirmRow(onDismiss) { onPick(count, unit) }
    }
}

private const val REPEAT_COUNT_MAX = 365
