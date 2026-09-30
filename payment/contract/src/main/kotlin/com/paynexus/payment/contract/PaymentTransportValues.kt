package com.paynexus.payment.contract

/** Explicit V3 wire values, never enum ordinals. Identifier limits count UTF-16 code units. */
object PaymentTransportValues {
    const val MAX_IDENTIFIER_LENGTH: Int = 256
    const val APPROVED: Int = 1
    const val DECLINED: Int = 2
    const val FAILED: Int = 3
    const val NONE: Int = 0
    const val UNSPECIFIED_DECLINE: Int = 1
    const val PROCESSING_ERROR: Int = 2
    const val INVALID_REQUEST: Int = 1

    /** Does not establish remote cancellation, remote non-processing, or a business payment outcome. */
    const val PAYMENT_OUTCOME_UNAVAILABLE: Int = 1

    fun isValidIdentifier(value: String?): Boolean {
        if (value == null) return false
        return value.length <= MAX_IDENTIFIER_LENGTH && value.isNotBlank()
    }
}
