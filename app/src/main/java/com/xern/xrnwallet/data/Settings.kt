package com.xern.xrnwallet.data

import android.content.Context
import com.xern.xrnwallet.domain.Belonging
import com.xern.xrnwallet.domain.DecimalMode
import com.xern.xrnwallet.domain.MoneyFormat
import com.xern.xrnwallet.domain.PeriodRules
import com.xern.xrnwallet.domain.SeparatorStyle
import com.xern.xrnwallet.domain.WeekendRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek

/** A ledger choice meaning every ledger at once. */
const val ALL_LEDGERS = 0L

@Serializable
data class Settings(
    val nickname: String = "Me",
    val avatar: String = "🦊",
    val themeMode: Int = THEME_DARK,
    val accent: Int = 0,
    val activeLedger: Long = ALL_LEDGERS,
    val baseCurrency: String = "AUD",
    val budgetEnabled: Boolean = true,
    val showTagsOnCard: Boolean = true,
    val checklistOnHome: Boolean = true,
    val startDay: Int = 1,
    val weekendRule: Int = 0,
    val belonging: Int = 0,
    val carryover: Boolean = true,
    val showBaseSymbol: Boolean = true,
    val separators: Int = SeparatorStyle.DOT_COMMA.ordinal,
    val decimals: Int = DecimalMode.ADAPTIVE.ordinal,
    val firstDayOfWeek: Int = 1,
    val cardStyle: Int = 1,
    val noteSuggestions: Boolean = false,
    val showAccountOnCard: Boolean = true,
    val expenseColor: Long = DEFAULT_EXPENSE,
    val incomeColor: Long = DEFAULT_INCOME,
    val expenseArrowColor: Long = DEFAULT_EXPENSE,
    val incomeArrowColor: Long = DEFAULT_INCOME,
    val arrowsReversed: Boolean = false,
    val includeFuture: Boolean = true,
    val hapticsOn: Boolean = true,
    val gestureTipSeen: Boolean = false,
    val welcomeSeen: Boolean = false,
    val hapticStrength: Int = 1,
    val hideAmounts: Boolean = false,
    val pinHash: String? = null,
    val patternHash: String? = null,
    val lockSalt: String = "",
    val biometric: Boolean = false,
    val ratesUpdatedAt: Long = 0,
) {
    val moneyFormat: MoneyFormat
        get() = MoneyFormat(SeparatorStyle.entries[separators], DecimalMode.entries[decimals])

    val periodRules: PeriodRules
        get() = PeriodRules(
            startDay = startDay,
            weekend = WeekendRule.entries[weekendRule],
            belonging = Belonging.entries[belonging],
            firstDayOfWeek = DayOfWeek.of(firstDayOfWeek),
        )

    val locked: Boolean get() = pinHash != null || patternHash != null || biometric

    /**
     * What here this app can't work with, or null. Each is looked up as the app opens, so one out of
     * range, from a backup file or server copy edited by hand, closed it on every launch.
     */
    fun problem(): String? = when {
        separators !in SeparatorStyle.entries.indices -> "its Thousands Separator is $separators, which this app doesn't have"
        decimals !in DecimalMode.entries.indices -> "its Decimal places is $decimals, which this app doesn't have"
        weekendRule !in WeekendRule.entries.indices -> "its On weekends is $weekendRule, which this app doesn't have"
        belonging !in Belonging.entries.indices -> "its Belonging month is $belonging, which this app doesn't have"
        firstDayOfWeek !in 1..7 -> "its first day of the week is $firstDayOfWeek, not 1 to 7"
        startDay !in 1..31 -> "its Start of Month is day $startDay"
        else -> null
    }

    /** The app lock belongs to this phone. It is never sent to the home server. */
    fun withoutLock(): Settings = withLockOf(Settings())

    fun withLockOf(other: Settings): Settings = copy(
        pinHash = other.pinHash,
        patternHash = other.patternHash,
        lockSalt = other.lockSalt,
        biometric = other.biometric,
    )

    companion object {
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2
        const val DEFAULT_EXPENSE = 0xFFFF3B30
        const val DEFAULT_INCOME = 0xFF00D639
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(load())

    val settings: StateFlow<Settings> = state

    private fun load(): Settings = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() }
        ?: Settings()

    fun update(change: (Settings) -> Settings) {
        val next = change(state.value)
        state.value = next
        prefs.edit().putString(KEY, json.encodeToString(next)).apply()
    }

    fun export(): String = json.encodeToString(state.value)

    fun exportWithoutLock(): String = json.encodeToString(state.value.withoutLock())

    fun restore(text: String) {
        val restored = json.decodeFromString<Settings>(text)
        state.value = restored
        prefs.edit().putString(KEY, text).apply()
    }

    fun restoreKeepingLock(text: String) {
        val restored = json.decodeFromString<Settings>(text)
        update { restored.withLockOf(it) }
    }

    private companion object {
        const val KEY = "settings"
    }
}
