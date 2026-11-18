package com.xern.xrnwallet

import com.xern.xrnwallet.data.Account
import com.xern.xrnwallet.data.Backup
import com.xern.xrnwallet.data.BackupReader
import com.xern.xrnwallet.data.BalanceAdjustment
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillCsv
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Ledger
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.data.UnusableBackup
import com.xern.xrnwallet.data.inRange
import com.xern.xrnwallet.domain.Csv
import com.xern.xrnwallet.domain.DateRange
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone

/** A bill's date and time are those it was entered with, wherever the phone is now. */
class BillDateTest {
    private val home = TimeZone.getDefault()

    @After
    fun putTheZoneBack() = TimeZone.setDefault(home)

    private fun zone(id: String) = TimeZone.setDefault(TimeZone.getTimeZone(id))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val lateOnTheLastDay = LocalDateTime.of(2026, 10, 31, 21, 30)

    private fun dinner() = Bill(
        id = 1, ledgerId = 1, type = BillType.EXPENSE, amountCents = 25_000_000, currency = "VND",
        atMillis = BillCsv.millisOf(lateOnTheLastDay),
    )

    @Test
    fun aBillStaysOnItsDayWhenThePhoneMovesToAnotherTimeZone() {
        zone("Asia/Ho_Chi_Minh")
        val bill = dinner()
        assertEquals(lateOnTheLastDay, bill.dateTime)

        // Four hours ahead, 21:30 in Hanoi is 01:30 on 1 November.
        zone("Australia/Sydney")
        assertEquals(lateOnTheLastDay, bill.dateTime)
        assertEquals(LocalDate.of(2026, 10, 31), bill.date)
        val october = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(listOf(bill), listOf(bill).inRange(october))

        // A file written there says what was entered.
        val written = Csv.parse(BillCsv.write(Snapshot(ledgers = listOf(Ledger(id = 1, name = "General"))), listOf(bill)))
        assertEquals(listOf("2026-10-31", "21:30:00"), written[1].take(2))

        // And it travels in a copy.
        assertEquals(lateOnTheLastDay, json.decodeFromString<Bill>(json.encodeToString(bill)).dateTime)
    }

    @Test
    fun aBillFromACopyMadeBeforeKeepsTheDayItHadWhereItWasFirstRead() {
        zone("Asia/Ho_Chi_Minh")
        val older = """{"id":1,"ledgerId":1,"type":0,"amountCents":25000000,"currency":"VND","atMillis":${dinner().atMillis}}"""
        val bill = json.decodeFromString<Bill>(older)
        zone("Australia/Sydney")
        assertEquals(lateOnTheLastDay, bill.dateTime)
    }

    @Test
    fun aFileWrittenInOneZoneMatchesItsBillsReadInAnother() {
        zone("Asia/Ho_Chi_Minh")
        val bill = dinner()
        val file = BillCsv.write(Snapshot(ledgers = listOf(Ledger(id = 1, name = "General"))), listOf(bill))
        zone("Australia/Sydney")
        val row = BillCsv.read(file, "AUD").rows.single()
        assertEquals(
            BillCsv.fingerprint(bill.dateTime, bill.amountCents, bill.note, bill.type),
            BillCsv.fingerprint(row.at, row.cents, row.note, row.type),
        )
    }

    @Test
    fun aCopyWithAnOffsetNoZoneHasIsRefused() {
        val text = json.encodeToString(
            Backup(
                settings = json.encodeToString(Settings()),
                ledgers = listOf(Ledger(id = 1, name = "General")),
                accounts = emptyList(), categories = emptyList(), tags = emptyList(),
                bills = listOf(dinner().copy(offsetSeconds = 25 * 3600)),
                billTags = emptyList(), budgets = emptyList(), recurring = emptyList(), plans = emptyList(),
                deposits = emptyList(), checklists = emptyList(), checklistItems = emptyList(), reminders = emptyList(),
                rates = emptyList(),
            ),
        )
        val refused = runCatching { BackupReader.read(text) }.exceptionOrNull()
        assertEquals("a bill is dated 90000 seconds from UTC", (refused as UnusableBackup).message)
        assertEquals(1, BackupReader.read(text.replace("90000", "25200")).bills.size)
    }

    @Test
    fun aCorrectionStaysOnItsDayToo() {
        zone("Asia/Ho_Chi_Minh")
        val wallet = Account(id = 1, name = "Wallet", currency = "VND")
        val correction = Snapshot(accounts = listOf(wallet)).correction(wallet, wanted = 100_000_000, atMillis = BillCsv.millisOf(lateOnTheLastDay))
        zone("Australia/Sydney")
        assertEquals(LocalDate.of(2026, 10, 31), correction.date)
        assertEquals(lateOnTheLastDay, json.decodeFromString<BalanceAdjustment>(json.encodeToString(correction)).dateTime)
    }
}
