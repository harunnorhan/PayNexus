package com.paynexus.server.infrastructure.persistence

import com.paynexus.server.domain.payment.AcceptedPaymentRequest
import com.paynexus.server.domain.payment.IdempotentPaymentProcessor
import com.paynexus.server.domain.payment.PaymentCurrency
import com.paynexus.server.domain.payment.PaymentIntent
import com.paynexus.server.domain.payment.PaymentOutcome
import com.paynexus.server.domain.payment.PaymentProcessingResult
import com.paynexus.server.domain.payment.PaymentRepositoryException
import com.paynexus.server.domain.payment.StoreOrReadResult
import com.paynexus.server.domain.payment.StoredPaymentRecord
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Comparator
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqlitePaymentRepositoryTest {
    @Test
    fun `schema bootstrap creates parents and exact values round trip`() =
        withTemporaryDatabase(nested = true) { databasePath ->
            val repository = SqlitePaymentRepository(databasePath)
            val records =
                listOf(
                    record(
                        paymentId = " Mixed-Case Payment-ID ",
                        idempotencyKey = " Mixed-Case Idempotency-Key ",
                        amountMinorUnits = 300L,
                        outcome = PaymentOutcome.APPROVED,
                    ),
                    record("declined-payment", "declined-key", 301L, PaymentOutcome.DECLINED),
                    record("failed-payment", "failed-key", 302L, PaymentOutcome.FAILED),
                    record("maximum-payment", "maximum-key", Long.MAX_VALUE, PaymentOutcome.DECLINED),
                )

            records.forEach { expected ->
                assertEquals(StoreOrReadResult.Created(expected), repository.storeOrRead(expected))
                assertEquals(StoreOrReadResult.Existing(expected), repository.storeOrRead(expected))
            }

            assertTrue(Files.isRegularFile(databasePath))
            assertEquals(records.size, rowCount(databasePath))
        }

    @Test
    fun `file backed records survive repository reconstruction and conflict does not mutate`() =
        withTemporaryDatabase { databasePath ->
            val request = request(paymentId = "original-payment", amountMinorUnits = 302L)
            val firstProcessor = IdempotentPaymentProcessor(SqlitePaymentRepository(databasePath))
            val created = assertIs<PaymentProcessingResult.Created>(firstProcessor.process(request))

            val secondProcessor = IdempotentPaymentProcessor(SqlitePaymentRepository(databasePath))
            val replayed = assertIs<PaymentProcessingResult.Replayed>(secondProcessor.process(request))
            val conflict = secondProcessor.process(request(paymentId = "different-payment", amountMinorUnits = 302L))

            assertEquals(created.record, replayed.record)
            assertIs<PaymentProcessingResult.Conflict>(conflict)
            assertEquals(
                StoreOrReadResult.Existing(created.record),
                SqlitePaymentRepository(databasePath).storeOrRead(created.record),
            )
            assertEquals(1, rowCount(databasePath))
        }

    @Test
    fun `concurrent same key and intent converge on one durable record`() =
        withTemporaryDatabase { databasePath ->
            val first = IdempotentPaymentProcessor(SqlitePaymentRepository(databasePath))
            val second = IdempotentPaymentProcessor(SqlitePaymentRepository(databasePath))
            val accepted = request(amountMinorUnits = Long.MAX_VALUE)

            val results = race({ first.process(accepted) }, { second.process(accepted) })

            assertEquals(1, results.count { it is PaymentProcessingResult.Created })
            assertEquals(1, results.count { it is PaymentProcessingResult.Replayed })
            val records =
                results.map {
                    when (it) {
                        is PaymentProcessingResult.Created -> it.record
                        is PaymentProcessingResult.Replayed -> it.record
                        PaymentProcessingResult.Conflict -> error("Same intent must not conflict.")
                    }
                }
            assertEquals(records.first(), records.last())
            assertEquals(1, rowCount(databasePath))
        }

    @Test
    fun `concurrent different intents produce one winner and one conflict`() =
        withTemporaryDatabase { databasePath ->
            val first = IdempotentPaymentProcessor(SqlitePaymentRepository(databasePath))
            val second = IdempotentPaymentProcessor(SqlitePaymentRepository(databasePath))

            val results =
                race(
                    { first.process(request(paymentId = "first-payment", amountMinorUnits = 300L)) },
                    { second.process(request(paymentId = "second-payment", amountMinorUnits = 301L)) },
                )

            assertEquals(1, results.count { it is PaymentProcessingResult.Created })
            assertEquals(1, results.count { it is PaymentProcessingResult.Conflict })
            assertEquals(1, rowCount(databasePath))
        }

    @Test
    fun `database primary key rejects a duplicate row outside repository arbitration`() =
        withTemporaryDatabase { databasePath ->
            val repository = SqlitePaymentRepository(databasePath)
            repository.storeOrRead(record())

            assertFailsWith<SQLException> {
                DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
                    insertRaw(connection, record(paymentId = "other-payment"))
                }
            }
            assertEquals(1, rowCount(databasePath))
        }

    @Test
    fun `corrupt stored identifiers fail through the sanitized repository exception`() =
        withTemporaryDatabase { databasePath ->
            val repository = SqlitePaymentRepository(databasePath)
            DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
                insertRawValues(
                    connection = connection,
                    idempotencyKey = "idempotency-key",
                    paymentId = "",
                    amountMinorUnits = 300L,
                    outcome = PaymentOutcome.APPROVED,
                )
            }

            val failure = assertFailsWith<PaymentRepositoryException> { repository.storeOrRead(record()) }

            assertEquals("Payment repository operation failed.", failure.message)
        }

    @Test
    fun `exhausted SQLite lock wait becomes a sanitized repository failure`() =
        withTemporaryDatabase { databasePath ->
            val repository = SqlitePaymentRepository(databasePath, busyTimeoutMillis = 0)
            DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { lockingConnection ->
                lockingConnection.autoCommit = false
                insertRaw(lockingConnection, record(idempotencyKey = "locking-key"))

                val failure =
                    assertFailsWith<PaymentRepositoryException> {
                        repository.storeOrRead(record(idempotencyKey = "contending-key"))
                    }

                assertEquals("Payment repository operation failed.", failure.message)
                assertTrue(failure.message?.contains(databasePath.toString()) == false)
                lockingConnection.rollback()
            }
        }

    private fun request(
        paymentId: String = "payment-id",
        idempotencyKey: String = "idempotency-key",
        amountMinorUnits: Long = 300L,
    ): AcceptedPaymentRequest =
        AcceptedPaymentRequest(
            idempotencyKey = idempotencyKey,
            intent = PaymentIntent(paymentId, amountMinorUnits, PaymentCurrency.TRY),
        )

    private fun record(
        paymentId: String = "payment-id",
        idempotencyKey: String = "idempotency-key",
        amountMinorUnits: Long = 300L,
        outcome: PaymentOutcome = PaymentOutcome.APPROVED,
    ): StoredPaymentRecord = StoredPaymentRecord(request(paymentId, idempotencyKey, amountMinorUnits), outcome)

    private fun <T> race(
        first: () -> T,
        second: () -> T,
    ): List<T> {
        val barrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        return try {
            listOf(first, second)
                .map { operation ->
                    executor.submit(
                        Callable {
                            barrier.await()
                            operation()
                        },
                    )
                }.map { future -> future.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun rowCount(databasePath: Path): Int =
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM payments").use { resultSet ->
                    check(resultSet.next())
                    resultSet.getInt(1)
                }
            }
        }

    private fun insertRaw(
        connection: java.sql.Connection,
        record: StoredPaymentRecord,
    ) = insertRawValues(
        connection = connection,
        idempotencyKey = record.request.idempotencyKey,
        paymentId = record.request.intent.paymentId,
        amountMinorUnits = record.request.intent.amountMinorUnits,
        outcome = record.outcome,
    )

    private fun insertRawValues(
        connection: java.sql.Connection,
        idempotencyKey: String,
        paymentId: String,
        amountMinorUnits: Long,
        outcome: PaymentOutcome,
    ) {
        connection
            .prepareStatement(
                """
                INSERT INTO payments (
                    idempotency_key, payment_id, amount_minor_units, currency, outcome, reason
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, idempotencyKey)
                statement.setString(2, paymentId)
                statement.setLong(3, amountMinorUnits)
                statement.setString(4, PaymentCurrency.TRY.name)
                statement.setString(5, outcome.name)
                statement.setString(6, outcome.reason?.name)
                statement.executeUpdate()
            }
    }

    private fun withTemporaryDatabase(
        nested: Boolean = false,
        block: (Path) -> Unit,
    ) {
        val directory = Files.createTempDirectory("paynexus-payment-test-")
        val databasePath =
            if (nested) {
                directory.resolve("nested/data/payments.db")
            } else {
                directory.resolve("payments.db")
            }
        try {
            block(databasePath)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
