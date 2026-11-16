package com.xern.xrnwallet.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM Ledger ORDER BY sort, id") fun ledgers(): Flow<List<Ledger>>
    @Query("SELECT * FROM Account ORDER BY sort, id") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM Category ORDER BY sort, id") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM Tag ORDER BY sort, id") fun tags(): Flow<List<Tag>>
    @Query("SELECT * FROM Bill ORDER BY atMillis DESC, id DESC") fun bills(): Flow<List<Bill>>
    @Query("SELECT * FROM BillTag") fun billTags(): Flow<List<BillTag>>
    @Query("SELECT * FROM Budget") fun budgets(): Flow<List<Budget>>
    @Query("SELECT * FROM Recurring ORDER BY id") fun recurring(): Flow<List<Recurring>>
    @Query("SELECT * FROM SavingsPlan ORDER BY id DESC") fun plans(): Flow<List<SavingsPlan>>
    @Query("SELECT * FROM SavingsDeposit ORDER BY atMillis DESC") fun deposits(): Flow<List<SavingsDeposit>>
    @Query("SELECT * FROM Checklist ORDER BY sort, id") fun checklists(): Flow<List<Checklist>>
    @Query("SELECT * FROM ChecklistItem ORDER BY sort, id") fun checklistItems(): Flow<List<ChecklistItem>>
    @Query("SELECT * FROM Reminder ORDER BY id") fun reminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM CurrencyRate ORDER BY sort, code") fun rates(): Flow<List<CurrencyRate>>
    @Query("SELECT * FROM BalanceAdjustment ORDER BY atMillis DESC, id DESC") fun adjustments(): Flow<List<BalanceAdjustment>>

    @Query("SELECT * FROM Recurring") suspend fun recurringNow(): List<Recurring>
    @Query("SELECT * FROM Reminder") suspend fun remindersNow(): List<Reminder>
    @Query("SELECT * FROM Category") suspend fun categoriesNow(): List<Category>
    @Query("SELECT * FROM Account") suspend fun accountsNow(): List<Account>
    @Query("SELECT * FROM Ledger") suspend fun ledgersNow(): List<Ledger>
    @Query("SELECT * FROM Tag") suspend fun tagsNow(): List<Tag>
    @Query("SELECT * FROM CurrencyRate") suspend fun ratesNow(): List<CurrencyRate>
    @Query("SELECT * FROM Bill") suspend fun billsNow(): List<Bill>
    @Query("SELECT * FROM BillTag") suspend fun billTagsNow(): List<BillTag>
    @Query("SELECT * FROM Budget") suspend fun budgetsNow(): List<Budget>
    @Query("SELECT * FROM SavingsPlan") suspend fun plansNow(): List<SavingsPlan>
    @Query("SELECT * FROM SavingsDeposit") suspend fun depositsNow(): List<SavingsDeposit>
    @Query("SELECT * FROM Checklist") suspend fun checklistsNow(): List<Checklist>
    @Query("SELECT * FROM ChecklistItem") suspend fun checklistItemsNow(): List<ChecklistItem>
    @Query("SELECT * FROM BalanceAdjustment") suspend fun adjustmentsNow(): List<BalanceAdjustment>
    @Query("SELECT COUNT(*) FROM Ledger") suspend fun ledgerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Ledger): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Account): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Category): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Tag): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Bill): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Budget): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Recurring): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: SavingsPlan): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: SavingsDeposit): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Checklist): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: ChecklistItem): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Reminder): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: CurrencyRate)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: BalanceAdjustment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBillTags(values: List<BillTag>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBills(values: List<Bill>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCategories(values: List<Category>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAccounts(values: List<Account>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLedgers(values: List<Ledger>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTags(values: List<Tag>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRates(values: List<CurrencyRate>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudgets(values: List<Budget>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRecurring(values: List<Recurring>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlans(values: List<SavingsPlan>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDeposits(values: List<SavingsDeposit>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChecklists(values: List<Checklist>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putChecklistItems(values: List<ChecklistItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putReminders(values: List<Reminder>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAdjustments(values: List<BalanceAdjustment>)

    @Delete suspend fun delete(value: Ledger)
    @Delete suspend fun delete(value: Account)
    @Delete suspend fun delete(value: Tag)
    @Delete suspend fun delete(value: Budget)
    /** Sets only the link, so a bill keeps its tags, amount and everything else. */
    @Query("UPDATE Bill SET recurringId = :ruleId WHERE id IN (:billIds)")
    suspend fun linkBills(ruleId: Long, billIds: List<Long>)

    @Delete suspend fun delete(value: Recurring)
    @Delete suspend fun delete(value: SavingsPlan)
    @Delete suspend fun delete(value: Checklist)
    @Delete suspend fun delete(value: ChecklistItem)
    @Delete suspend fun delete(value: Reminder)
    @Delete suspend fun delete(value: CurrencyRate)
    @Delete suspend fun delete(value: BalanceAdjustment)

    @Query("DELETE FROM Bill WHERE id = :id") suspend fun deleteBill(id: Long)
    @Query("DELETE FROM Category WHERE id = :id OR parentId = :id") suspend fun deleteCategory(id: Long)
    @Query("DELETE FROM BillTag WHERE billId = :billId") suspend fun deleteBillTags(billId: Long)
    @Query("DELETE FROM BillTag WHERE tagId = :tagId") suspend fun deleteTagLinks(tagId: Long)
    @Query("DELETE FROM Bill WHERE ledgerId = :ledgerId") suspend fun deleteBillsOfLedger(ledgerId: Long)
    @Query("DELETE FROM BillTag WHERE billId IN (SELECT id FROM Bill WHERE ledgerId = :ledgerId)")
    suspend fun deleteBillTagsOfLedger(ledgerId: Long)
    @Query("DELETE FROM Recurring WHERE ledgerId = :ledgerId") suspend fun deleteRecurringOfLedger(ledgerId: Long)
    @Query("DELETE FROM Budget WHERE categoryId = :categoryId") suspend fun deleteCategoryBudget(categoryId: Long)
    @Query("DELETE FROM ChecklistItem WHERE checklistId = :id") suspend fun deleteItemsOf(id: Long)
    @Query("DELETE FROM SavingsDeposit WHERE planId = :id") suspend fun deleteDepositsOf(id: Long)
    @Query("DELETE FROM BalanceAdjustment WHERE accountId = :id") suspend fun deleteAdjustmentsOf(id: Long)
    @Query("UPDATE Bill SET accountId = NULL WHERE accountId = :id") suspend fun detachAccount(id: Long)
    @Query("UPDATE Bill SET toAccountId = NULL WHERE toAccountId = :id") suspend fun detachToAccount(id: Long)

    @Query("DELETE FROM Bill") suspend fun clearBills()
    @Query("DELETE FROM BillTag") suspend fun clearBillTags()
    @Query("DELETE FROM Ledger") suspend fun clearLedgers()
    @Query("DELETE FROM Account") suspend fun clearAccounts()
    @Query("DELETE FROM Category") suspend fun clearCategories()
    @Query("DELETE FROM Tag") suspend fun clearTags()
    @Query("DELETE FROM Budget") suspend fun clearBudgets()
    @Query("DELETE FROM Recurring") suspend fun clearRecurring()
    @Query("DELETE FROM SavingsPlan") suspend fun clearPlans()
    @Query("DELETE FROM SavingsDeposit") suspend fun clearDeposits()
    @Query("DELETE FROM Checklist") suspend fun clearChecklists()
    @Query("DELETE FROM ChecklistItem") suspend fun clearChecklistItems()
    @Query("DELETE FROM Reminder") suspend fun clearReminders()
    @Query("DELETE FROM CurrencyRate") suspend fun clearRates()
    @Query("DELETE FROM BalanceAdjustment") suspend fun clearAdjustments()

    /** An account, with the bills whose fixed amounts were re-expressed for a change of its currency. */
    @Transaction
    suspend fun putAccount(account: Account, bills: List<Bill>) {
        putBills(bills)
        put(account)
    }

    @Transaction
    suspend fun clearEverything() {
        clearBillTags(); clearBills(); clearLedgers(); clearAccounts(); clearCategories(); clearTags()
        clearBudgets(); clearRecurring(); clearDeposits(); clearPlans(); clearChecklistItems()
        clearChecklists(); clearReminders(); clearRates(); clearAdjustments()
    }
}

@Database(
    entities = [
        Ledger::class, Account::class, Category::class, Tag::class, Bill::class, BillTag::class,
        Budget::class, Recurring::class, SavingsPlan::class, SavingsDeposit::class, Checklist::class,
        ChecklistItem::class, Reminder::class, CurrencyRate::class, BalanceAdjustment::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wallet.db")
                .addMigrations(ADD_RECURRING_OPTIONS, ADD_BALANCE_ADJUSTMENTS)
                .build()

        /** Version 2 gives recurring rules an interval, a budget switch and tags. */
        private val ADD_RECURRING_OPTIONS = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Recurring ADD COLUMN every INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE Recurring ADD COLUMN inBudget INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE Recurring ADD COLUMN tagIds TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Version 3 keeps corrections of account balances. Nothing already there changes. */
        val ADD_BALANCE_ADJUSTMENTS = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `BalanceAdjustment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, `atMillis` INTEGER NOT NULL, `beforeCents` INTEGER NOT NULL, " +
                        "`afterCents` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_BalanceAdjustment_accountId` ON `BalanceAdjustment` (`accountId`)")
            }
        }
    }
}
