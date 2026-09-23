package com.paynexus.merchant.feature.amountentry

internal sealed interface AmountParseResult {
    data object Empty : AmountParseResult

    data object Incomplete : AmountParseResult

    data class Parsed(val minorUnits: Long) : AmountParseResult

    data class Invalid(val reason: AmountInputError) : AmountParseResult
}

internal enum class AmountInputError {
    InvalidFormat,
    TooManyFractionDigits,
    Overflow,
}

internal object TryAmountParser {
    private const val DECIMAL_BASE = 10
    private const val MINOR_UNITS_PER_MAJOR = 100
    private const val FRACTION_DIGITS = 2
    private val decimalStructure = Regex("[0-9]*([.,][0-9]*)?")

    fun parse(input: String): AmountParseResult = when {
        input.isEmpty() -> AmountParseResult.Empty
        !decimalStructure.matches(input) -> AmountParseResult.Invalid(AmountInputError.InvalidFormat)
        input.last() == '.' || input.last() == ',' -> AmountParseResult.Incomplete
        else -> parseComplete(input)
    }

    private fun parseComplete(input: String): AmountParseResult {
        val separator = input.indexOfFirst { it == '.' || it == ',' }
        val majorText = if (separator < 0) input else input.substring(0, separator)
        val fractionText = if (separator < 0) "" else input.substring(separator + 1)
        return if (fractionText.length > FRACTION_DIGITS) {
            AmountParseResult.Invalid(AmountInputError.TooManyFractionDigits)
        } else {
            val fraction = fractionText.padEnd(FRACTION_DIGITS, '0').toInt()
            convertToMinorUnits(majorText, fraction)
        }
    }

    private fun convertToMinorUnits(majorText: String, fraction: Int): AmountParseResult {
        var major = 0L
        for (character in majorText) {
            val digit = character - '0'
            if (major > (Long.MAX_VALUE - digit) / DECIMAL_BASE) {
                return AmountParseResult.Invalid(AmountInputError.Overflow)
            }
            major = major * DECIMAL_BASE + digit
        }
        return if (major > (Long.MAX_VALUE - fraction) / MINOR_UNITS_PER_MAJOR) {
            AmountParseResult.Invalid(AmountInputError.Overflow)
        } else {
            AmountParseResult.Parsed(major * MINOR_UNITS_PER_MAJOR + fraction)
        }
    }
}
