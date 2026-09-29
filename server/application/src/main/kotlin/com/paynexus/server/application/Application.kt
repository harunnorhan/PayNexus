package com.paynexus.server.application

import com.paynexus.server.application.payment.invalidPaymentRequestResponse
import com.paynexus.server.application.payment.paymentRoutes
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(
        factory = Netty,
        host = "127.0.0.1",
        port = 8080,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
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
    }
    routing {
        get("/health") {
            call.respondText(
                text = HEALTH_RESPONSE,
                contentType = HEALTH_CONTENT_TYPE,
                status = HttpStatusCode.OK,
            )
        }
        paymentRoutes()
    }
}

private const val HEALTH_RESPONSE = "{\"status\":\"ok\",\"service\":\"paynexus-payment-server\"}"
private val HEALTH_CONTENT_TYPE = ContentType.parse("application/json; charset=UTF-8")
