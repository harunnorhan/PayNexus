package com.paynexus.payment.contract

import android.os.Parcel
import android.os.Parcelable

/** V2/V3 wire fields. Nullable strings remain untrusted until boundary validation. */
data class PaymentRequestParcel(
    val paymentId: String?,
    val idempotencyKey: String?,
    val minorUnits: Long,
    val currencyCode: String?,
) : Parcelable {
    override fun writeToParcel(
        destination: Parcel,
        flags: Int,
    ) {
        destination.writeString(paymentId)
        destination.writeString(idempotencyKey)
        destination.writeLong(minorUnits)
        destination.writeString(currencyCode)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PaymentRequestParcel> =
            object : Parcelable.Creator<PaymentRequestParcel> {
                override fun createFromParcel(source: Parcel): PaymentRequestParcel =
                    PaymentRequestParcel(
                        paymentId = PaymentParcelReader.string(source),
                        idempotencyKey = PaymentParcelReader.string(source),
                        minorUnits = PaymentParcelReader.long(source),
                        currencyCode = PaymentParcelReader.string(source),
                    )

                override fun newArray(size: Int): Array<PaymentRequestParcel?> = arrayOfNulls(size)
            }
    }
}
