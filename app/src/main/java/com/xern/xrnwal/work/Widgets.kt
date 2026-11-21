package com.xern.xrnwal.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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
    private class Look(val provider: Class<out WalletWidget>, @LayoutRes val layout: Int, val month: Boolean)

    private val looks = listOf(
        Look(TodayLightWidget::class.java, R.layout.widget_today_light, false),
        Look(TodayDarkWidget::class.java, R.layout.widget_today_dark, false),
        Look(TodayLimeWidget::class.java, R.layout.widget_today_lime, false),
        Look(MonthLightWidget::class.java, R.layout.widget_month_light, true),
        Look(MonthDarkWidget::class.java, R.layout.widget_month_dark, true),
        Look(MonthLimeWidget::class.java, R.layout.widget_month_lime, true),
    )

    private fun open(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context,
        code,
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun views(context: Context, look: Look, figures: WidgetFigures, money: (Long) -> String) =
        RemoteViews(context.packageName, look.layout).apply {
            setOnClickPendingIntent(R.id.widget_root, open(context, Intent.ACTION_MAIN, 0))
            if (look.month) {
                setTextViewText(R.id.widget_title, figures.monthName)
                setTextViewText(R.id.widget_expense, money(figures.month.expense))
                setTextViewText(R.id.widget_income, money(figures.month.income))
                setTextViewText(R.id.widget_balance, money(figures.balance))
            } else {
                setTextViewText(R.id.widget_title, figures.dayName)
                setTextViewText(R.id.widget_expense, money(figures.today.expense))
                setTextViewText(R.id.widget_income, money(figures.today.income))
                setOnClickPendingIntent(R.id.widget_add, open(context, MainActivity.ACTION_ADD_BILL, 1))
            }
        }

    /** Redraws every widget on the home screen. Does nothing when none are placed. */
    suspend fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val placed = looks.map { it to manager.getAppWidgetIds(ComponentName(context, it.provider)) }.filter { it.second.isNotEmpty() }
        if (placed.isEmpty()) return
        val repo = (context.applicationContext as WalletApp).repository
        val snapshot = withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { repo.snapshot.first { it.ledgers.isNotEmpty() } } ?: return
        val settings = repo.store.settings.value
        val figures = WidgetFigures.of(snapshot, settings, LocalDate.now())
        val money = WidgetFigures.moneyText(settings)
        placed.forEach { (look, ids) -> manager.updateAppWidget(ids, views(context, look, figures, money)) }
    }

    private const val LOAD_TIMEOUT_MILLIS = 8_000L
}

abstract class WalletWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        (context.applicationContext as WalletApp).scope.launch {
            try {
                WalletWidgets.update(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class TodayLightWidget : WalletWidget()
class TodayDarkWidget : WalletWidget()
class TodayLimeWidget : WalletWidget()
class MonthLightWidget : WalletWidget()
class MonthDarkWidget : WalletWidget()
class MonthLimeWidget : WalletWidget()
