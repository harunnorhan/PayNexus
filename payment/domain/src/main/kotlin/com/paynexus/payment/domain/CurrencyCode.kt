package com.paynexus.payment.domain

enum class CurrencyCode {
    TRY,
    ;

    companion object {
        fun fromCode(code: String): CurrencyCode =
            requireNotNull(entries.find { it.name == code }) {
                "Unsupported currency code."
            }
    }
}
