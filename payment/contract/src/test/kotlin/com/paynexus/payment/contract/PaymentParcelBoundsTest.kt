package com.paynexus.payment.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests production size decisions only, never Android Parcel operations. */
class PaymentParcelBoundsTest {
    @Test
    fun `missing primitive bytes cannot satisfy an integer or Long read`() {
        for (required in listOf(4L, 8L)) {
            for (available in 0 until required.toInt()) {
                assertFalse(PaymentParcelBounds.hasBytes(available, required))
            }
            assertTrue(PaymentParcelBounds.hasBytes(required.toInt(), required))
        }
    }

    @Test
    fun `negative sizes and unavailable data are rejected`() {
        assertFalse(PaymentParcelBounds.hasBytes(-1, 4))
        assertFalse(PaymentParcelBounds.hasBytes(8, -1))
    }

    @Test
    fun `String16 sizes include prefix terminator and four byte padding`() {
        val expected = mapOf(0 to 8L, 1 to 8L, 2 to 12L, 3 to 12L, 256 to 520L)
        for ((length, size) in expected) {
            assertEquals(size, PaymentParcelBounds.stringFieldSize(length))
        }
        assertEquals(12L, PaymentParcelBounds.stringFieldSize("😀".length))
    }

    @Test
    fun `explicit null still requires a complete prefix and other negative lengths are invalid`() {
        assertEquals(4L, PaymentParcelBounds.stringFieldSize(-1))
        assertFalse(PaymentParcelBounds.hasBytes(0, 4))
        assertNull(PaymentParcelBounds.stringFieldSize(-2))
        assertNull(PaymentParcelBounds.stringFieldSize(Int.MIN_VALUE))
    }

    @Test
    fun `truncated string content terminator or padding is rejected`() {
        val required = requireNotNull(PaymentParcelBounds.stringFieldSize(2))
        for (available in 0 until required.toInt()) {
            assertFalse(PaymentParcelBounds.hasBytes(available, required))
        }
        assertTrue(PaymentParcelBounds.hasBytes(12, required))
    }

    @Test
    fun `malicious large length cannot overflow into an acceptable size`() {
        val required = requireNotNull(PaymentParcelBounds.stringFieldSize(Int.MAX_VALUE))
        assertEquals(4_294_967_300L, required)
        assertFalse(PaymentParcelBounds.hasBytes(Int.MAX_VALUE, required))
    }
}
