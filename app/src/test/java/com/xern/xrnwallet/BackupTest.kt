package com.xern.xrnwallet

import com.xern.xrnwallet.data.Backup
import com.xern.xrnwallet.data.BackupReader
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.CurrencyRate
import com.xern.xrnwallet.data.Ledger
import com.xern.xrnwallet.data.Recurring
import com.xern.xrnwallet.data.Reminder
import com.xern.xrnwallet.data.SavingsPlan
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.UnusableBackup
import com.xern.xrnwallet.domain.RepeatKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val json = Json { encodeDefaults = true }

    private val rule = Recurring(
        id = 1,
        ledgerId = 1,
        type = BillType.EXPENSE,
        amountCents = 21000,
        currency = "AUD",
        startEpochDay = LocalDate.of(2026, 11, 1).toEpochDay(),
    )

    private val plan = SavingsPlan(id = 1, name = "Trip", targetCents = 100_000, currency = "AUD", startEpochDay = rule.startEpochDay)

    private fun backup(
        settings: Settings = Settings(),
        recurring: List<Recurring> = listOf(rule),
        plans: List<SavingsPlan> = listOf(plan),
        reminders: List<Reminder> = listOf(Reminder(id = 1, name = "Evening")),
        rates: List<CurrencyRate> = listOf(CurrencyRate("VND", 0.00006)),
    ): String = json.encodeToString(
        Backup(
            settings = json.encodeToString(settings),
            ledgers = listOf(Ledger(id = 1, name = "General")),
            accounts = emptyList(),
            categories = emptyList(),
            tags = emptyList(),
            bills = emptyList(),
            billTags = emptyList(),
            budgets = emptyList(),
            recurring = recurring,
            plans = plans,
            deposits = emptyList(),
            checklists = emptyList(),
            checklistItems = emptyList(),
            reminders = reminders,
            rates = rates,
        ),
    )

    /** Why the copy is refused, or null when it is taken. */
    private fun refusal(text: String): String? = try {
        BackupReader.read(text)
        null
    } catch (e: UnusableBackup) {
        e.message
    }

    @Test
    fun aCopyTheAppWouldFailOnAtEveryLaunchIsRefusedSayingWhy() {
        assertNull(refusal(backup()))
        assertEquals(
            listOf(
                "its Thousands Separator is 3, which this app doesn't have",
                "its Decimal places is -1, which this app doesn't have",
                "its On weekends is 3, which this app doesn't have",
                "its Belonging month is 2, which this app doesn't have",
                "its first day of the week is 0, not 1 to 7",
                "its Start of Month is day 0",
            ),
            listOf(
                Settings(separators = 3),
                Settings(decimals = -1),
                Settings(weekendRule = 3),
                Settings(belonging = 2),
                Settings(firstDayOfWeek = 0),
                Settings(startDay = 0),
            ).map { refusal(backup(settings = it)) },
        )
        assertEquals(
            listOf(
                "a recurring item repeats \"HOURLY\", which this app doesn't know",
                "a recurring item repeats monthly on day 0",
                "a recurring item repeats weekly on weekday 8, not 1 to 7",
                "a recurring item is due at minute 1440 of the day",
                "a recurring item starts on no date the calendar has",
            ),
            listOf(
                rule.copy(repeatKind = "HOURLY"),
                rule.copy(repeatKind = RepeatKind.MONTHLY.name, repeatValue = 0),
                rule.copy(repeatKind = RepeatKind.WEEKLY.name, repeatValue = 8),
                rule.copy(minuteOfDay = 24 * 60),
                rule.copy(startEpochDay = Long.MAX_VALUE),
            ).map { refusal(backup(recurring = listOf(it))) },
        )
        assertEquals(
            listOf(
                "a savings plan is of the kind \"SOMETIMES\", which this app doesn't know",
                "a savings plan repeats \"HOURLY\", which this app doesn't know",
                "a savings plan starts on no date the calendar has",
            ),
            listOf(
                plan.copy(type = "SOMETIMES"),
                plan.copy(repeatKind = "HOURLY"),
                plan.copy(startEpochDay = -1_000_000_000L),
            ).map { refusal(backup(plans = listOf(it))) },
        )
        assertEquals(
            "a reminder is set for minute -5 of the day",
            refusal(backup(reminders = listOf(Reminder(id = 1, name = "Evening", minuteOfDay = -5)))),
        )
        assertEquals(
            listOf("VND has a rate of 0.0", "VND has a rate of -1.0"),
            listOf(0.0, -1.0).map { refusal(backup(rates = listOf(CurrencyRate("VND", it)))) },
        )
        assertEquals(
            "it was made by a newer version of the app",
            refusal(backup().replaceFirst("\"version\":1", "\"version\":2")),
        )
    }
}
