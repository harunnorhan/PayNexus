package com.paynexus.merchant.feature.amountentry

import com.paynexus.merchant.ipc.PaymentSubmissionAdmission
import com.paynexus.merchant.ipc.PaymentSubmissionState
import com.paynexus.merchant.ipc.PaymentTransportFailure
import com.paynexus.payment.domain.CurrencyCode
import com.paynexus.payment.domain.DeclineReason
import com.paynexus.payment.domain.IdempotencyKey
import com.paynexus.payment.domain.Money
import com.paynexus.payment.domain.PaymentAmount
import com.paynexus.payment.domain.PaymentFailure
import com.paynexus.payment.domain.PaymentId
import com.paynexus.payment.domain.PaymentOutcome
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
        assertNull(viewModel.uiState.confirmedAmount())
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
        assertEquals(candidate, viewModel.uiState.confirmedAmount())
        assertEquals(PaymentAmount(Money(1234L, CurrencyCode.TRY)), viewModel.uiState.confirmedAmount())
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
            assertNull(viewModel.uiState.confirmedAmount(), input)
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
        assertNull(viewModel.uiState.confirmedAmount())
        assertEquals(PaymentAmount(Money(1200L, CurrencyCode.TRY)), viewModel.uiState.amount)
        assertTrue(viewModel.uiState.isConfirmEnabled)
    }

    @Test
    fun `editing a confirmed amount to another valid value replaces the candidate`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("12")
        viewModel.onConfirm()
        viewModel.onAmountChanged(".5")
        assertNull(viewModel.uiState.confirmedAmount())
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
            assertNull(viewModel.uiState.confirmedAmount(), input)
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
        assertNull(previous.confirmedAmount())
        assertTrue(previous.isConfirmEnabled)
    }

    @Test
    fun `maximum quantity can be confirmed and formatted deterministically`() {
        val viewModel = AmountEntryViewModel()
        viewModel.onAmountChanged("92233720368547758.07")
        viewModel.onConfirm()
        val expected = PaymentAmount(Money(Long.MAX_VALUE, CurrencyCode.TRY))
        assertEquals(expected, viewModel.uiState.confirmedAmount())
        assertEquals("92233720368547758.07", TryAmountFormatter.format(expected))
    }
}

class MerchantPaymentFlowViewModelTest {
    private val first = PaymentIdentifiers(PaymentId("payment-001"), IdempotencyKey("idempotency-001"))
    private val second = PaymentIdentifiers(PaymentId("payment-002"), IdempotencyKey("idempotency-002"))

    @Test
    fun `amount confirmation remains local and does not submit`() {
        val viewModel = confirmedViewModel(first)
        var submissions = 0

        assertEquals(firstAmount, viewModel.uiState.confirmedAmount())
        assertEquals(0, submissions)

        viewModel.onStartPayment { _, _, _ ->
            submissions += 1
            PaymentSubmissionAdmission.Accepted
        }
        assertEquals(1, submissions)
    }

    @Test
    fun `accepted submission preserves exact identifiers and amount then enters processing`() {
        val viewModel = confirmedViewModel(first)
        var submitted: Triple<PaymentId, IdempotencyKey, PaymentAmount>? = null

        viewModel.onStartPayment { id, key, amount ->
            submitted = Triple(id, key, amount)
            PaymentSubmissionAdmission.Accepted
        }

        assertEquals(Triple(first.paymentId, first.idempotencyKey, firstAmount), submitted)
        assertEquals(MerchantPaymentUiState.Processing(firstAmount), viewModel.uiState.payment)
    }

    @Test
    fun `synchronous terminal delivery during accepted submission is retained`() {
        val viewModel = confirmedViewModel(first)

        viewModel.onStartPayment { id, key, _ ->
            viewModel.onSubmissionStateChanged(
                PaymentSubmissionState.TransportFailed(id, key, PaymentTransportFailure.WorkerUnavailable),
            )
            PaymentSubmissionAdmission.Accepted
        }

        assertEquals(
            MerchantPaymentUiState.TransportFailure(firstAmount, MerchantTransportFailure.ServiceUnavailable),
            viewModel.uiState.payment,
        )
    }

