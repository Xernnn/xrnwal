package com.xern.xrnwallet

import com.xern.xrnwallet.data.BACKUP_VERSION
import com.xern.xrnwallet.data.BalanceAdjustment
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
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

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
        adjustments: List<BalanceAdjustment> = emptyList(),
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
            adjustments = adjustments,
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
            refusal(backup().replaceFirst("\"version\":$BACKUP_VERSION", "\"version\":${BACKUP_VERSION + 1}")),
        )
    }

    @Test
    fun balanceCorrectionsGoWithACopyAndOneMadeBeforeThemStillReads() {
        val correction = BalanceAdjustment(id = 3, accountId = 1, atMillis = 2_000, beforeCents = 5_000, afterCents = 25_000)
        val text = backup(adjustments = listOf(correction))
        // An app from before them would drop them and send the copy back without, so it must refuse it.
        assertEquals(2, BACKUP_VERSION)
        assertEquals(listOf(correction), BackupReader.read(text).adjustments)
        val older = text.replaceFirst("\"version\":2", "\"version\":1").replace(Regex(",\"adjustments\":\\[[^\\]]*\\]"), "")
        assertEquals(false, "adjustments" in older)
        assertEquals(emptyList<BalanceAdjustment>(), BackupReader.read(older).adjustments)
    }

    @Test
    fun cloudBackupTakesNothingWhileANewPhoneCanStillBeGivenEverything() {
        fun read(path: String): Element =
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path)).documentElement
        val android = "http://schemas.android.com/apk/res/android"
        val application = read("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        // All off before Android 12. From 12 it is deprecated, and on some phones stops only the cloud.
        assertEquals("false", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
        val rules = read("src/main/res/xml/data_extraction_rules.xml")
        fun section(name: String) = rules.getElementsByTagName(name).let { if (it.length == 0) null else it.item(0) as Element }
        val cloud = section("cloud-backup") ?: error("no cloud-backup")
        val excluded = (0 until cloud.getElementsByTagName("exclude").length).map {
            (cloud.getElementsByTagName("exclude").item(it) as Element).let { e -> e.getAttribute("domain") to e.getAttribute("path") }
        }
        assertEquals(listOf("root", "file", "database", "sharedpref", "external", "device_root").map { it to "." }, excluded)
        assertEquals(0, cloud.getElementsByTagName("include").length)
        // A move to a new phone keeps its default, everything.
        assertNull(section("device-transfer"))
    }
}
