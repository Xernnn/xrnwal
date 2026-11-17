package com.xern.xrnwallet.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xern.xrnwallet.MainActivity
import com.xern.xrnwallet.R
import com.xern.xrnwallet.WalletApp
import com.xern.xrnwallet.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as WalletApp).repository.runRecurring()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "recurring",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RecurringWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}

/** Catches what a sync in the open app can't: a phone that was offline, or left the app closed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as WalletApp).sync
        if (!sync.autoSyncing) return Result.success()
        return if (sync.syncNow() && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "sync"
        private const val RETRY = "sync-retry"
        private const val MAX_ATTEMPTS = 5

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun apply(context: Context, on: Boolean) {
            val work = WorkManager.getInstance(context)
            if (on) {
                work.enqueueUniquePeriodicWork(
                    PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build(),
                )
            } else {
                work.cancelUniqueWork(PERIODIC)
                work.cancelUniqueWork(RETRY)
            }
        }

        fun retrySoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                RETRY,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(online)
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}

object Reminders {
    private const val CHANNEL = "reminders"
    private const val EXTRA_ID = "id"

    private fun intent(context: Context, id: Long) = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * When [reminder] next goes off after [now]: a weekly one on its start's weekday, a monthly one on its
     * start's day of the month, or the month's last day when it has fewer. Worked out from the start, not
     * from today, so setting the alarm again after a restart, a save, a restore or a sync keeps its day.
     */
    fun next(reminder: Reminder, now: LocalDateTime): LocalDateTime {
        val time = LocalTime.of(reminder.minuteOfDay / 60, reminder.minuteOfDay % 60)
        val day = reminder.start.dayOfMonth
        fun inMonth(month: YearMonth) = month.atDay(minOf(day, month.lengthOfMonth())).atTime(time)
        return when (reminder.repeat) {
            1 -> now.toLocalDate().with(TemporalAdjusters.nextOrSame(reminder.start.dayOfWeek)).atTime(time)
                .let { if (it.isAfter(now)) it else it.plusWeeks(1) }
            2 -> inMonth(YearMonth.from(now)).let { if (it.isAfter(now)) it else inMonth(YearMonth.from(now).plusMonths(1)) }
            else -> now.toLocalDate().atTime(time).let { if (it.isAfter(now)) it else it.plusDays(1) }
        }
    }

    fun schedule(context: Context, reminder: Reminder) {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (!reminder.enabled) {
            manager.cancel(intent(context, reminder.id))
            return
        }
        val millis = next(reminder, LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(context, reminder.id))
    }

    fun scheduleAll(context: Context, reminders: List<Reminder>) = reminders.forEach { schedule(context, it) }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context, id))
    }

    fun show(context: Context, reminder: Reminder) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(reminder.id.toInt(), notification)
    }

    fun idFrom(intent: Intent): Long = intent.getLongExtra(EXTRA_ID, -1)
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as WalletApp
        val id = Reminders.idFrom(intent)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = app.repository.dao.remindersNow()
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    Reminders.scheduleAll(context, reminders)
                } else {
                    reminders.firstOrNull { it.id == id }?.let {
                        Reminders.show(context, it)
                        Reminders.schedule(context, it)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