    @Test
    fun `local submission rejections remain confirmation failures outside processing`() {
        val cases = mapOf(
            PaymentSubmissionAdmission.NotReady to PaymentStartFailure.ServiceNotReady,
            PaymentSubmissionAdmission.AlreadyActive to PaymentStartFailure.AlreadyActive,
            PaymentSubmissionAdmission.InvalidRequest to PaymentStartFailure.InvalidRequest,
        )
        for ((admission, failure) in cases) {
            val viewModel = confirmedViewModel(first)
            viewModel.onStartPayment { _, _, _ -> admission }
            assertEquals(MerchantPaymentUiState.Confirmation(firstAmount, failure), viewModel.uiState.payment)
        }
    }

    @Test
    fun `repeated action during processing does not submit or generate another attempt`() {
        val factory = RecordingIdentifiersFactory(first, second)
        val viewModel = confirmedViewModel(factory)
        var submissions = 0
        val submit = { _: PaymentId, _: IdempotencyKey, _: PaymentAmount ->
            submissions += 1
            PaymentSubmissionAdmission.Accepted
        }

        viewModel.onStartPayment(submit)
        viewModel.onStartPayment(submit)

        assertEquals(1, submissions)
        assertEquals(1, factory.created)
        assertEquals(MerchantPaymentUiState.Processing(firstAmount), viewModel.uiState.payment)
    }

    @Test
    fun `approved declined and failed outcomes remain distinct terminal results`() {
        val outcomes = listOf(
            PaymentOutcome.Approved,
            PaymentOutcome.Declined(DeclineReason.UNSPECIFIED),
            PaymentOutcome.Failed(PaymentFailure.PROCESSING_ERROR),
        )
        for (outcome in outcomes) {
            val viewModel = processingViewModel(first)
            viewModel.onSubmissionStateChanged(
                PaymentSubmissionState.Completed(first.paymentId, first.idempotencyKey, outcome),
            )
            assertEquals(MerchantPaymentUiState.Result(firstAmount, outcome), viewModel.uiState.payment)
        }
    }

    @Test
    fun `transport and protocol failures never become payment outcomes`() {
        val cases = mapOf(
            PaymentTransportFailure.ConnectionLost to MerchantTransportFailure.ConnectionLost,
            PaymentTransportFailure.DispatchFailed to MerchantTransportFailure.DispatchFailed,
            PaymentTransportFailure.InvalidResult to MerchantTransportFailure.ProtocolFailure,
            PaymentTransportFailure.RequestRejected to MerchantTransportFailure.RequestRejected,
            PaymentTransportFailure.OutcomeUnavailable to MerchantTransportFailure.OutcomeUnavailable,
            PaymentTransportFailure.PermissionDenied to MerchantTransportFailure.ServiceUnavailable,
            PaymentTransportFailure.WorkerUnavailable to MerchantTransportFailure.ServiceUnavailable,
        )
        for ((transport, expected) in cases) {
            val viewModel = processingViewModel(first)
            viewModel.onSubmissionStateChanged(
                PaymentSubmissionState.TransportFailed(first.paymentId, first.idempotencyKey, transport),
            )
            assertEquals(MerchantPaymentUiState.TransportFailure(firstAmount, expected), viewModel.uiState.payment)
        }
    }

    @Test
    fun `stale mismatched and duplicate terminal observations cannot replace current flow`() {
        val viewModel = processingViewModel(first)
        viewModel.onSubmissionStateChanged(
            PaymentSubmissionState.Completed(second.paymentId, second.idempotencyKey, PaymentOutcome.Approved),
        )
        assertEquals(MerchantPaymentUiState.Processing(firstAmount), viewModel.uiState.payment)

        val declined = PaymentOutcome.Declined(DeclineReason.UNSPECIFIED)
        viewModel.onSubmissionStateChanged(
            PaymentSubmissionState.Completed(first.paymentId, first.idempotencyKey, declined),
        )
        val terminal = viewModel.uiState
        viewModel.onSubmissionStateChanged(
            PaymentSubmissionState.Completed(first.paymentId, first.idempotencyKey, PaymentOutcome.Approved),
        )
        assertEquals(terminal, viewModel.uiState)
    }

