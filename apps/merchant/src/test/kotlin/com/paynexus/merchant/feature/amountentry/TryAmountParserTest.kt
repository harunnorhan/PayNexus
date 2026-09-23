package com.paynexus.merchant.feature.amountentry

import kotlin.test.Test
import kotlin.test.assertEquals

class TryAmountParserTest {
    @Test
    fun `whole amount becomes minor units`() {
        assertEquals(AmountParseResult.Parsed(1200L), TryAmountParser.parse("12"))
    }

    @Test
    fun `one dot fractional digit is scaled`() {
        assertEquals(AmountParseResult.Parsed(1230L), TryAmountParser.parse("12.3"))
    }

    @Test
    fun `one comma fractional digit is scaled`() {
        assertEquals(AmountParseResult.Parsed(1230L), TryAmountParser.parse("12,3"))
    }

    @Test
    fun `two dot fractional digits are exact`() {
        assertEquals(AmountParseResult.Parsed(1234L), TryAmountParser.parse("12.34"))
    }

    @Test
    fun `two comma fractional digits are exact`() {
        assertEquals(AmountParseResult.Parsed(1234L), TryAmountParser.parse("12,34"))
    }

    @Test
    fun `whole zero is numeric`() {
        assertEquals(AmountParseResult.Parsed(0L), TryAmountParser.parse("0"))
    }

    @Test
    fun `fractional zero is numeric`() {
        assertEquals(AmountParseResult.Parsed(0L), TryAmountParser.parse("0.00"))
    }

    @Test
    fun `smallest positive minor unit is exact`() {
        assertEquals(AmountParseResult.Parsed(1L), TryAmountParser.parse("0.01"))
    }

    @Test
    fun `leading dot implies zero major units`() {
        assertEquals(AmountParseResult.Parsed(50L), TryAmountParser.parse(".5"))
    }

    @Test
    fun `leading comma implies zero major units`() {
        assertEquals(AmountParseResult.Parsed(50L), TryAmountParser.parse(",5"))
    }

    @Test
    fun `leading zeros preserve the amount`() {
        assertEquals(AmountParseResult.Parsed(1230L), TryAmountParser.parse("00012.30"))
    }

    @Test
    fun `multiple zeros are numeric zero`() {
        assertEquals(AmountParseResult.Parsed(0L), TryAmountParser.parse("000"))
    }

    @Test
    fun `empty text is empty`() {
        assertEquals(AmountParseResult.Empty, TryAmountParser.parse(""))
    }

    @Test
    fun `trailing dot is incomplete`() {
        assertEquals(AmountParseResult.Incomplete, TryAmountParser.parse("12."))
    }

    @Test
    fun `trailing comma is incomplete`() {
        assertEquals(AmountParseResult.Incomplete, TryAmountParser.parse("12,"))
    }

    @Test
    fun `lone dot is incomplete`() {
        assertEquals(AmountParseResult.Incomplete, TryAmountParser.parse("."))
    }

    @Test
    fun `lone comma is incomplete`() {
        assertEquals(AmountParseResult.Incomplete, TryAmountParser.parse(","))
    }

    @Test
    fun `maximum minor units are representable`() {
        assertEquals(AmountParseResult.Parsed(Long.MAX_VALUE), TryAmountParser.parse("92233720368547758.07"))
    }

    @Test
    fun `maximum with comma is representable`() {
        assertEquals(AmountParseResult.Parsed(Long.MAX_VALUE), TryAmountParser.parse("92233720368547758,07"))
    }

    @Test
    fun `whole value just below maximum is representable`() {
        assertEquals(AmountParseResult.Parsed(9223372036854775800L), TryAmountParser.parse("92233720368547758"))
    }

    @Test
    fun `negative sign is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("-1"))
    }

    @Test
    fun `positive sign is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("+1"))
    }

    @Test
    fun `excess fraction is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.TooManyFractionDigits), TryAmountParser.parse("12.345"))
    }

    @Test
    fun `comma excess fraction is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.TooManyFractionDigits), TryAmountParser.parse("12,345"))
    }

    @Test
    fun `repeated dots are rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("12..3"))
    }

    @Test
    fun `repeated commas are rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("12,,3"))
    }

    @Test
    fun `mixed separators are rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("12,3.4"))
    }

    @Test
    fun `reverse mixed separators are rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("12.3,4"))
    }

    @Test
    fun `letters are rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("abc"))
    }

    @Test
    fun `numeric prefix does not hide letters`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("12abc"))
    }

    @Test
    fun `whitespace alone is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse(" "))
    }

    @Test
    fun `leading whitespace is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse(" 12"))
    }

    @Test
    fun `trailing whitespace is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("12 "))
    }

    @Test
    fun `internal whitespace is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("1 2"))
    }

    @Test
    fun `non ASCII digits are rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("١٢"))
    }

    @Test
    fun `full width digits are rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("１２"))
    }

    @Test
    fun `grouped decimal input is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("1,234.56"))
    }

    @Test
    fun `grouping is not inferred`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.TooManyFractionDigits), TryAmountParser.parse("1,234"))
    }

    @Test
    fun `exponent notation is rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse("1e2"))
    }

    @Test
    fun `one minor unit above maximum is rejected`() {
        assertEquals(
            AmountParseResult.Invalid(AmountInputError.Overflow),
            TryAmountParser.parse("92233720368547758.08"),
        )
    }

    @Test
    fun `major units that cannot scale are rejected`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.Overflow), TryAmountParser.parse("92233720368547759"))
    }

    @Test
    fun `maximum Long major units cannot scale`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.Overflow), TryAmountParser.parse("9223372036854775807"))
    }

    @Test
    fun `major digit accumulation cannot overflow`() {
        assertEquals(AmountParseResult.Invalid(AmountInputError.Overflow), TryAmountParser.parse("9223372036854775808"))
    }

    @Test
    fun `invalid suffix wins over overflow`() {
        assertEquals(
            AmountParseResult.Invalid(AmountInputError.InvalidFormat),
            TryAmountParser.parse("922337203685477580800abc"),
        )
    }

    @Test
    fun `invalid structure wins over overflow`() {
        assertEquals(
            AmountParseResult.Invalid(AmountInputError.InvalidFormat),
            TryAmountParser.parse("922337203685477580800..1"),
        )
    }

    @Test
    fun `fraction precision wins over overflow`() {
        assertEquals(
            AmountParseResult.Invalid(AmountInputError.TooManyFractionDigits),
            TryAmountParser.parse("922337203685477580800.123"),
        )
    }

    @Test
    fun `long leading zero prefix does not impose a digit limit`() {
        assertEquals(AmountParseResult.Parsed(1230L), TryAmountParser.parse("0".repeat(100) + "12.30"))
    }

    @Test
    fun `tabs and line breaks are not trimmed`() {
        listOf("\t12", "12\n", "\r\n").forEach { input ->
            assertEquals(AmountParseResult.Invalid(AmountInputError.InvalidFormat), TryAmountParser.parse(input), input)
        }
    }
}
