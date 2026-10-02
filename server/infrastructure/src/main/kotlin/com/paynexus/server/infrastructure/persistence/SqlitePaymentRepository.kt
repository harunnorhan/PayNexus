package com.paynexus.server.infrastructure.persistence

import com.paynexus.server.domain.payment.AcceptedPaymentRequest
import com.paynexus.server.domain.payment.PaymentCurrency
import com.paynexus.server.domain.payment.PaymentIntent
import com.paynexus.server.domain.payment.PaymentOutcome
import com.paynexus.server.domain.payment.PaymentRepository
import com.paynexus.server.domain.payment.PaymentRepositoryException
import com.paynexus.server.domain.payment.StoreOrReadResult
import com.paynexus.server.domain.payment.StoredPaymentRecord
import org.sqlite.SQLiteConfig
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class SqlitePaymentRepository(
    databasePath: Path,
    private val busyTimeoutMillis: Int = DEFAULT_BUSY_TIMEOUT_MILLIS,
) : PaymentRepository {
    private val databasePath = databasePath.toAbsolutePath().normalize()

    init {
        require(busyTimeoutMillis >= 0) { "SQLite busy timeout must not be negative." }
        persistenceOperation {
            Files.createDirectories(requireNotNull(this.databasePath.parent))
            openConnection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(CREATE_PAYMENTS_TABLE)
                }
            }
        }
    }

    override fun storeOrRead(candidate: StoredPaymentRecord): StoreOrReadResult =
        persistenceOperation {
            openConnection().use { connection ->
                if (insert(connection, candidate)) {
                    StoreOrReadResult.Created(candidate)
                } else {
                    StoreOrReadResult.Existing(
                        readByIdempotencyKey(connection, candidate.request.idempotencyKey)
                            ?: throw IllegalStateException("Authoritative payment record is missing."),
                    )
                }
            }
        }

    override fun findByIdempotencyKey(idempotencyKey: String): StoredPaymentRecord? =
        persistenceOperation {
            openConnection().use { connection ->
                readByIdempotencyKey(connection, idempotencyKey)
            }
        }

    private fun openConnection(): Connection {
        val config = SQLiteConfig().apply { setBusyTimeout(busyTimeoutMillis) }
        return DriverManager.getConnection("jdbc:sqlite:$databasePath", config.toProperties())
    }

    private fun insert(
        connection: Connection,
        candidate: StoredPaymentRecord,
    ): Boolean =
        connection.prepareStatement(INSERT_PAYMENT).use { statement ->
            val request = candidate.request
            statement.setString(IDEMPOTENCY_KEY_PARAMETER, request.idempotencyKey)
            statement.setString(PAYMENT_ID_PARAMETER, request.intent.paymentId)
            statement.setLong(AMOUNT_MINOR_UNITS_PARAMETER, request.intent.amountMinorUnits)
            statement.setString(CURRENCY_PARAMETER, request.intent.currency.name)
            statement.setString(OUTCOME_PARAMETER, candidate.outcome.name)
            statement.setString(REASON_PARAMETER, candidate.outcome.reason?.name)
            statement.executeUpdate() == 1
        }

    private fun readByIdempotencyKey(
        connection: Connection,
        idempotencyKey: String,
    ): StoredPaymentRecord? =
        connection.prepareStatement(SELECT_PAYMENT).use { statement ->
            statement.setString(1, idempotencyKey)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toStoredPaymentRecord() else null
            }
        }

    private fun ResultSet.toStoredPaymentRecord(): StoredPaymentRecord {
        check(getString(AMOUNT_TYPE) == SQLITE_INTEGER_TYPE) { "Stored payment amount type is invalid." }
        val outcomeName = getString(OUTCOME)
        val reasonName = getString(REASON)
        val outcome =
            PaymentOutcome.entries.singleOrNull { it.name == outcomeName }
                ?: error("Stored payment outcome is invalid.")
        check(outcome.reason?.name == reasonName) { "Stored payment outcome reason is invalid." }
        val currencyName = getString(CURRENCY)
        val currency =
            PaymentCurrency.entries.singleOrNull { it.name == currencyName }
                ?: error("Stored payment currency is invalid.")
        return StoredPaymentRecord(
            request =
                AcceptedPaymentRequest(
                    idempotencyKey = getString(IDEMPOTENCY_KEY),
                    intent =
                        PaymentIntent(
                            paymentId = getString(PAYMENT_ID),
                            amountMinorUnits = getLong(AMOUNT_MINOR_UNITS),
                            currency = currency,
                        ),
                ),
            outcome = outcome,
        )
    }

    private inline fun <T> persistenceOperation(block: () -> T): T =
        try {
            block()
        } catch (exception: PaymentRepositoryException) {
            throw exception
        } catch (_: Exception) {
            throw PaymentRepositoryException()
        }

    private companion object {
        const val DEFAULT_BUSY_TIMEOUT_MILLIS = 5_000
        const val IDEMPOTENCY_KEY_PARAMETER = 1
        const val PAYMENT_ID_PARAMETER = 2
        const val AMOUNT_MINOR_UNITS_PARAMETER = 3
        const val CURRENCY_PARAMETER = 4
        const val OUTCOME_PARAMETER = 5
        const val REASON_PARAMETER = 6
        const val IDEMPOTENCY_KEY = "idempotency_key"
        const val PAYMENT_ID = "payment_id"
        const val AMOUNT_MINOR_UNITS = "amount_minor_units"
        const val AMOUNT_TYPE = "amount_type"
        const val CURRENCY = "currency"
        const val OUTCOME = "outcome"
        const val REASON = "reason"
        const val SQLITE_INTEGER_TYPE = "integer"

        val CREATE_PAYMENTS_TABLE =
            """
            CREATE TABLE IF NOT EXISTS payments (
                idempotency_key TEXT PRIMARY KEY NOT NULL,
                payment_id TEXT NOT NULL,
                amount_minor_units INTEGER NOT NULL CHECK (amount_minor_units > 0),
                currency TEXT NOT NULL CHECK (currency = 'TRY'),
                outcome TEXT NOT NULL,
                reason TEXT,
                CHECK (
                    (outcome = 'APPROVED' AND reason IS NULL) OR
                    (outcome = 'DECLINED' AND reason = 'UNSPECIFIED') OR
                    (outcome = 'FAILED' AND reason = 'PROCESSING_ERROR')
                )
            )
            """.trimIndent()

        val INSERT_PAYMENT =
            """
            INSERT INTO payments (
                idempotency_key,
                payment_id,
                amount_minor_units,
                currency,
                outcome,
                reason
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(idempotency_key) DO NOTHING
            """.trimIndent()

        val SELECT_PAYMENT =
            """
            SELECT
                idempotency_key,
                payment_id,
                amount_minor_units,
                typeof(amount_minor_units) AS amount_type,
                currency,
                outcome,
                reason
            FROM payments
            WHERE idempotency_key = ?
            """.trimIndent()
    }
}
