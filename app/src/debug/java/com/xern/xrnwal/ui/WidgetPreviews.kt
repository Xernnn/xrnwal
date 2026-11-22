package com.xern.xrnwal.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.xern.xrnwal.R
import com.xern.xrnwal.data.Bill
import com.xern.xrnwal.data.BillType
import com.xern.xrnwal.data.Budget
import com.xern.xrnwal.data.Category
import com.xern.xrnwal.data.Ledger
import com.xern.xrnwal.data.Seed
import com.xern.xrnwal.data.Settings
import com.xern.xrnwal.data.Snapshot
import com.xern.xrnwal.work.WalletWidgets.Kind
import com.xern.xrnwal.work.WidgetFigures
import com.xern.xrnwal.work.WidgetSeries
import com.xern.xrnwal.work.WidgetViews
import java.time.LocalDate
import java.time.ZoneId

// The home-screen widgets without a phone: android preview render "?:app"
// --fqn=com.xern.xrnwal.ui.WidgetPreviewsKt.<Name>. The month below is made up: nobody's real money.

private val day: LocalDate = LocalDate.of(2026, 11, 22)
private val zone: ZoneId = ZoneId.systemDefault()

private val categories: List<Category> =
    Seed.expenseCategories.mapIndexed { i, (name, icon) -> Category(id = i + 1L, type = BillType.EXPENSE, name = name, icon = icon, sort = i) } +
        Seed.incomeCategories.mapIndexed { i, (name, icon) -> Category(id = 21L + i, type = BillType.INCOME, name = name, icon = icon, sort = i) }

