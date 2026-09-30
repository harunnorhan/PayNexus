package com.paynexus.payment.contract

object PaymentIpcContract {
    const val CURRENT_VERSION: Int = 3
    const val MIN_SUPPORTED_VERSION: Int = 3

    fun supports(remoteVersion: Int): Boolean = remoteVersion in MIN_SUPPORTED_VERSION..CURRENT_VERSION
}
