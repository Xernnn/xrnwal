package com.xern.xrnwallet.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The amount field on the add-bill keypad. Holds raw keypad text such as "12+3.5" and evaluates it
 * with normal operator precedence. Text always uses '.' as the decimal mark; display swaps it.
 */
object Calculator {
    private const val OPERATORS = "+-×÷"

    fun isOperator(c: Char) = c in OPERATORS

    fun hasPendingSum(text: String): Boolean = text.drop(1).any(::isOperator)

    /** Applies one keypad press and returns the new text. */
    fun press(text: String, key: Char): String {
        val current = if (text == "0") "" else text
        return when {
            key in '0'..'9' -> appendDigit(current, key)
            key == '.' -> appendPoint(current)
            isOperator(key) -> appendOperator(current, key)
            else -> text
        }.ifEmpty { "0" }
    }

    fun backspace(text: String): String = text.dropLast(1).ifEmpty { "0" }

    private fun lastNumber(text: String): String = text.takeLastWhile { !isOperator(it) }

    private fun appendDigit(text: String, digit: Char): String {
        val number = lastNumber(text)
        val decimals = number.substringAfter('.', "")
        if ('.' in number && decimals.length >= 2) return text
        if (number.substringBefore('.').length >= 12) return text
        return text + digit
    }

    private fun appendPoint(text: String): String {
        val number = lastNumber(text)
        return when {
            '.' in number -> text
            number.isEmpty() -> "${text}0."
            else -> "$text."
        }
    }

    private fun appendOperator(text: String, op: Char): String = when {
        text.isEmpty() -> "0$op"
        isOperator(text.last()) -> text.dropLast(1) + op
        text.last() == '.' -> text.dropLast(1) + op
        else -> text + op
    }

    /** Result to two places, or null when the text cannot be evaluated (such as division by zero). */
    fun evaluate(text: String): BigDecimal? {
        val trimmed = text.trimEnd { isOperator(it) || it == '.' }
        if (trimmed.isEmpty()) return BigDecimal.ZERO
        val numbers = mutableListOf<BigDecimal>()
        val ops = mutableListOf<Char>()
        val token = StringBuilder()
        for (c in trimmed) {
            if (isOperator(c) && token.isNotEmpty()) {
                numbers += token.toString().toBigDecimalOrNull() ?: return null
                ops += c
                token.clear()
            } else {
                token.append(c)
            }
        }
        numbers += token.toString().toBigDecimalOrNull() ?: return null

        val terms = mutableListOf(numbers[0])
        val additive = mutableListOf<Char>()
        ops.forEachIndexed { index, op ->
            val next = numbers[index + 1]
            when (op) {
                '×' -> terms[terms.lastIndex] = terms.last().multiply(next)
                '÷' -> {
                    if (next.signum() == 0) return null
                    terms[terms.lastIndex] = terms.last().divide(next, MathContext.DECIMAL64)
                }
                else -> {
                    additive += op
                    terms += next
                }
            }
        }
        var total = terms[0]
        additive.forEachIndexed { index, op ->
            total = if (op == '+') total + terms[index + 1] else total - terms[index + 1]
        }
        return total.setScale(2, RoundingMode.HALF_UP)
    }

    /** Text form of a value, for loading an existing amount or replacing a sum with its result. */
    fun format(value: BigDecimal): String =
        value.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
}
