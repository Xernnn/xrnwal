package com.xern.xrnwallet.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Amounts are stored as Long hundredths of a currency unit, whatever the currency. */
object Money {
    const val SCALE = 100L

    /** The most digits an amount is read with, before or after the point. A Long of cents runs out sooner. */
    private const val MAX_DIGITS = 18

    fun toCents(value: BigDecimal): Long =
        value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

    fun fromCents(cents: Long): BigDecimal = BigDecimal.valueOf(cents, 2)

    /** One mark, two or more times, each before three digits: only thousands are written that way. */
    private val thousands = Regex("[+-]?\\d{1,3}(?:[.,]\\d{3}){2,}")

    /** One mark, once, before exactly three digits, as in "500.000" or "1,500": thousands or a decimal. */
    private val either = Regex("[+-]?[1-9]\\d{0,2}[.,]\\d{3}")

    /** Whether [code] has no cents, as ISO 4217 has it for the dong, yen and won. False for a code it doesn't know. */
    fun wholeUnits(code: String): Boolean =
        runCatching { java.util.Currency.getInstance(code).defaultFractionDigits == 0 }.getOrDefault(false)

    /**
     * Parses "1234.5", "1234,5", "1.234,50", "1,234.50", "1.234.567" or "1,234,567" into cents, or null.
     * One mark once before exactly three digits, as in "500.000", reads either way. Typed in a field,
     * [decimalMark] is that of the number format chosen, so the other mark is a thousands one, and with
     * [wholeUnits], for a currency without cents such as the dong, either mark is. With neither, as for a
     * CSV file, which doesn't say, it is a decimal.
     */
    fun parse(text: String, decimalMark: Char? = null, wholeUnits: Boolean = false): Long? {
        val cleaned = text.trim().replace(" ", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        if (either.matches(cleaned) && (wholeUnits || (decimalMark != null && decimalMark !in cleaned))) {
            return cleaned.replace(".", "").replace(",", "").toBigDecimalOrNull()?.let(::centsOrNull)
        }
        val lastDot = cleaned.lastIndexOf('.')
        val lastComma = cleaned.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimalIsComma = lastComma > lastDot
                val grouping = if (decimalIsComma) "." else ","
                val decimal = if (decimalIsComma) "," else "."
                cleaned.replace(grouping, "").replace(decimal, ".")
            }
            // As amounts are shown here, "1.500.000". Read as a decimal it was no number at all.
            thousands.matches(cleaned) -> cleaned.replace(".", "").replace(",", "")
            lastComma >= 0 -> cleaned.replace(',', '.')
            else -> cleaned
        }
        return normalized.toBigDecimalOrNull()?.let(::centsOrNull)
    }

    /**
     * Null for a number no amount could be. One past what a Long holds made [toCents] throw, and an
     * exponent like 1e999999999 kept it working out the digits until memory ran out.
     */
    fun centsOrNull(value: BigDecimal): Long? {
        if (value.scale() !in -MAX_DIGITS..MAX_DIGITS || value.precision() - value.scale() > MAX_DIGITS) return null
        return try {
            toCents(value)
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** Converts native cents to base cents with a rate meaning "one unit in base currency". */
    fun convert(cents: Long, rate: Double): Long = (cents * rate).roundToLong()
}

enum class SeparatorStyle(val label: String) {
    COMMA_DOT("$1,000.01"),
    DOT_COMMA("$1.000,01"),
    ADAPTIVE("Adaptive"),
}

enum class DecimalMode(val label: String) {
    ADAPTIVE("Adaptive"),
    ONE("1"),
    TWO("2"),
}

data class MoneyFormat(
    val separators: SeparatorStyle = SeparatorStyle.DOT_COMMA,
    val decimals: DecimalMode = DecimalMode.ADAPTIVE,
    val locale: Locale = Locale.getDefault(),
) {
    val groupChar: Char
        get() = when (separators) {
            SeparatorStyle.COMMA_DOT -> ','
            SeparatorStyle.DOT_COMMA -> '.'
            SeparatorStyle.ADAPTIVE -> DecimalFormatSymbols.getInstance(locale).groupingSeparator
        }

    val decimalChar: Char
        get() = when (separators) {
            SeparatorStyle.COMMA_DOT -> '.'
            SeparatorStyle.DOT_COMMA -> ','
            SeparatorStyle.ADAPTIVE -> DecimalFormatSymbols.getInstance(locale).decimalSeparator
        }

    /** Formats without a sign or symbol. */
    fun digits(cents: Long): String {
        val value = abs(cents)
        val whole = value / Money.SCALE
        val fraction = value % Money.SCALE
        val grouped = whole.toString().reversed().chunked(3).joinToString(groupChar.toString()).reversed()
        val fractionText = when (decimals) {
            DecimalMode.TWO -> "%02d".format(fraction)
            DecimalMode.ONE -> if (fraction % 10 == 0L) (fraction / 10).toString() else "%02d".format(fraction)
            DecimalMode.ADAPTIVE -> when {
                fraction == 0L -> ""
                fraction % 10 == 0L -> (fraction / 10).toString()
                else -> "%02d".format(fraction)
            }
        }
        return if (fractionText.isEmpty()) grouped else "$grouped$decimalChar$fractionText"
    }

    /** "$1.234,5", "-$12". A zero never carries a sign. */
    fun money(cents: Long, symbol: String): String {
        val sign = if (cents < 0) "-" else ""
        return "$sign$symbol${digits(cents)}"
    }

    /** "+$12" or "-$12", used on bill rows. */
    fun signed(cents: Long, symbol: String): String {
        val sign = when {
            cents > 0 -> "+"
            cents < 0 -> "-"
            else -> ""
        }
        return "$sign$symbol${digits(cents)}"
    }
}

data class CurrencyInfo(val code: String, val symbol: String, val name: String) {
    /** Flag emoji built from the ISO country prefix; EUR maps to the EU flag. */
    val flag: String
        get() {
            val country = code.take(2)
            if (country.length < 2 || !country.all { it in 'A'..'Z' }) return "💱"
            return String(Character.toChars(0x1F1E6 + (country[0] - 'A'))) +
                String(Character.toChars(0x1F1E6 + (country[1] - 'A')))
        }
}

object Currencies {
    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("AED", "د.إ", "UAE Dirham"), CurrencyInfo("AMD", "֏", "Armenian Dram"),
        CurrencyInfo("ANG", "ƒ", "Netherlands Antillean Guilder"), CurrencyInfo("ARS", "$", "Argentine Peso"),
        CurrencyInfo("AUD", "$", "Australian Dollar"), CurrencyInfo("AWG", "ƒ", "Aruban Florin"),
        CurrencyInfo("AZN", "₼", "Azerbaijani Manat"), CurrencyInfo("BDT", "৳", "Bangladeshi Taka"),
        CurrencyInfo("BGN", "лв", "Bulgarian Lev"), CurrencyInfo("BHD", ".د.ب", "Bahraini Dinar"),
        CurrencyInfo("BND", "$", "Brunei Dollar"), CurrencyInfo("BOB", "Bs", "Bolivian Boliviano"),
        CurrencyInfo("BRL", "R$", "Brazilian Real"), CurrencyInfo("CAD", "$", "Canadian Dollar"),
        CurrencyInfo("CHF", "Fr", "Swiss Franc"), CurrencyInfo("CLP", "$", "Chilean Peso"),
        CurrencyInfo("CNY", "¥", "Chinese Yuan"), CurrencyInfo("COP", "$", "Colombian Peso"),
        CurrencyInfo("CZK", "Kč", "Czech Koruna"), CurrencyInfo("DKK", "kr", "Danish Krone"),
        CurrencyInfo("EGP", "£", "Egyptian Pound"), CurrencyInfo("EUR", "€", "Euro"),
        CurrencyInfo("FJD", "$", "Fijian Dollar"), CurrencyInfo("GBP", "£", "British Pound"),
        CurrencyInfo("GEL", "₾", "Georgian Lari"), CurrencyInfo("GHS", "₵", "Ghanaian Cedi"),
        CurrencyInfo("HKD", "HK$", "Hong Kong Dollar"), CurrencyInfo("HUF", "Ft", "Hungarian Forint"),
        CurrencyInfo("IDR", "Rp", "Indonesian Rupiah"), CurrencyInfo("ILS", "₪", "Israeli Shekel"),
        CurrencyInfo("INR", "₹", "Indian Rupee"), CurrencyInfo("ISK", "kr", "Icelandic Krona"),
        CurrencyInfo("JOD", "د.ا", "Jordanian Dinar"), CurrencyInfo("JPY", "¥", "Japanese Yen"),
        CurrencyInfo("KES", "KSh", "Kenyan Shilling"), CurrencyInfo("KHR", "៛", "Cambodian Riel"),
        CurrencyInfo("KRW", "₩", "South Korean Won"), CurrencyInfo("KWD", "د.ك", "Kuwaiti Dinar"),
        CurrencyInfo("KZT", "₸", "Kazakhstani Tenge"), CurrencyInfo("LAK", "₭", "Lao Kip"),
        CurrencyInfo("LKR", "Rs", "Sri Lankan Rupee"), CurrencyInfo("MAD", "د.م.", "Moroccan Dirham"),
        CurrencyInfo("MMK", "K", "Myanmar Kyat"), CurrencyInfo("MNT", "₮", "Mongolian Tugrik"),
        CurrencyInfo("MOP", "P", "Macanese Pataca"), CurrencyInfo("MXN", "$", "Mexican Peso"),
        CurrencyInfo("MYR", "RM", "Malaysian Ringgit"), CurrencyInfo("NGN", "₦", "Nigerian Naira"),
        CurrencyInfo("NOK", "kr", "Norwegian Krone"), CurrencyInfo("NPR", "Rs", "Nepalese Rupee"),
        CurrencyInfo("NZD", "$", "New Zealand Dollar"), CurrencyInfo("OMR", "ر.ع.", "Omani Rial"),
        CurrencyInfo("PEN", "S/", "Peruvian Sol"), CurrencyInfo("PHP", "₱", "Philippine Peso"),
        CurrencyInfo("PKR", "Rs", "Pakistani Rupee"), CurrencyInfo("PLN", "zł", "Polish Zloty"),
        CurrencyInfo("QAR", "ر.ق", "Qatari Riyal"), CurrencyInfo("RON", "lei", "Romanian Leu"),
        CurrencyInfo("RSD", "дин", "Serbian Dinar"), CurrencyInfo("RUB", "₽", "Russian Ruble"),
        CurrencyInfo("SAR", "﷼", "Saudi Riyal"), CurrencyInfo("SEK", "kr", "Swedish Krona"),
        CurrencyInfo("SGD", "$", "Singapore Dollar"), CurrencyInfo("THB", "฿", "Thai Baht"),
        CurrencyInfo("TRY", "₺", "Turkish Lira"), CurrencyInfo("TWD", "NT$", "New Taiwan Dollar"),
        CurrencyInfo("TZS", "TSh", "Tanzanian Shilling"), CurrencyInfo("UAH", "₴", "Ukrainian Hryvnia"),
        CurrencyInfo("UGX", "USh", "Ugandan Shilling"), CurrencyInfo("USD", "$", "US Dollar"),
        CurrencyInfo("UYU", "$", "Uruguayan Peso"), CurrencyInfo("UZS", "soʻm", "Uzbekistani Som"),
        CurrencyInfo("VND", "₫", "Vietnamese Dong"), CurrencyInfo("XAF", "FCFA", "Central African Franc"),
        CurrencyInfo("XOF", "CFA", "West African Franc"), CurrencyInfo("ZAR", "R", "South African Rand"),
    )

    private val byCode = all.associateBy { it.code }

    fun info(code: String): CurrencyInfo = byCode[code] ?: CurrencyInfo(code, code, code)
    fun symbol(code: String): String = info(code).symbol
}
