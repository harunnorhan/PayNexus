package com.paynexus.merchant.feature.amountentry

import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountEntryViewModelTest {
    @Test
    fun `initial state is empty and confirmation is disabled`() {
        val state = AmountEntryViewModel().uiState
        assertEquals(AmountEntryUiState(), state)
        assertFalse(state.isConfirmEnabled)
    }

    @Test
    fun `valid input preserves text and creates an exact TRY candidate`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("00012,34")
        assertEquals("00012,34", viewModel.uiState.input)
        assertEquals(PaymentAmount(Money(1234L, CurrencyCode.TRY)), viewModel.uiState.amount)
        assertEquals(AmountEntryValidation.Valid, viewModel.uiState.validation)
        assertTrue(viewModel.uiState.isConfirmEnabled)
        assertNull(viewModel.uiState.confirmedAmount)
    }

    @Test
    fun `zero never creates a candidate`() {
        listOf("0", "0.00", "000", ",00").forEach { input ->
            val viewModel = AmountEntryViewModel()
            viewModel.onAmountChanged(input)
            assertEquals(AmountEntryValidation.MustBePositive, viewModel.uiState.validation, input)
            assertNull(viewModel.uiState.amount, input)
            assertFalse(viewModel.uiState.isConfirmEnabled, input)
        }
    }

    @Test
    fun `invalid editing clears a previously valid candidate and preserves raw text`() {
        val cases = mapOf(
            " 12abc " to AmountEntryValidation.InvalidFormat,
            "12.345" to AmountEntryValidation.TooManyFractionDigits,
            "92233720368547758.08" to AmountEntryValidation.Overflow,
            "0" to AmountEntryValidation.MustBePositive,
        )
        cases.forEach { (input, validation) ->
            val viewModel = AmountEntryViewModel()
            viewModel.onAmountChanged("12")
            viewModel.onAmountChanged(input)
            assertEquals(input, viewModel.uiState.input)
            assertEquals(validation, viewModel.uiState.validation, input)
            assertNull(viewModel.uiState.amount, input)
            assertFalse(viewModel.uiState.isConfirmEnabled, input)
        }
    }

    @Test
    fun `incomplete editing clears a previously valid candidate`() {
        listOf("12.", "12,", ".", ",").forEach { input ->
            val viewModel = AmountEntryViewModel()
            viewModel.onAmountChanged("12")
            viewModel.onAmountChanged(input)
            assertEquals(AmountEntryValidation.Incomplete, viewModel.uiState.validation, input)
            assertNull(viewModel.uiState.amount, input)
            assertFalse(viewModel.uiState.isConfirmEnabled, input)
        }
    }

    @Test
    fun `clearing input returns to empty state`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("12")
        viewModel.onConfirm()
        viewModel.onAmountChanged("")
        assertEquals(AmountEntryUiState(), viewModel.uiState)
    }

    @Test
    fun `valid confirmation retains the canonical amount and disables confirmation`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("12,34")
        val candidate = viewModel.uiState.amount
        viewModel.onConfirm()
        assertEquals(candidate, viewModel.uiState.confirmedAmount)
        assertEquals(PaymentAmount(Money(1234L, CurrencyCode.TRY)), viewModel.uiState.confirmedAmount)
        assertEquals("12,34", viewModel.uiState.input)
        assertFalse(viewModel.uiState.isConfirmEnabled)
    }

    @Test
    fun `invalid confirmation never changes state`() {
        listOf("", "0", "12.", "-1", "abc", "12.345", "92233720368547758.08").forEach { input ->
            val viewModel = AmountEntryViewModel()
            viewModel.onAmountChanged(input)
            val before = viewModel.uiState
            viewModel.onConfirm()
            assertEquals(before, viewModel.uiState, input)
            assertNull(viewModel.uiState.confirmedAmount, input)
        }
    }

    @Test
    fun `repeated confirmation is harmless`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("12")
        viewModel.onConfirm()
        val before = viewModel.uiState
        viewModel.onConfirm()
        assertEquals(before, viewModel.uiState)
    }

    @Test
    fun `identical editing event clears confirmation and enables confirmation again`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("12")
        viewModel.onConfirm()
        viewModel.onAmountChanged("12")
        assertNull(viewModel.uiState.confirmedAmount)
        assertEquals(PaymentAmount(Money(1200L, CurrencyCode.TRY)), viewModel.uiState.amount)
        assertTrue(viewModel.uiState.isConfirmEnabled)
    }

    @Test
    fun `editing a confirmed amount to another valid value replaces the candidate`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("12")
        viewModel.onConfirm()
        viewModel.onAmountChanged(".5")
        assertNull(viewModel.uiState.confirmedAmount)
        assertEquals(PaymentAmount(Money(50L, CurrencyCode.TRY)), viewModel.uiState.amount)
        assertTrue(viewModel.uiState.isConfirmEnabled)
    }

    @Test
    fun `every nonconfirmable edit clears both candidate and confirmation`() {
        listOf("", "0", "12.", "abc", "12.345", "92233720368547758.08").forEach { input ->
            val viewModel = AmountEntryViewModel()
            viewModel.onAmountChanged("12")
            viewModel.onConfirm()
            viewModel.onAmountChanged(input)
            assertNull(viewModel.uiState.confirmedAmount, input)
            assertNull(viewModel.uiState.amount, input)
            assertFalse(viewModel.uiState.isConfirmEnabled, input)
        }
    }

    @Test
    fun `previously observed state does not change after later events`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("12")
        val previous = viewModel.uiState
        viewModel.onConfirm()
        viewModel.onAmountChanged("abc")
        assertEquals("12", previous.input)
        assertEquals(PaymentAmount(Money(1200L, CurrencyCode.TRY)), previous.amount)
        assertNull(previous.confirmedAmount)
        assertTrue(previous.isConfirmEnabled)
    }

    @Test
    fun `maximum quantity can be confirmed and formatted deterministically`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("92233720368547758.07")
        viewModel.onConfirm()
        val expected = PaymentAmount(Money(Long.MAX_VALUE, CurrencyCode.TRY))
        assertEquals(expected, viewModel.uiState.confirmedAmount)
        assertEquals("92233720368547758.07", TryAmountFormatter.format(expected))
    }
}
