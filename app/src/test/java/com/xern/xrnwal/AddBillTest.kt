package com.xern.xrnwal

import com.xern.xrnwal.data.Account
import com.xern.xrnwal.data.Bill
import com.xern.xrnwal.data.BillType
import com.xern.xrnwal.data.CurrencyRate
import com.xern.xrnwal.data.Settings
import com.xern.xrnwal.data.SettingsStore
import com.xern.xrnwal.data.Snapshot
import com.xern.xrnwal.ui.BillStart
import com.xern.xrnwal.ui.billStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Which account the add page starts on. */
class AddBillTest {
    private val cash = Account(id = 1, name = "Cash")
    private val card = Account(id = 2, name = "Everyday")
    private val credit = Account(id = 3, name = "Credit card")
    private val today = LocalDate.of(2026, 11, 19)

    private fun bill(id: Long, date: LocalDate, account: Long?, type: Int = BillType.EXPENSE, rule: Long? = null, to: Long? = null) = Bill(
        id = id, ledgerId = 1, type = type, accountId = account, toAccountId = to, amountCents = 1_000, currency = "AUD",
        atMillis = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), recurringId = rule,
    )

    /** As the database lists them: newest date first. */
    private fun snapshot(vararg bills: Bill, accounts: List<Account> = listOf(cash, card, credit)) =
        Snapshot(accounts = accounts, bills = bills.sortedByDescending { it.atMillis })

    @Test
    fun aNewBillStartsOnTheAccountOfTheBillEnteredLastNotTheNewestDate() {
        val s = snapshot(
            // Next month's rent, entered weeks ago: the newest date, on the card.
            bill(10, LocalDate.of(2026, 12, 1), card.id),
            // Today's bill from a recurring rule, on cash.
            bill(11, today, cash.id, rule = 4),
            // A transfer entered after both.
            bill(12, today, card.id, type = BillType.TRANSFER, to = credit.id),
            // The bill typed in last, dated yesterday, on the credit card.
            bill(9, today.minusDays(3), cash.id),
            bill(13, today.minusDays(1), credit.id),
        )
        assertEquals(BillStart(credit.id, "AUD"), s.billStart("AUD"))
    }

    @Test
    fun theAccountRememberedComesFirst() {
        val s = snapshot(bill(13, today.minusDays(1), credit.id))
        assertEquals(BillStart(cash.id, "AUD"), s.billStart("AUD", remembered = cash.id))
    }

    @Test
    fun anArchivedOrDeletedAccountIsPassedOver() {
        val archived = credit.copy(archived = true)
        val s = snapshot(bill(13, today, credit.id), accounts = listOf(cash, card, archived))
        assertEquals(BillStart(cash.id, "AUD"), s.billStart("AUD", remembered = archived.id))
        assertEquals(BillStart(cash.id, "AUD"), s.billStart("AUD", remembered = 99))
    }

    @Test
    fun aRememberedAccountStartsInItsOwnCurrency() {
        val abroad = Account(id = 5, name = "US card", currency = "USD")
        val s = Snapshot(accounts = listOf(cash, abroad), rates = listOf(CurrencyRate("USD", 1.53)))
        assertEquals(BillStart(abroad.id, "USD"), s.billStart("AUD", remembered = abroad.id))
    }

    @Test
    fun aNewExpenseOrIncomeIsRememberedAcrossARestart() {
        val prefs = MemoryPrefs()
        SettingsStore(prefs).update { it.recorded(bill(0, today, credit.id), new = true) }
        val reopened = SettingsStore(prefs).settings.value
        assertEquals(credit.id, reopened.lastAccountId)
        assertEquals(BillStart(credit.id, "AUD"), snapshot(bill(10, today, card.id)).billStart("AUD", reopened.lastAccountId))
    }

    @Test
    fun editsTransfersAndBillsWithNoAccountLeaveItAsItWas() {
        val chosen = Settings(lastAccountId = cash.id)
        assertEquals(chosen, chosen.recorded(bill(7, today, credit.id), new = false))
        assertEquals(chosen, chosen.recorded(bill(0, today, credit.id, type = BillType.TRANSFER, to = card.id), new = true))
        assertEquals(chosen, chosen.recorded(bill(0, today, null), new = true))
        assertEquals(card.id, chosen.recorded(bill(0, today, card.id, type = BillType.INCOME), new = true).lastAccountId)
        assertNull(Settings().lastAccountId)
    }
}
