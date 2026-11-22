package com.xern.xrnwal.work

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.core.graphics.createBitmap
import com.xern.xrnwal.R
import com.xern.xrnwal.domain.Money
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The Pace and Rhythm widgets' views. Text and shapes take their colours from the look a layout is
 * themed with (res/values/widget_looks.xml); the charts are drawn into bitmaps here, in the same colours,
 * at the size the launcher gives the widget.
 *
 * - Pace: a ring for how much of what today may spend is gone, and a bar for the month's spending
 *   against its budget, or its income when there is none, with a mark at how far through the month
 *   today is. Both turn red once spending runs ahead.
 * - Rhythm: the day's or month's spending over a bar for each day, today's lit, days to come as dots.
 */
object WidgetViews {
    enum class Design { PACE, RHYTHM }

    /** A look's colours, read from its style so the charts match the text. */
    data class Look(
        val surface: Int,
        val ink: Int,
        val muted: Int,
        val accent: Int,
        val accentText: Int,
        val track: Int,
        val faint: Int,
        val alert: Int,
    ) {
        companion object {
            fun of(context: Context, @StyleRes style: Int): Look {
                val a = context.obtainStyledAttributes(style, R.styleable.WidgetLook)
                try {
                    return Look(
                        surface = a.getColor(R.styleable.WidgetLook_widgetSurface, 0),
                        ink = a.getColor(R.styleable.WidgetLook_widgetInk, 0),
                        muted = a.getColor(R.styleable.WidgetLook_widgetMuted, 0),
                        accent = a.getColor(R.styleable.WidgetLook_widgetAccent, 0),
                        accentText = a.getColor(R.styleable.WidgetLook_widgetAccentText, 0),
                        track = a.getColor(R.styleable.WidgetLook_widgetTrack, 0),
                        faint = a.getColor(R.styleable.WidgetLook_widgetFaint, 0),
                        alert = a.getColor(R.styleable.WidgetLook_widgetAlert, 0),
                    )
                } finally {
                    a.recycle()
                }
            }
        }
    }

    /** What the widgets show, worked out once for every widget on the home screen. */
    class Content(
        val figures: WidgetFigures,
        val series: WidgetSeries,
        val money: (Long) -> String,
        val decimalChar: Char,
        /**
         * An app lock is set. [money] already prints dots for every amount, and the charts are drawn
         * empty, so nothing on the home screen tells how much was spent.
         */
        val hidden: Boolean,
    )

    /** Where a tap goes. Null in previews, which have nothing to open. */
    class Taps(val open: PendingIntent?, val add: PendingIntent?)

    private val dayShort = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

    /**
     * The RemoteViews for [design] in [layout] (one of the themed widget layouts) and its [style],
     * two by two or, with [month], four by two, drawn for a widget [widthDp] by [heightDp].
     */
    fun views(
        context: Context,
        design: Design,
        month: Boolean,
        @LayoutRes layout: Int,
        @StyleRes style: Int,
        content: Content,
        widthDp: Float,
        heightDp: Float,
        taps: Taps = Taps(null, null),
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        taps.open?.let { views.setOnClickPendingIntent(R.id.widget_root, it) }
        taps.add?.let { views.setOnClickPendingIntent(R.id.widget_add, it) }
        val look = Look.of(context, style)
        val density = context.resources.displayMetrics.density
        when (design) {
            Design.PACE -> pace(views, month, look, content, widthDp, heightDp, density)
            Design.RHYTHM -> rhythm(views, month, look, content, widthDp, heightDp, density)
        }
        return views
    }

