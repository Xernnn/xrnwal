package com.xern.xrnwal

import android.app.Application
import com.xern.xrnwal.data.AppDatabase
import com.xern.xrnwal.data.Repository
import com.xern.xrnwal.data.SettingsStore
import com.xern.xrnwal.data.SyncManager
import com.xern.xrnwal.work.RecurringWorker
import com.xern.xrnwal.work.Reminders
import com.xern.xrnwal.work.WalletWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private const val WIDGET_REFRESH_DELAY_MILLIS = 1_000L

class WalletApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: Repository by lazy {
        Repository(this, AppDatabase.build(this), SettingsStore(this), scope)
    }

    val sync: SyncManager by lazy { SyncManager(this, repository, scope) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Widgets draw times too, sometimes before any page has opened.
        com.xern.xrnwal.domain.Dates.use24Hour = android.text.format.DateFormat.is24HourFormat(this)
        scope.launch {
            repository.seedIfEmpty()
            repository.runRecurring()
            // Alarms go when the app is force-stopped, as some phones do to apps swiped away.
            Reminders.scheduleAll(this@WalletApp, repository.dao.remindersNow())
        }
        // As the app starts, before a deleted bill's Undo can be on offer, and between syncs.
        scope.launch { sync.unlessSyncing { repository.sweepPhotos() } }
        RecurringWorker.schedule(this)
        sync.start()
        scope.launch {
            // Settings count as well as data, so a newly set app lock hides the figures straight away.
            combine(repository.snapshot, repository.store.settings) { _, _ -> }
                .debounce(WIDGET_REFRESH_DELAY_MILLIS)
                .collectLatest { WalletWidgets.update(this@WalletApp) }
        }
    }
}
