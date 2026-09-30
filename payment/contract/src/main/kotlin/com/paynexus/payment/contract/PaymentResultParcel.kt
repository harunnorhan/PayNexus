package com.paynexus.payment.contract

import android.os.Parcel
import android.os.Parcelable

/** V2/V3 wire fields. Nullable strings remain untrusted until boundary validation. */
data class PaymentResultParcel(
    val paymentId: String?,
    val idempotencyKey: String?,
    val outcomeCode: Int,
    val reasonCode: Int,
) : Parcelable {
    override fun writeToParcel(
        destination: Parcel,
        flags: Int,
    ) {
        destination.writeString(paymentId)
        destination.writeString(idempotencyKey)
        destination.writeInt(outcomeCode)
        destination.writeInt(reasonCode)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PaymentResultParcel> =
            object : Parcelable.Creator<PaymentResultParcel> {
                override fun createFromParcel(source: Parcel): PaymentResultParcel =
                    PaymentResultParcel(
                        paymentId = PaymentParcelReader.string(source),
                        idempotencyKey = PaymentParcelReader.string(source),
                        outcomeCode = PaymentParcelReader.int(source),
                        reasonCode = PaymentParcelReader.int(source),
                    )

                override fun newArray(size: Int): Array<PaymentResultParcel?> = arrayOfNulls(size)
            }
    }
}