/** November 2026 for a student with a part-time job, up to Sunday the 22nd. */
private val bills: List<Bill> = run {
    val out = mutableListOf<Bill>()
    fun add(date: Int, hour: Int, minute: Int, category: String, cents: Long, note: String = "", type: Int = BillType.EXPENSE) {
        val at = day.withDayOfMonth(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        val id = categories.first { it.name == category && it.type == type }.id
        out += Bill(id = out.size + 1L, ledgerId = 1, type = type, categoryId = id, amountCents = cents, currency = "AUD", note = note, atMillis = at)
    }
    listOf(2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 16, 17, 18, 19).forEach { add(it, 8, 10, "Trans", 420, "Opal") }
    add(1, 9, 0, "Rent", 165_000, "Rent")
    add(1, 12, 20, "Food", 2_460, "Lunch")
    add(2, 12, 40, "Food", 1_890, "Pho")
    add(3, 7, 0, "Membership", 5_500, "Gym")
    add(3, 12, 30, "Food", 1_400, "Sushi train")
    add(4, 12, 15, "Food", 830)
    add(5, 6, 0, "Salary", 235_000, "Pay", BillType.INCOME)
    add(5, 12, 10, "Food", 1_650)
    add(5, 19, 30, "Food", 2_610, "Thai")
    add(6, 12, 30, "Food", 1_780)
    add(7, 11, 0, "Food", 2_240, "Brunch")
    add(7, 16, 0, "Fun", 2_000, "Mini golf")
    add(7, 21, 0, "Drink", 4_600, "Bar")
    add(8, 11, 0, "Daily nes.", 4_780, "Groceries")
    add(8, 13, 0, "Food", 1_630)
    add(9, 12, 30, "Food", 1_110)
    add(10, 12, 30, "Food", 1_470)
    add(10, 15, 20, "Snacks", 900)
    add(11, 12, 40, "Food", 560)
    add(12, 12, 30, "Food", 2_230)
    add(12, 20, 0, "Gaming", 1_499, "Game pass")
    add(13, 12, 30, "Food", 1_540)
    add(14, 16, 0, "Fun", 2_200, "Cinema")
    add(14, 19, 0, "Food", 4_320, "Ramen")
    add(14, 21, 30, "Drink", 3_800, "Bar")
    add(15, 11, 0, "Daily nes.", 4_170, "Groceries")
    add(15, 13, 0, "Food", 1_700)
    add(16, 12, 30, "Food", 1_020)
    add(17, 12, 30, "Food", 1_880)
    add(17, 17, 30, "Health", 1_000, "Pharmacy")
    add(18, 10, 0, "Digital", 1_299, "Music")
    add(18, 12, 30, "Food", 890)
    add(19, 6, 0, "Salary", 235_000, "Pay", BillType.INCOME)
    add(19, 12, 30, "Food", 1_720)
    add(21, 11, 30, "Food", 2_860, "Brunch")
    add(21, 21, 0, "Drink", 4_400, "Bar")
    add(22, 12, 30, "Food", 1_890, "Pho")
    add(22, 15, 10, "Snacks", 780, "Bubble tea")
    add(22, 17, 20, "Daily nes.", 3_530, "Coles")
    // Entered ahead: the phone plan, due on the 28th.
    add(28, 9, 0, "Digital", 3_500, "Phone plan")
    out
}

/** [bills] as of [today], with an overall budget of [budget] (none at 0), and [settings] as they are. */
private fun contentFor(budget: Long, today: LocalDate = day, from: List<Bill> = bills, settings: Settings = Settings()): WidgetViews.Content {
    val snapshot = Snapshot(
        ledgers = listOf(Ledger(id = 1, name = "General")),
        categories = categories,
        bills = from,
        budgets = if (budget > 0) listOf(Budget(id = 1, amountCents = budget)) else emptyList(),
    )
    return WidgetViews.Content(
        figures = WidgetFigures.of(snapshot, settings, today),
        series = WidgetSeries.of(snapshot, settings, today),
        money = WidgetFigures.moneyText(settings),
        decimalChar = settings.moneyFormat.decimalChar,
        hidden = settings.locked,
    )
}

/** On pace against a 3.600 budget. */
private val content = contentFor(360_000)

/** Ahead of a 3.000 budget, and over what today may spend. */
private val ahead = contentFor(300_000)

private val TODAY_W = 172.dp
private val MONTH_W = 361.dp
private val CELL_H = 172.dp

private class Wall(val brush: Brush, val glow: Brush, val label: Color)

private val lightWall = Wall(
    Brush.linearGradient(listOf(Color(0xFFDCE3EE), Color(0xFFE9E6EF), Color(0xFFF1EBE3)), Offset.Zero, Offset(900f, 1400f)),
    Brush.radialGradient(listOf(Color(0x66FFFFFF), Color(0x00FFFFFF)), Offset(250f, 120f), 700f),
    Color(0xFF26262B),
)

private val darkWall = Wall(
    Brush.linearGradient(listOf(Color(0xFF1B2029), Color(0xFF111318), Color(0xFF15171D)), Offset.Zero, Offset(900f, 1400f)),
    Brush.radialGradient(listOf(Color(0x332F4A66), Color(0x002F4A66)), Offset(900f, 900f), 900f),
    Color(0xFFF4F4F5),
)

private val misans = FontFamily(Font(R.font.misans_medium))

/** A widget as the launcher shows it: the views WalletWidgets sends, applied at [width] by [height]. */
@Composable
private fun Widget(kind: Kind, width: Dp = if (kind.month) MONTH_W else TODAY_W, height: Dp = CELL_H, shown: WidgetViews.Content = content) =
    AndroidView(
        { context ->
            WidgetViews.views(context, kind.design, kind.month, kind.layout, kind.style, shown, width.value, height.value)
                .apply(context, FrameLayout(context))
        },
        Modifier.size(width, height),
    )

/** A widget as the picker shows it on Android 12 and later: its layout as it is, the sample in it. */
@Composable
private fun Picker(kind: Kind) = AndroidView(
    { context -> LayoutInflater.from(context).inflate(kind.layout, FrameLayout(context), false) },
    Modifier.size(if (kind.month) MONTH_W else TODAY_W, CELL_H),
)

@Composable
private fun Wallpaper(wall: Wall, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Box(modifier.background(wall.brush)) {
        Box(Modifier.fillMaxSize().background(wall.glow))
        content()
    }

/** The launcher icon in the cell beside a two-by-two widget. */
@Composable
private fun Icon(wall: Wall) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
    AppTile(56.dp, choice = AppIcons.choices.first { it.name == "Lime" })
    Text("xrnwal", color = wall.label, fontSize = 12.sp, fontFamily = misans, modifier = Modifier.padding(top = 5.dp))
}

private fun wallOf(kind: Kind) = if (kind.name.endsWith("DARK")) darkWall else lightWall

/** A phone-width slice of home screen with one widget, and the icon beside a two-by-two. */
@Composable
private fun Slice(kind: Kind) = Wallpaper(wallOf(kind), Modifier.size(393.dp, 220.dp)) {
    Row(Modifier.padding(start = 16.dp, top = 24.dp), horizontalArrangement = Arrangement.spacedBy(17.dp)) {
        Widget(kind)
        if (!kind.month) Icon(wallOf(kind))
    }
}

/** Both sizes of one look on a home screen. */
@Composable
private fun Screen(today: Kind, month: Kind, shown: WidgetViews.Content = content) = Wallpaper(wallOf(today), Modifier.size(393.dp, 428.dp)) {
    Column(Modifier.padding(start = 16.dp, top = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            Widget(today, shown = shown)
            Icon(wallOf(today))
        }
        Widget(month, shown = shown)
    }
}

@Preview(widthDp = 393, heightDp = 220) @Composable fun PaceTodayLight() = Slice(Kind.PACE_TODAY_LIGHT)
@Preview(widthDp = 393, heightDp = 220) @Composable fun PaceTodayDark() = Slice(Kind.PACE_TODAY_DARK)
@Preview(widthDp = 393, heightDp = 220) @Composable fun PaceMonthLight() = Slice(Kind.PACE_MONTH_LIGHT)
@Preview(widthDp = 393, heightDp = 220) @Composable fun PaceMonthDark() = Slice(Kind.PACE_MONTH_DARK)
@Preview(widthDp = 393, heightDp = 220) @Composable fun RhythmTodayLight() = Slice(Kind.RHYTHM_TODAY_LIGHT)
@Preview(widthDp = 393, heightDp = 220) @Composable fun RhythmTodayDark() = Slice(Kind.RHYTHM_TODAY_DARK)
@Preview(widthDp = 393, heightDp = 220) @Composable fun RhythmMonthLight() = Slice(Kind.RHYTHM_MONTH_LIGHT)
@Preview(widthDp = 393, heightDp = 220) @Composable fun RhythmMonthDark() = Slice(Kind.RHYTHM_MONTH_DARK)

@Preview(widthDp = 786, heightDp = 428)
@Composable
fun PaceBoth() = Row {
    Screen(Kind.PACE_TODAY_LIGHT, Kind.PACE_MONTH_LIGHT)
    Screen(Kind.PACE_TODAY_DARK, Kind.PACE_MONTH_DARK)
}

@Preview(widthDp = 786, heightDp = 428)
@Composable
fun RhythmBoth() = Row {
    Screen(Kind.RHYTHM_TODAY_LIGHT, Kind.RHYTHM_MONTH_LIGHT)
    Screen(Kind.RHYTHM_TODAY_DARK, Kind.RHYTHM_MONTH_DARK)
}

/** Against a 3.000 budget: today is over what it may spend, and the month runs ahead. */
@Preview(widthDp = 786, heightDp = 428)
@Composable
fun PaceAhead() = Row {
    Screen(Kind.PACE_TODAY_LIGHT, Kind.PACE_MONTH_LIGHT, ahead)
    Screen(Kind.PACE_TODAY_DARK, Kind.PACE_MONTH_DARK, ahead)
}

/** Stretched: each Month four by three, each Today three wide and two by three. */
@Preview(widthDp = 786, heightDp = 1090)
@Composable
fun Resized() = Row {
    listOf(lightWall to listOf(Kind.PACE_TODAY_LIGHT, Kind.PACE_MONTH_LIGHT, Kind.RHYTHM_TODAY_LIGHT, Kind.RHYTHM_MONTH_LIGHT),
        darkWall to listOf(Kind.PACE_TODAY_DARK, Kind.PACE_MONTH_DARK, Kind.RHYTHM_TODAY_DARK, Kind.RHYTHM_MONTH_DARK)).forEach { (wall, kinds) ->
        Wallpaper(wall, Modifier.size(393.dp, 1090.dp)) {
            Column(Modifier.padding(start = 16.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                    Widget(kinds[0], 172.dp, 260.dp)
                    Widget(kinds[2], 172.dp, 260.dp)
                }
                Widget(kinds[1], MONTH_W, 260.dp)
                Widget(kinds[3], MONTH_W, 260.dp)
                Widget(kinds[2], 266.dp, CELL_H)
            }
        }
    }
}

/** With an app lock set: dots for every amount and empty charts. */
@Preview(widthDp = 786, heightDp = 428)
@Composable
fun Locked() = Row {
    val locked = contentFor(360_000, settings = Settings(pinHash = "set"))
    Screen(Kind.PACE_TODAY_LIGHT, Kind.PACE_MONTH_LIGHT, locked)
    Screen(Kind.RHYTHM_TODAY_DARK, Kind.RHYTHM_MONTH_DARK, locked)
}

private val firstDay: LocalDate = day.withDayOfMonth(1)

/**
 * The edge cases, light above and dark below: a month with no bills and no budget, the first of the
 * month with only the rent, and a month with spending but no income and no budget.
 */
@Preview(widthDp = 1179, heightDp = 856)
@Composable
fun EdgeCases() {
    val empty = contentFor(0, from = emptyList())
    val first = contentFor(360_000, firstDay, bills.filter { it.date == firstDay })
    val noIncome = contentFor(0, from = bills.filter { it.type != BillType.INCOME })
    Column {
        listOf(Kind.PACE_TODAY_LIGHT to Kind.PACE_MONTH_LIGHT, Kind.RHYTHM_TODAY_DARK to Kind.RHYTHM_MONTH_DARK).forEach { (today, month) ->
            Row { listOf(empty, first, noIncome).forEach { Screen(today, month, it) } }
        }
    }
}

/** The picker, Android 12 and later: each widget's layout as it is, on a plain sheet. */
@Preview(widthDp = 786, heightDp = 1000)
@Composable
fun PickerPreviews() = Box(Modifier.size(786.dp, 1000.dp).background(Color(0xFFEDEEF2))) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Kind.entries.chunked(4).forEach { group ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Picker(group[0]); Picker(group[1]) }
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Picker(group[2]); Picker(group[3]) }
            }
        }
    }
}

