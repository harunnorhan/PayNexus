package com.paynexus.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PaymentIdTest {
    @Test
    fun `caller supplied values are preserved exactly`() {
        listOf("synthetic-123", " Mixed-Case-123 ").forEach { value ->
            assertEquals(value, PaymentId(value).value)
        }
    }

    @Test
    fun `blank identifiers are rejected`() {
        listOf("", " ", "\t\n").forEach { value ->
            assertFailsWith<IllegalArgumentException> { PaymentId(value) }
        }
    }

    @Test
    fun `identifiers have exact value equality`() {
        val original = PaymentId("synthetic-123")
        val equal = PaymentId("synthetic-123")
        assertEquals(original, equal)
        assertEquals(original.hashCode(), equal.hashCode())
        assertNotEquals(original, PaymentId("synthetic-456"))
        assertNotEquals(original, PaymentId("SYNTHETIC-123"))
        assertNotEquals(original, PaymentId(" synthetic-123 "))
    }

    @Test
    fun `copy cannot bypass identifier validation`() {
        val original = PaymentId("synthetic-123")
        assertFailsWith<IllegalArgumentException> { original.copy(value = " ") }
        assertEquals("synthetic-123", original.value)
    }
}
