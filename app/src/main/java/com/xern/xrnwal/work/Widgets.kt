package com.xern.xrnwal.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.LayoutRes
import com.xern.xrnwal.MainActivity
import com.xern.xrnwal.R
import com.xern.xrnwal.WalletApp
import com.xern.xrnwal.data.Settings
import com.xern.xrnwal.data.Snapshot
import com.xern.xrnwal.data.Stats
import com.xern.xrnwal.data.Totals
import com.xern.xrnwal.data.inRange
import com.xern.xrnwal.data.totals
import com.xern.xrnwal.domain.Period
import com.xern.xrnwal.ui.Fmt
import com.xern.xrnwal.ui.activeLedger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The numbers a widget shows. They follow the same rules as the Home header, so the two always agree. */
data class WidgetFigures(val today: Totals, val month: Totals, val balance: Long, val monthName: String, val dayName: String) {
    companion object {
        /** Printed instead of every amount once an app lock is set, because a widget sits outside that lock. */
        const val HIDDEN = "••••"

        private val days = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

        fun moneyText(settings: Settings): (Long) -> String {
            if (settings.locked) return { HIDDEN }
            val format = Fmt(settings)
            return { format.money(it) }
        }

        fun of(s: Snapshot, settings: Settings, today: LocalDate): WidgetFigures {
            val rules = settings.periodRules
            val period = Period.Month(rules.monthOf(today))
            val range = period.range(rules)
            val ledgerBills = s.inLedger(s.activeLedger(settings))
            val counted = if (settings.includeFuture) ledgerBills else ledgerBills.filter { it.date <= today }
            val month = counted.inRange(range).totals()
            return WidgetFigures(
                today = counted.filter { it.date == today }.totals(),
                month = month,
                balance = if (settings.carryover) Stats.carriedBalance(counted, range.endInclusive) else month.balance,
                monthName = period.label(rules, today),
                dayName = today.format(days),
            )
        }
    }
}

object WalletWidgets {
    /** A widget: its provider, whether it shows the month rather than today, and its layout. */
    enum class Kind(val provider: Class<out WalletWidget>, val month: Boolean, @LayoutRes val layout: Int) {
        TODAY(TodayWidget::class.java, false, R.layout.widget_today),
        MONTH(MonthWidget::class.java, true, R.layout.widget_month),
        ;

        /** The size a Redmi Note 14's home screen gives two by two and four by two, for a launcher that reports none. */
        val defaultWidthDp: Float get() = if (month) 376f else 173f
        val defaultHeightDp: Float get() = 187f

        companion object {
            fun of(provider: Class<*>): Kind? = entries.firstOrNull { it.provider == provider }
        }
    }

    private fun open(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context,
        code,
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * The widget's size in dp as the launcher reports it, upright: the narrowest width and the tallest
     * height, as a phone in portrait shows it.
     */
    fun sizeOf(options: Bundle, kind: Kind): Pair<Float, Float> {
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) width.toFloat() to height.toFloat() else kind.defaultWidthDp to kind.defaultHeightDp
    }

    /**
     * Redraws every widget on the home screen, or with [only], just those of one kind (one that was
     * resized). Does nothing when none are placed.
     */
    suspend fun update(context: Context, only: Pair<Kind, IntArray>? = null) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val placed = if (only != null) listOf(only)
        else Kind.entries.map { it to manager.getAppWidgetIds(ComponentName(context, it.provider)) }.filter { it.second.isNotEmpty() }
        if (placed.isEmpty()) return
        val repo = (context.applicationContext as WalletApp).repository
        val snapshot = withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { repo.snapshot.first { it.ledgers.isNotEmpty() } } ?: return
        val settings = repo.store.settings.value
        val today = LocalDate.now()
        val content = WidgetViews.Content(
            figures = WidgetFigures.of(snapshot, settings, today),
            series = WidgetSeries.of(snapshot, settings, today),
            money = WidgetFigures.moneyText(settings),
            decimalChar = settings.moneyFormat.decimalChar,
            hidden = settings.locked,
        )
        val taps = WidgetViews.Taps(open(context, Intent.ACTION_MAIN, 0), open(context, MainActivity.ACTION_ADD_BILL, 1))
        placed.forEach { (kind, ids) ->
            ids.forEach { id ->
                val (width, height) = sizeOf(manager.getAppWidgetOptions(id), kind)
                val views = WidgetViews.views(context, kind.month, kind.layout, content, width, height, taps)
                manager.updateAppWidget(id, views)
            }
        }
    }

    private const val LOAD_TIMEOUT_MILLIS = 8_000L
}

abstract class WalletWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = later(context) { WalletWidgets.update(context) }

    /** A resized widget is drawn again at its new size. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        val kind = WalletWidgets.Kind.of(javaClass) ?: return
        later(context) { WalletWidgets.update(context, kind to intArrayOf(id)) }
    }

    private fun later(context: Context, work: suspend () -> Unit) {
        val pending = goAsync()
        (context.applicationContext as WalletApp).scope.launch {
            try {
                work()
            } finally {
                pending.finish()
            }
        }
    }
}

class TodayWidget : WalletWidget()
class MonthWidget : WalletWidget()
