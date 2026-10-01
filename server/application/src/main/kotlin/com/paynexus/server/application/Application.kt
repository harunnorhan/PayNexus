package com.paynexus.server.application

import com.paynexus.server.application.payment.internalServerErrorResponse
import com.paynexus.server.application.payment.invalidPaymentRequestResponse
import com.paynexus.server.application.payment.paymentRoutes
import com.paynexus.server.domain.payment.AcceptedPaymentRequest
import com.paynexus.server.domain.payment.IdempotentPaymentProcessor
import com.paynexus.server.domain.payment.PaymentProcessingResult
import com.paynexus.server.domain.payment.PaymentRepositoryException
import com.paynexus.server.infrastructure.persistence.SqlitePaymentRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.file.InvalidPathException
import java.nio.file.Path

fun main() {
    embeddedServer(
        factory = Netty,
        host = "127.0.0.1",
        port = 8080,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    val repository = SqlitePaymentRepository(resolvePaymentDatabasePath(System.getenv()))
    val processor = IdempotentPaymentProcessor(repository)
    module { request ->
        withContext(Dispatchers.IO) {
            processor.process(request)
        }
    }
}

internal fun Application.module(processPayment: suspend (AcceptedPaymentRequest) -> PaymentProcessingResult) {
    install(ContentNegotiation) {
        json(
            Json {
                encodeDefaults = true
                explicitNulls = true
                ignoreUnknownKeys = false
                isLenient = false
            },
        )
    }
    install(StatusPages) {
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, invalidPaymentRequestResponse)
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, invalidPaymentRequestResponse)
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, invalidPaymentRequestResponse)
        }
        exception<PaymentRepositoryException> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, internalServerErrorResponse)
        }
    }
    routing {
        get("/health") {
            call.respondText(
                text = HEALTH_RESPONSE,
                contentType = HEALTH_CONTENT_TYPE,
                status = HttpStatusCode.OK,
            )
        }
        paymentRoutes(processPayment)
    }
}

internal fun resolvePaymentDatabasePath(environment: Map<String, String>): Path {
    val configuredPath = environment[PAYMENT_DATABASE_PATH_ENVIRONMENT_VARIABLE]
    val pathValue =
        when {
            configuredPath == null -> DEFAULT_PAYMENT_DATABASE_PATH
            configuredPath.isBlank() -> error("Payment database path must not be blank.")
            else -> configuredPath
        }
    return try {
        Path.of(pathValue)
    } catch (_: InvalidPathException) {
        error("Payment database path is invalid.")
    }
}

private const val HEALTH_RESPONSE = "{\"status\":\"ok\",\"service\":\"paynexus-payment-server\"}"
private val HEALTH_CONTENT_TYPE = ContentType.parse("application/json; charset=UTF-8")
private const val PAYMENT_DATABASE_PATH_ENVIRONMENT_VARIABLE = "PAYNEXUS_PAYMENT_DB_PATH"
private const val DEFAULT_PAYMENT_DATABASE_PATH = "./data/paynexus-payments.db"
