package com.xern.xrnwallet

import com.xern.xrnwallet.data.Account
import com.xern.xrnwallet.data.AlreadyStored
import com.xern.xrnwallet.data.Bill
import com.xern.xrnwallet.data.BillCsv
import com.xern.xrnwallet.data.BillType
import com.xern.xrnwallet.data.Category
import com.xern.xrnwallet.data.Ledger
import com.xern.xrnwallet.data.Snapshot
import com.xern.xrnwallet.domain.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CsvTest {
    @Test
    fun parserHandlesQuotesNewlinesAndBom() {
        val rows = Csv.parse("﻿a,b\r\n\"x,1\",\"say \"\"hi\"\"\"\n\"two\nlines\",z")
        assertEquals(listOf("a", "b"), rows[0])
        assertEquals(listOf("x,1", "say \"hi\""), rows[1])
        assertEquals(listOf("two\nlines", "z"), rows[2])
    }

    @Test
    fun writerRoundTripsAwkwardCells() {
        val table = listOf(listOf("Note", "Amount"), listOf("a, \"b\"\nc", "1.50"))
        assertEquals(table, Csv.parse(Csv.write(table)))
    }

    private val otherAppExport =
        "Date,Category,Subcategory,Bill type,Amount,Currency,Notes,Account,Ledger,Tags,Included in budget,Id,Image\r\n" +
            "2026-10-05 19:30:00,Food,,Expenses,18.95,AUD,Lunch,Revolut,General,,1,1_1,\r\n" +
            "2026-10-04 12:00:00,Gift,,Expenses,1200000.0,VND,,Bank,General,Data,0,1_2,\r\n" +
            "2026-10-03 09:00:00,\"Savings \nRevolut\",,Transfer,100.0,AUD,Top up,Savings ,,,,1_3,\r\n"

    @Test
    fun readsAnExportWithBothTransferAccountsInCategory() {
        val result = BillCsv.read(otherAppExport, "AUD")
        assertTrue(result.skipped.isEmpty())
        val (food, gift, transfer) = result.rows
        assertEquals(BillType.EXPENSE, food.type)
        assertEquals("Food", food.category)
        assertTrue(food.inBudget)
        assertEquals(120000000L, gift.cents)
        assertEquals(listOf("Data"), gift.tags)
        assertFalse(gift.inBudget)
        assertEquals(BillType.TRANSFER, transfer.type)
        assertEquals("Savings", transfer.account)
        assertEquals("Revolut", transfer.toAccount)
        assertEquals("", transfer.category)
        assertEquals("", transfer.ledger)
    }

    @Test
    fun aForeignBillNeedsARate() {
        val rows = BillCsv.read(otherAppExport, "AUD").rows
        assertEquals(listOf("VND"), BillCsv.missingRates(rows, "AUD", emptySet()))
        assertTrue(BillCsv.missingRates(rows, "AUD", setOf("VND")).isEmpty())
        assertTrue(BillCsv.missingRates(rows.map { it.copy(rate = 0.00005) }, "AUD", emptySet()).isEmpty())
        assertTrue(BillCsv.missingRates(rows, "VND", setOf("AUD")).isEmpty())
    }

    @Test
    fun aRateThatIsNotAPositiveNumberCountsAsMissing() {
        val file = "Date,Amount,Currency,Rate\r\n" +
            "2026-10-01,10,VND,0\r\n" +
            "2026-10-02,10,VND,-2\r\n" +
            "2026-10-03,10,VND,NaN\r\n" +
            "2026-10-04,10,VND,Infinity\r\n" +
            "2026-10-05,10,VND,\"0,00006\"\r\n"
        val rows = BillCsv.read(file, "AUD").rows
        assertEquals(listOf(null, null, null, null, 0.00006), rows.map { it.rate })
        // So the import asks for a rate first, instead of counting every bill as nothing, or not opening again.
        assertEquals(listOf("VND"), BillCsv.missingRates(rows, "AUD", emptySet()))
    }

    @Test
    fun aNewAccountTakesACurrencyOnlyWhenAllItsBillsAgree() {
        val rows = BillCsv.read(otherAppExport, "AUD").rows
        val paypal = rows[0].copy(account = "PayPal", currency = "USD")
        val mixed = rows[0].copy(account = "Bank", currency = "AUD")
        val found = BillCsv.accountCurrencies(rows + paypal + mixed, "AUD")
        assertEquals("USD", found["paypal"])
        assertEquals("AUD", found["bank"])
        assertEquals("AUD", found["revolut"])
    }

    @Test
    fun templateReadsBack() {
        val result = BillCsv.read(BillCsv.template(), "AUD")
        assertTrue(result.skipped.isEmpty())
        assertEquals(listOf(BillType.EXPENSE, BillType.INCOME, BillType.TRANSFER), result.rows.map { it.type })
        assertEquals(1895L, result.rows[0].cents)
        assertEquals("Savings", result.rows[2].toAccount)
    }

    @Test
    fun exportReadsBackExactly() {
        val snapshot = Snapshot(
            ledgers = listOf(Ledger(id = 1, name = "General")),
            accounts = listOf(Account(id = 1, name = "Bank", currency = "VND")),
            categories = listOf(
                Category(id = 1, type = BillType.EXPENSE, name = "Food", icon = "x"),
                Category(id = 2, type = BillType.EXPENSE, name = "Lunch", icon = "x", parentId = 1),
            ),
            bills = listOf(
                Bill(
                    id = 1, ledgerId = 1, type = BillType.EXPENSE, categoryId = 2, accountId = 1,
                    amountCents = 120_000_000, currency = "VND", rate = 0.00005548, note = "a, b",
                    atMillis = BillCsv.millisOf(LocalDateTime.of(2026, 10, 4, 7, 30)), inBudget = false,
                ),
            ),
        )
        val row = BillCsv.read(BillCsv.write(snapshot, snapshot.bills), "AUD").rows.single()
        assertEquals(LocalDateTime.of(2026, 10, 4, 7, 30), row.at)
        assertEquals("Food", row.category)
        assertEquals("Lunch", row.subcategory)
        assertEquals(120_000_000L, row.cents)
        assertEquals("VND", row.currency)
        assertEquals(0.00005548, row.rate!!, 1e-12)
        assertEquals("a, b", row.note)
        assertFalse(row.inBudget)
    }

    @Test
    fun columnsAreFoundByNameInAnyOrder() {
        val text = "Note,Money,Bill Date,Ledger Name\nLunch,-12.50,2026/10/04 07:30,General\nPay,650,2026/10/05,General\n"
        val rows = BillCsv.read(text, "AUD").rows
        assertEquals(2, rows.size)
        assertEquals(BillType.EXPENSE, rows[0].type)
        assertEquals(1250L, rows[0].cents)
        assertEquals(BillType.INCOME, rows[1].type)
    }

    @Test
    fun unreadableRowsAreReportedNotDropped() {
        val result = BillCsv.read("Date,Amount\n2026-10-04,5\nnonsense,5\n2026-10-05,abc\n", "AUD")
        assertEquals(1, result.rows.size)
        assertEquals(2, result.skipped.size)
    }

    @Test
    fun identicalRowsInOneFileAreBothKept() {
        val empty = AlreadyStored(emptyList<String>())
        assertFalse(empty.claim("coffee"))
        assertFalse(empty.claim("coffee"))
    }

    @Test
    fun importingTheSameFileTwiceAddsNothing() {
        val stored = AlreadyStored(listOf("coffee", "coffee", "rent"))
        assertTrue(stored.claim("coffee"))
        assertTrue(stored.claim("coffee"))
        assertTrue(stored.claim("rent"))
        assertFalse("a third identical row is new", stored.claim("coffee"))
    }
}