    private fun pace(v: RemoteViews, month: Boolean, look: Look, c: Content, widthDp: Float, heightDp: Float, density: Float) {
        val pace = c.series.pace
        if (month) {
            v.setTextViewText(R.id.widget_title, monthName(c))
            v.setTextViewText(R.id.widget_hero, hero(c.money(pace.spent), c.decimalChar))
            if (pace.limit > 0) {
                v.setTextViewText(R.id.widget_sub, "of ${c.money(pace.limit)}")
                // While locked, not even whether the budget is gone shows.
                val over = pace.left < 0 && !c.hidden
                v.setTextViewText(R.id.widget_stat1_label, if (over) "Over" else "Left")
                v.setTextViewText(R.id.widget_stat1, figure(c.money(abs(pace.left)), c.decimalChar))
                v.setTextColor(R.id.widget_stat1, if (over) look.alert else look.ink)
                v.setTextViewText(R.id.widget_stat2_label, "A day")
                v.setTextViewText(R.id.widget_stat2, c.money(whole(pace.perDay)))
            } else {
                v.setViewVisibility(R.id.widget_sub, View.GONE)
                v.setTextViewText(R.id.widget_stat1_label, "In")
                v.setTextViewText(R.id.widget_stat1, figure(c.money(c.figures.month.income), c.decimalChar))
                v.setTextViewText(R.id.widget_stat2_label, "Balance")
                v.setTextViewText(R.id.widget_stat2, figure(c.money(c.figures.balance), c.decimalChar))
            }
            val chart = ChartSizes.paceBar(widthDp)
            v.setImageViewBitmap(R.id.widget_chart, paceBar(ChartSizes.pixels(chart, density), chart, pace, c.hidden, look))
        } else {
            v.setTextViewText(R.id.widget_title, c.series.today.format(dayShort))
            v.setTextViewText(R.id.widget_hero, hero(c.money(pace.spentToday), c.decimalChar))
            if (pace.limit > 0) v.setTextViewText(R.id.widget_sub, "of ${c.money(whole(pace.allowanceToday))}")
            else v.setViewVisibility(R.id.widget_sub, View.GONE)
            val share = when {
                c.hidden || pace.limit <= 0 -> 0f
                pace.allowanceToday <= 0 -> if (pace.spentToday > 0) 1.01f else 0f
                else -> pace.spentToday.toFloat() / pace.allowanceToday
            }
            val chart = ChartSizes.paceRing(widthDp, heightDp)
            v.setImageViewBitmap(R.id.widget_chart, ring(ChartSizes.pixels(chart, density), chart, share, look))
        }
    }

    private val dayLetters = listOf(
        R.id.widget_day0, R.id.widget_day1, R.id.widget_day2, R.id.widget_day3, R.id.widget_day4, R.id.widget_day5, R.id.widget_day6,
    )

