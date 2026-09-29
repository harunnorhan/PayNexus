package com.paynexus.server.application.payment

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

internal fun Route.paymentRoutes() {
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

            call.respond(
                status = HttpStatusCode.OK,
                message = SyntheticPaymentProcessor.process(request).toResponse(request),
            )
        }
    }
}

private fun JsonElement?.strictLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive
    return if (primitive == null || primitive.isString) null else primitive.longOrNull
}

private fun SyntheticPaymentOutcome.toResponse(request: AcceptedPaymentRequest): PaymentResponseDto {
    val (outcome, reason) =
        when (this) {
            SyntheticPaymentOutcome.Approved -> APPROVED to null
            SyntheticPaymentOutcome.Declined -> DECLINED to UNSPECIFIED
            SyntheticPaymentOutcome.Failed -> FAILED to PROCESSING_ERROR
        }
    return PaymentResponseDto(
        paymentId = request.paymentId,
        idempotencyKey = request.idempotencyKey,
        outcome = outcome,
        reason = reason,
    )
}

private const val APPROVED = "APPROVED"
private const val DECLINED = "DECLINED"
private const val FAILED = "FAILED"
private const val UNSPECIFIED = "UNSPECIFIED"
private const val PROCESSING_ERROR = "PROCESSING_ERROR"
private const val INVALID_REQUEST = "INVALID_REQUEST"
