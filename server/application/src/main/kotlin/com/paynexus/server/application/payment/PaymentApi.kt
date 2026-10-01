package com.paynexus.server.application.payment

import com.paynexus.server.domain.payment.AcceptedPaymentRequest
import com.paynexus.server.domain.payment.PaymentProcessingResult
import com.paynexus.server.domain.payment.StoredPaymentRecord
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
internal data class PaymentRequestDto(
    val paymentId: String? = null,
    val idempotencyKey: String? = null,
    val amountMinorUnits: JsonElement? = null,
    val currency: String? = null,
)

@Serializable
internal data class PaymentResponseDto(
    val paymentId: String,
    val idempotencyKey: String,
    val outcome: String,
    val reason: String?,
)

@Serializable
internal data class PaymentErrorDto(
    val error: String,
)

internal val invalidPaymentRequestResponse = PaymentErrorDto(error = INVALID_REQUEST)
internal val internalServerErrorResponse = PaymentErrorDto(error = INTERNAL_ERROR)
private val idempotencyConflictResponse = PaymentErrorDto(error = IDEMPOTENCY_CONFLICT)

internal fun Route.paymentRoutes(processPayment: suspend (AcceptedPaymentRequest) -> PaymentProcessingResult) {
    route("/v1") {
        post("/payments") {
            val requestDto = call.receive<PaymentRequestDto>()
            val request =
                PaymentRequestValidator.validate(
                    paymentId = requestDto.paymentId,
                    idempotencyKey = requestDto.idempotencyKey,
                    amountMinorUnits = requestDto.amountMinorUnits.strictLongOrNull(),
                    currency = requestDto.currency,
                )
            if (request == null) {
                call.respond(HttpStatusCode.BadRequest, invalidPaymentRequestResponse)
                return@post
            }

            when (val result = processPayment(request)) {
                is PaymentProcessingResult.Created -> {
                    call.respondPayment(result.record)
                }

                is PaymentProcessingResult.Replayed -> {
                    call.respondPayment(result.record)
                }

                PaymentProcessingResult.Conflict -> {
                    call.respond(HttpStatusCode.Conflict, idempotencyConflictResponse)
                }
            }
        }
    }
}

private fun JsonElement?.strictLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive
    return if (primitive == null || primitive.isString) null else primitive.longOrNull
}

private suspend fun io.ktor.server.application.ApplicationCall.respondPayment(record: StoredPaymentRecord) {
    respond(
        status = HttpStatusCode.OK,
        message =
            PaymentResponseDto(
                paymentId = record.request.intent.paymentId,
                idempotencyKey = record.request.idempotencyKey,
                outcome = record.outcome.name,
                reason = record.outcome.reason?.name,
            ),
    )
}

private const val INVALID_REQUEST = "INVALID_REQUEST"
private const val IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT"
private const val INTERNAL_ERROR = "INTERNAL_ERROR"