    private fun rhythm(v: RemoteViews, month: Boolean, look: Look, c: Content, widthDp: Float, heightDp: Float, density: Float) {
        val s = c.series
        if (month) {
            v.setTextViewText(R.id.widget_title, monthName(c))
            v.setTextViewText(R.id.widget_hero, hero(c.money(c.figures.month.expense), c.decimalChar))
            v.setTextViewText(R.id.widget_stat1, labelled("In", c.money(c.figures.month.income), look))
            v.setTextViewText(R.id.widget_stat2, labelled("Balance", c.money(c.figures.balance), look))
            val chart = ChartSizes.month(widthDp, heightDp)
            v.setImageViewBitmap(R.id.widget_chart, bars(ChartSizes.pixels(chart, density), chart, s.days, s, c.hidden, look, gapDp = 3.5f))
        } else {
            v.setTextViewText(R.id.widget_title, s.today.format(dayShort))
            v.setTextViewText(R.id.widget_hero, hero(c.money(c.figures.today.expense), c.decimalChar))
            val chart = ChartSizes.week(widthDp, heightDp)
            v.setImageViewBitmap(R.id.widget_chart, bars(ChartSizes.pixels(chart, density), chart, s.week, s, c.hidden, look, gapDp = 9f))
            s.week.forEachIndexed { i, day ->
                v.setTextViewText(dayLetters[i], day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH))
                v.setTextColor(dayLetters[i], if (day.date == s.today) look.accentText else look.muted)
            }
        }
    }

    /** The accounting month's full name, with its year when that isn't this one. */
    private fun monthName(c: Content): String {
        val month = c.series.month
        val name = month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return if (month.year == c.series.today.year) name else "$name ${month.year}"
    }

    /** An estimate in whole units: what a day may spend needs no cents. */
    private fun whole(cents: Long): Long = cents / Money.SCALE * Money.SCALE

    /** A hero amount with its symbol raised and small, and its decimals small, like a price tag. */
    fun hero(text: String, decimalChar: Char): CharSequence {
        val parts = MoneyParts.of(text, decimalChar)
        val out = SpannableStringBuilder()
        if (parts.lead.isNotEmpty()) {
            out.append(parts.lead)
            out.setSpan(RelativeSizeSpan(0.55f), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(SuperscriptSpan(), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        out.append(parts.whole)
        if (parts.fraction.isNotEmpty()) {
            val start = out.length
            out.append(parts.fraction)
            out.setSpan(RelativeSizeSpan(0.6f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** A smaller figure: only its decimals are set small. */
    private fun figure(text: String, decimalChar: Char): CharSequence {
        val parts = MoneyParts.of(text, decimalChar)
        val out = SpannableStringBuilder(parts.lead).append(parts.whole)
        if (parts.fraction.isNotEmpty()) {
            val start = out.length
            out.append(parts.fraction)
            out.setSpan(RelativeSizeSpan(0.72f), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    private fun labelled(label: String, value: String, look: Look): CharSequence {
        val out = SpannableStringBuilder(label).append("  ")
        out.setSpan(ForegroundColorSpan(look.muted), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return out.append(value)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** An open ring, 270 degrees with its gap at the bottom, filled clockwise to [share] of it. */
    private fun ring(px: ChartSizes.Pixels, chart: ChartSizes.Chart, share: Float, look: Look): Bitmap {
        val bitmap = createBitmap(px.width, px.height)
        val canvas = Canvas(bitmap)
        val d = px.width / chart.widthDp
        val stroke = 9 * d * (chart.widthDp / 108f).coerceIn(1f, 1.3f)
        val box = RectF(stroke / 2 + 1, stroke / 2 + 1, px.width - stroke / 2 - 1, px.height - stroke / 2 - 1)
        fun arc(color: Int, sweep: Float) = canvas.drawArc(
            box, 135f, sweep, false,
            fill(color).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND },
        )
        arc(look.track, 270f)
        if (share > 0f) arc(if (share > 1f) look.alert else look.accent, max(2f, 270f * min(share, 1f)))
        return bitmap
    }

    /** The month's spending as a bar against its limit, with a mark at how far through the month today is. */
    private fun paceBar(px: ChartSizes.Pixels, chart: ChartSizes.Chart, pace: Pace, hidden: Boolean, look: Look): Bitmap {
        val w = px.width
        val h = px.height
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        val d = w / chart.widthDp
        val bar = 10 * d
        val top = (h - bar) / 2
        val mark = 3 * d
        val left = mark
        val right = w - mark
        canvas.drawRoundRect(RectF(left, top, right, top + bar), bar / 2, bar / 2, fill(look.track))
        if (!hidden && pace.limit > 0 && pace.spent > 0) {
            val end = left + (right - left) * min(pace.share, 1f)
            canvas.drawRoundRect(RectF(left, top, max(end, left + bar), top + bar), bar / 2, bar / 2, fill(if (pace.ahead) look.alert else look.accent))
        }
        val x = left + (right - left) * pace.elapsed
        canvas.drawRoundRect(RectF(x - mark / 2 - 1.5f * d, 0f, x + mark / 2 + 1.5f * d, h.toFloat()), mark, mark, fill(look.surface))
        canvas.drawRoundRect(RectF(x - mark / 2, 1.5f * d, x + mark / 2, h - 1.5f * d), mark / 2, mark / 2, fill(look.ink))
        return bitmap
    }

    /**
     * A bar for each of [days], centred in equal cells so the letters under them line up: today's in
     * the accent, the days before it in the track colour, the days still to come as dots (or faint bars
     * for bills already entered for them). A day clipped by [WidgetSeries.bars] has a break cut near its
     * top. With [hidden], every day is drawn as if nothing was spent.
     */
    private fun bars(
        px: ChartSizes.Pixels,
        chart: ChartSizes.Chart,
        days: List<DaySpend>,
        s: WidgetSeries,
        hidden: Boolean,
        look: Look,
        gapDp: Float,
    ): Bitmap {
        val w = px.width
        val h = px.height
        val bitmap = createBitmap(w, h)
        if (days.isEmpty()) return bitmap
        val canvas = Canvas(bitmap)
        val d = w / chart.widthDp
        val cell = w.toFloat() / days.size
        val bw = max(d, cell - gapDp * d)
        val radius = min(bw / 2, 3 * d)
        val cents = days.map { if (hidden) 0L else it.cents }
        val heights = WidgetSeries.bars(cents)
        days.forEachIndexed { i, day ->
            val x = i * cell + (cell - bw) / 2
            val bar = heights[i]
            val future = day.date > s.today
            when {
                future && cents[i] == 0L -> {
                    val r = min(bw / 2, 1.6f * d)
                    canvas.drawCircle(x + bw / 2, h - r, r, fill(look.faint))
                }
                cents[i] == 0L -> canvas.drawRoundRect(RectF(x, h - 2 * d, x + bw, h.toFloat()), d, d, fill(look.faint))
                else -> {
                    val top = h - max(bar.height * h, 3 * d)
                    val color = when {
                        future -> look.faint
                        day.date == s.today -> look.accent
                        else -> look.track
                    }
                    canvas.drawRoundRect(RectF(x, top, x + bw, h.toFloat()), radius, radius, fill(color))
                    if (bar.clipped) {
                        val y = top + 5 * d
                        canvas.drawRect(x - 1, y, x + bw + 1, y + 2 * d, fill(look.surface))
                    }
                }
            }
        }
        return bitmap
    }
}