    @Test
    fun `lifecycle abandonment remains transport uncertainty`() {
        val viewModel = processingViewModel(first)
        viewModel.onSubmissionStateChanged(
            PaymentSubmissionState.Abandoned(first.paymentId, first.idempotencyKey),
        )
        assertEquals(
            MerchantPaymentUiState.TransportFailure(firstAmount, MerchantTransportFailure.Abandoned),
            viewModel.uiState.payment,
        )
    }

    @Test
    fun `new payment clears flow ownership without submitting and next attempt uses fresh identifiers`() {
        val factory = RecordingIdentifiersFactory(first, second)
        val viewModel = confirmedViewModel(factory)
        val submissions = mutableListOf<PaymentIdentifiers>()
        val submit = { id: PaymentId, key: IdempotencyKey, _: PaymentAmount ->
            submissions += PaymentIdentifiers(id, key)
            PaymentSubmissionAdmission.Accepted
        }
        viewModel.onStartPayment(submit)
        viewModel.onSubmissionStateChanged(
            PaymentSubmissionState.Completed(first.paymentId, first.idempotencyKey, PaymentOutcome.Approved),
        )

        viewModel.onNewPayment()
        assertEquals(AmountEntryUiState(), viewModel.uiState)
        assertEquals(listOf(first), submissions)

        confirm(viewModel)
        viewModel.onStartPayment(submit)
        assertEquals(listOf(first, second), submissions)
    }

    @Test
    fun `terminal observation after reset cannot mutate a later attempt`() {
        val viewModel = confirmedViewModel(RecordingIdentifiersFactory(first, second))
        viewModel.onStartPayment { _, _, _ -> PaymentSubmissionAdmission.Accepted }
        viewModel.onSubmissionStateChanged(
            PaymentSubmissionState.Completed(first.paymentId, first.idempotencyKey, PaymentOutcome.Approved),
        )
        viewModel.onNewPayment()
        confirm(viewModel)
        viewModel.onStartPayment { _, _, _ -> PaymentSubmissionAdmission.Accepted }

        viewModel.onSubmissionStateChanged(
            PaymentSubmissionState.Completed(first.paymentId, first.idempotencyKey, PaymentOutcome.Approved),
        )
        assertEquals(MerchantPaymentUiState.Processing(firstAmount), viewModel.uiState.payment)
    }

    private fun confirmedViewModel(
        identifiers: PaymentIdentifiers,
    ): AmountEntryViewModel = confirmedViewModel(RecordingIdentifiersFactory(identifiers))

    private fun confirmedViewModel(
        factory: PaymentIdentifiersFactory,
    ): AmountEntryViewModel = AmountEntryViewModel(factory).also(::confirm)

    private fun processingViewModel(
        identifiers: PaymentIdentifiers,
    ): AmountEntryViewModel = confirmedViewModel(identifiers).also {
        it.onStartPayment { _, _, _ -> PaymentSubmissionAdmission.Accepted }
    }

    private fun confirm(viewModel: AmountEntryViewModel) {
        viewModel.onAmountChanged("3.00")
        viewModel.onConfirm()
    }

    private val firstAmount = PaymentAmount(Money(300L, CurrencyCode.TRY))
}

private class RecordingIdentifiersFactory(vararg identifiers: PaymentIdentifiers) : PaymentIdentifiersFactory {
    private val values = ArrayDeque(identifiers.toList())
    var created: Int = 0
        private set

    override fun create(): PaymentIdentifiers {
        created += 1
        return values.removeFirst()
    }
}

private fun AmountEntryUiState.confirmedAmount(): PaymentAmount? {
    val confirmation = payment as? MerchantPaymentUiState.Confirmation
    return confirmation?.amount
}
