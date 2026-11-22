package com.xern.xrnwal.work

import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.xern.xrnwal.R
import com.xern.xrnwal.domain.Money
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The widgets' views.
 *
 * - Today: today's spending inside an open ring that fills towards what today may spend.
 * - Month: the month's spending over a thick bar against its budget, or its income when there is
 *   none, with a mark at how far through the month today is.
 *
 * Text and shapes take their colours from the look the layouts are themed with, Sage in light mode and
 * Moss in dark (res/values/widget_looks.xml), which the launcher switches with the phone. The charts are
 * bitmaps drawn here, at the size the launcher gives the widget, once in each look, so the launcher can
 * switch those too. Spending faster than the month allows is drawn in the look's ink, not in red.
 */
object WidgetViews {
    @StyleRes private val LIGHT = R.style.WidgetLook_Sage

    @StyleRes private val DARK = R.style.WidgetLook_Moss

    /** A look's colours, read from its style so the charts match the text. */
    data class Look(val ink: Int, val accent: Int, val track: Int, val alert: Int) {
        companion object {
            fun of(context: Context, @StyleRes style: Int): Look {
                val a = context.obtainStyledAttributes(style, R.styleable.WidgetLook)
                try {
                    return Look(
                        ink = a.getColor(R.styleable.WidgetLook_widgetInk, 0),
                        accent = a.getColor(R.styleable.WidgetLook_widgetAccent, 0),
                        track = a.getColor(R.styleable.WidgetLook_widgetTrack, 0),
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

    /** The RemoteViews for Today or, with [month], Month, in [layout], drawn for a widget [widthDp] by [heightDp]. */
    fun views(
        context: Context,
        month: Boolean,
        @LayoutRes layout: Int,
        content: Content,
        widthDp: Float,
        heightDp: Float,
        taps: Taps = Taps(null, null),
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        taps.open?.let { views.setOnClickPendingIntent(R.id.widget_root, it) }
        taps.add?.let { views.setOnClickPendingIntent(R.id.widget_add, it) }
        val density = context.resources.displayMetrics.density
        val chart = if (month) month(views, content, widthDp, density) else today(views, content, widthDp, heightDp, density)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setIcon(
                R.id.widget_chart,
                "setImageIcon",
                Icon.createWithBitmap(chart(Look.of(context, LIGHT))),
                Icon.createWithBitmap(chart(Look.of(context, DARK))),
            )
        } else {
            // A launcher before Android 12 can't pick between two: the chart takes the phone's mode now,
            // and catches up with a change at the next redraw, within the hour.
            views.setImageViewBitmap(R.id.widget_chart, chart(Look.of(context, if (systemNight()) DARK else LIGHT)))
        }
        return views
    }

    private fun systemNight(): Boolean =
        Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** Today: the day, today's spending and what today may spend. Returns how to draw its ring in a look. */
    private fun today(v: RemoteViews, c: Content, widthDp: Float, heightDp: Float, density: Float): (Look) -> Bitmap {
        val pace = c.series.pace
        v.setTextViewText(R.id.widget_title, c.series.today.format(dayShort))
        v.setTextViewText(R.id.widget_hero, hero(c.money(pace.spentToday), c.decimalChar))
        if (pace.limit > 0) {
            v.setTextViewText(R.id.widget_sub, "of ${c.money(whole(pace.allowanceToday))}")
        } else {
            v.setViewVisibility(R.id.widget_sub, View.GONE)
        }
        val share = when {
            c.hidden || pace.limit <= 0 -> 0f
            pace.allowanceToday <= 0 -> if (pace.spentToday > 0) 1.01f else 0f
            else -> pace.spentToday.toFloat() / pace.allowanceToday
        }
        val chart = ChartSizes.dial(widthDp, heightDp)
        val px = ChartSizes.pixels(chart, density)
        return { look -> ring(px, chart, share, look) }
    }

    /**
     * Month: the month's spending, what is left and what a day may spend, or with no limit, its income
     * and balance. Returns how to draw its bar in a look.
     */
    private fun month(v: RemoteViews, c: Content, widthDp: Float, density: Float): (Look) -> Bitmap {
        val pace = c.series.pace
        v.setTextViewText(R.id.widget_title, monthName(c))
        v.setTextViewText(R.id.widget_hero, hero(c.money(pace.spent), c.decimalChar))
        if (pace.limit > 0) {
            v.setTextViewText(R.id.widget_sub, "of ${c.money(pace.limit)}")
            // While locked, not even whether the budget is gone shows.
            val over = pace.left < 0 && !c.hidden
            v.setTextViewText(R.id.widget_stat1_label, if (over) "Over" else "Left")
            v.setTextViewText(R.id.widget_stat1, figure(c.money(abs(pace.left)), c.decimalChar))
            v.setTextViewText(R.id.widget_stat2_label, "A day")
            v.setTextViewText(R.id.widget_stat2, c.money(whole(pace.perDay)))
        } else {
            v.setViewVisibility(R.id.widget_sub, View.GONE)
            v.setTextViewText(R.id.widget_stat1_label, "In")
            v.setTextViewText(R.id.widget_stat1, figure(c.money(c.figures.month.income), c.decimalChar))
            v.setTextViewText(R.id.widget_stat2_label, "Balance")
            v.setTextViewText(R.id.widget_stat2, figure(c.money(c.figures.balance), c.decimalChar))
        }
        val share = if (c.hidden) 0f else pace.share
        val chart = ChartSizes.bar(widthDp)
        val px = ChartSizes.pixels(chart, density)
        return { look -> bar(px, chart, share, pace.elapsed, look) }
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

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** Paints transparent: a gap cut into a chart shows the widget behind it. */
    private fun clear() = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    /** An open ring, 270 degrees with its gap at the bottom, filled clockwise to [share] of it, all in ink past 1. */
    private fun ring(px: ChartSizes.Pixels, chart: ChartSizes.Chart, share: Float, look: Look): Bitmap {
        val bitmap = createBitmap(px.width, px.height)
        val canvas = Canvas(bitmap)
        val d = px.width / chart.widthDp
        val stroke = 12 * d
        val box = RectF(stroke / 2 + d, stroke / 2 + d, px.width - stroke / 2 - d, px.height - stroke / 2 - d)
        fun arc(color: Int, sweep: Float) = canvas.drawArc(
            box,
            135f,
            sweep,
            false,
            fill(color).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND },
        )
        arc(look.track, 270f)
        if (share > 0f) arc(if (share > 1f) look.alert else look.accent, max(2f, 270f * min(share, 1f)))
        return bitmap
    }

    /**
     * A thick bar filled to [share], with a mark at [elapsed], how far through the month today is.
     * Whatever is spent beyond the mark is drawn in the look's ink.
     */
    private fun bar(px: ChartSizes.Pixels, chart: ChartSizes.Chart, share: Float, elapsed: Float, look: Look): Bitmap {
        val w = px.width
        val h = px.height
        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        val d = w / chart.widthDp
        val bar = min(16 * d, h.toFloat())
        val top = (h - bar) / 2
        val mark = 3 * d
        val left = mark
        val right = w - mark
        val r = bar / 2
        canvas.drawRoundRect(RectF(left, top, right, top + bar), r, r, fill(look.track))
        if (share > 0f) {
            val spent = RectF(left, top, max(left + (right - left) * min(share, 1f), left + bar), top + bar)
            canvas.drawRoundRect(spent, r, r, fill(look.alert))
            canvas.withClip(0f, 0f, left + (right - left) * elapsed, h.toFloat()) { drawRoundRect(spent, r, r, fill(look.accent)) }
        }
        val x = left + (right - left) * elapsed
        canvas.drawRoundRect(RectF(x - mark / 2 - 1.5f * d, 0f, x + mark / 2 + 1.5f * d, h.toFloat()), mark, mark, clear())
        canvas.drawRoundRect(RectF(x - mark / 2, 0.5f * d, x + mark / 2, h - 0.5f * d), mark / 2, mark / 2, fill(look.ink))
        return bitmap
    }
}