// Each widget alone at its picker size, transparent around it: app/src/main/res/drawable-nodpi's
// widget_preview_*.webp, which the picker shows before Android 12, are these.

@Preview(widthDp = 172, heightDp = 172) @Composable fun PickerPaceTodayLight() = Picker(Kind.PACE_TODAY_LIGHT)
@Preview(widthDp = 172, heightDp = 172) @Composable fun PickerPaceTodayDark() = Picker(Kind.PACE_TODAY_DARK)
@Preview(widthDp = 361, heightDp = 172) @Composable fun PickerPaceMonthLight() = Picker(Kind.PACE_MONTH_LIGHT)
@Preview(widthDp = 361, heightDp = 172) @Composable fun PickerPaceMonthDark() = Picker(Kind.PACE_MONTH_DARK)
@Preview(widthDp = 172, heightDp = 172) @Composable fun PickerRhythmTodayLight() = Picker(Kind.RHYTHM_TODAY_LIGHT)
@Preview(widthDp = 172, heightDp = 172) @Composable fun PickerRhythmTodayDark() = Picker(Kind.RHYTHM_TODAY_DARK)
@Preview(widthDp = 361, heightDp = 172) @Composable fun PickerRhythmMonthLight() = Picker(Kind.RHYTHM_MONTH_LIGHT)
@Preview(widthDp = 361, heightDp = 172) @Composable fun PickerRhythmMonthDark() = Picker(Kind.RHYTHM_MONTH_DARK)
