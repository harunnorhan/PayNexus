package com.paynexus.payment.contract

import android.os.BadParcelableException
import android.os.Parcel

/** Checks the existing Android String16/primitive layout before reads can supply defaults. */
internal object PaymentParcelReader {
    fun string(source: Parcel): String? {
        val start = source.dataPosition()
        val available = source.dataAvail()
        requireBytes(available, Int.SIZE_BYTES.toLong())
        val length = source.readInt()
        val size =
            PaymentParcelBounds.stringFieldSize(length)
                ?: throw BadParcelableException("Invalid payment string length.")
        requireBytes(available, size)
        source.setDataPosition(start)
        val value = source.readString()
        if ((length >= 0 && value?.length != length) || source.dataPosition().toLong() != start.toLong() + size) {
            throw BadParcelableException("Invalid payment string field.")
        }
        return value
    }

    fun long(source: Parcel): Long {
        requireBytes(source.dataAvail(), Long.SIZE_BYTES.toLong())
        return source.readLong()
    }

    fun int(source: Parcel): Int {
        requireBytes(source.dataAvail(), Int.SIZE_BYTES.toLong())
        return source.readInt()
    }

    private fun requireBytes(
        available: Int,
        required: Long,
    ) {
        if (!PaymentParcelBounds.hasBytes(available, required)) {
            throw BadParcelableException("Incomplete payment transport fields.")
        }
    }
}

/** Pure size arithmetic only; these checks do not emulate Parcel or Binder. */
internal object PaymentParcelBounds {
    fun hasBytes(
        available: Int,
        required: Long,
    ): Boolean = required >= 0 && available.toLong() >= required

    fun stringFieldSize(length: Int): Long? =
        when {
            length < -1 -> {
                null
            }

            length == -1 -> {
                Int.SIZE_BYTES.toLong()
            }

            else -> {
                val terminatedBytes = (length.toLong() + 1) * Char.SIZE_BYTES
                val alignment = Int.SIZE_BYTES.toLong()
                Int.SIZE_BYTES + ((terminatedBytes + alignment - 1) / alignment) * alignment
            }
        }
}
