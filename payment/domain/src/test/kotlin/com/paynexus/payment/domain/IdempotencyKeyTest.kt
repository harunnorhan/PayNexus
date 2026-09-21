package com.paynexus.payment.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class IdempotencyKeyTest {
    @Test
    fun `caller supplied values are preserved exactly`() {
        listOf("synthetic-123", " Mixed-Case-123 ").forEach { value ->
            assertEquals(value, IdempotencyKey(value).value)
        }
    }

    @Test
    fun `blank identifiers are rejected`() {
        listOf("", " ", "\t\n").forEach { value ->
            assertFailsWith<IllegalArgumentException> { IdempotencyKey(value) }
        }
    }

    @Test
    fun `identifiers have exact value equality`() {
        val original = IdempotencyKey("synthetic-123")
        val equal = IdempotencyKey("synthetic-123")
        assertEquals(original, equal)
        assertEquals(original.hashCode(), equal.hashCode())
        assertNotEquals(original, IdempotencyKey("synthetic-456"))
        assertNotEquals(original, IdempotencyKey("SYNTHETIC-123"))
        assertNotEquals(original, IdempotencyKey(" synthetic-123 "))
    }

    @Test
    fun `copy cannot bypass identifier validation`() {
        val original = IdempotencyKey("synthetic-123")
        assertFailsWith<IllegalArgumentException> { original.copy(value = " ") }
        assertEquals("synthetic-123", original.value)
    }
}
