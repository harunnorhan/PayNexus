package com.paynexus.server.application

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(
        factory = Netty,
        host = "127.0.0.1",
        port = 8080,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    routing {
        get("/health") {
            call.respondText(
                text = HEALTH_RESPONSE,
                contentType = HEALTH_CONTENT_TYPE,
                status = HttpStatusCode.OK,
            )
        }
    }
}

private const val HEALTH_RESPONSE = "{\"status\":\"ok\",\"service\":\"paynexus-payment-server\"}"
private val HEALTH_CONTENT_TYPE = ContentType.parse("application/json; charset=UTF-8")
