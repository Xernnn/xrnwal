package com.xern.xrnwal.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
//
// Each widget is drawn at the size a Redmi Note 14's home screen gives two by two (173 by 187 dp) and
// four by two (376 by 187 dp), over a black and white grid, about the busiest wallpaper there is. The
// "Dark" previews are the phone in dark mode. A widget's text is set in the system's sans-serif, which
// is MiSans on HyperOS, so MiSans is swapped in here at the weight the phone would draw.

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

private const val TODAY_W = 173f
private const val MONTH_W = 376f
private const val CELL_H = 187f
private const val NIGHT = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL

@SuppressLint("NewApi")
private fun asOnPhone(view: View) {
    when (view) {
        is ViewGroup -> for (i in 0 until view.childCount) asOnPhone(view.getChildAt(i))
        is TextView -> {
            val face = view.typeface
            val weight = if (face == null) 400 else maxOf(face.weight, if (face.isBold) 700 else 0)
            val font = when {
                weight >= 700 -> R.font.misans_bold
                weight >= 600 -> R.font.misans_semibold
                weight >= 500 -> R.font.misans_medium
                else -> R.font.misans_regular
            }
            view.typeface = view.resources.getFont(font)
        }
    }
}

/** A widget as the launcher shows it: the views WalletWidgets sends, applied at [width] by [height]. */
@Composable
private fun Widget(
    kind: Kind,
    shown: WidgetViews.Content = content,
    width: Float = if (kind.month) MONTH_W else TODAY_W,
    height: Float = CELL_H,
) = AndroidView(
    { context ->
        WidgetViews.views(context, kind.month, kind.layout, shown, width, height).apply(context, FrameLayout(context)).also(::asOnPhone)
    },
    Modifier.size(width.dp, height.dp),
)

/** A widget as the picker shows it from Android 12: its layout as it is, the sample in it. */
@Composable
private fun Picker(kind: Kind) = AndroidView(
    { context -> LayoutInflater.from(context).inflate(kind.layout, FrameLayout(context), false).also(::asOnPhone) },
    Modifier.size((if (kind.month) MONTH_W else TODAY_W).dp, CELL_H.dp),
)

/** A black and white grid with sky behind it. */
@Composable
private fun Grid(width: Dp, height: Dp, content: @Composable () -> Unit) = Box(
    Modifier.size(width, height).drawBehind {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFDADADA), Color(0xFFB4B4B4), Color(0xFF8E8E8E))))
        val light = Offset(size.width * 0.22f, size.height * 0.25f)
        drawCircle(Brush.radialGradient(listOf(Color(0x88FFFFFF), Color(0x00FFFFFF)), light, size.width * 0.3f), size.width * 0.3f, light)
        val shade = Offset(size.width * 0.8f, size.height * 0.7f)
        drawCircle(Brush.radialGradient(listOf(Color(0x55000000), Color(0x00000000)), shade, size.width * 0.25f), size.width * 0.25f, shade)
        val ink = Color(0xFF151515)
        var x = 7.dp.toPx()
        while (x < size.width) {
            drawRect(ink, Offset(x, 0f), Size(2.6.dp.toPx(), size.height))
            x += 24.dp.toPx()
        }
        var y = 12.dp.toPx()
        while (y < size.height) {
            drawRect(ink, Offset(0f, y), Size(size.width, 3.2.dp.toPx()))
            y += 32.dp.toPx()
        }
        drawRect(ink, Offset(0f, 130.dp.toPx()), Size(size.width, 8.dp.toPx()))
    },
) { content() }

/** Today and Month side by side, a row for each of [shown]. */
@Composable
private fun Home(vararg shown: WidgetViews.Content) = Grid(597.dp, (40 + 203 * shown.size - 16).dp) {
    Column(Modifier.padding(start = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        shown.forEach {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Widget(Kind.TODAY, it)
                Widget(Kind.MONTH, it)
            }
        }
    }
}

/** On pace against a 3.600 budget. */
@Preview(widthDp = 597, heightDp = 227)
@Composable
fun Widgets() = Home(content)

@Preview(widthDp = 597, heightDp = 227, uiMode = NIGHT)
@Composable
fun WidgetsDark() = Home(content)

/** Against a 3.000 budget: today is over what it may spend, and the month runs ahead of its days. */
@Preview(widthDp = 597, heightDp = 227)
@Composable
fun Ahead() = Home(ahead)

@Preview(widthDp = 597, heightDp = 227, uiMode = NIGHT)
@Composable
fun AheadDark() = Home(ahead)

/** With an app lock set: dots for every amount and empty charts. */
@Preview(widthDp = 597, heightDp = 227)
@Composable
fun Locked() = Home(contentFor(360_000, settings = Settings(pinHash = "set")))

private val firstDay: LocalDate = day.withDayOfMonth(1)

/** A month with no bills and no budget, the first of the month with only the rent, and spending with no income and no budget. */
@Composable
private fun Edges() = Home(
    contentFor(0, from = emptyList()),
    contentFor(360_000, firstDay, bills.filter { it.date == firstDay }),
    contentFor(0, from = bills.filter { it.type != BillType.INCOME }),
)

@Preview(widthDp = 597, heightDp = 633)
@Composable
fun EdgeCases() = Edges()

@Preview(widthDp = 597, heightDp = 633, uiMode = NIGHT)
@Composable
fun EdgeCasesDark() = Edges()

/** Stretched: Today two by three and three by two, Month four by three. */
@Preview(widthDp = 597, heightDp = 596)
@Composable
fun Resized() = Grid(597.dp, 596.dp) {
    Column(Modifier.padding(start = 16.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Widget(Kind.TODAY, height = 290f)
            Widget(Kind.TODAY, width = 266f)
        }
        Widget(Kind.MONTH, height = 250f)
    }
}

/** The picker from Android 12: each layout as it is, on the grid. */
@Composable
private fun Pickers() = Grid(597.dp, 227.dp) {
    Row(Modifier.padding(start = 16.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Picker(Kind.TODAY)
        Picker(Kind.MONTH)
    }
}

@Preview(widthDp = 597, heightDp = 227)
@Composable
fun PickerPreviews() = Pickers()

@Preview(widthDp = 597, heightDp = 227, uiMode = NIGHT)
@Composable
fun PickerPreviewsDark() = Pickers()

// Each layout alone, transparent around it: the widget_preview_*.webp in app/src/main/res/drawable-nodpi
// (and drawable-night-nodpi for dark mode), which the picker shows before Android 12, are these.

@Preview(widthDp = 173, heightDp = 187)
@Composable
fun PickerToday() = Picker(Kind.TODAY)

@Preview(widthDp = 173, heightDp = 187, uiMode = NIGHT)
@Composable
fun PickerTodayDark() = Picker(Kind.TODAY)

@Preview(widthDp = 376, heightDp = 187)
@Composable
fun PickerMonth() = Picker(Kind.MONTH)

@Preview(widthDp = 376, heightDp = 187, uiMode = NIGHT)
@Composable
fun PickerMonthDark() = Picker(Kind.MONTH)
