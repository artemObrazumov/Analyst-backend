package com.artemobraz

import com.artemobraz.plugins.prometheusRegistry
import com.artemobraz.plugins.redis
import com.artemobraz.repository.UserRepository
import com.artemobraz.routing.authRoutes
import com.artemobraz.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val version: String = "1.0.0")

fun Application.configureRouting() {
    val authService = AuthService(environment.config, UserRepository(), redis)

    routing {
        get("/health") {
            call.respond(HealthResponse("ok"))
        }
        get("/metrics") {
            call.respondText(prometheusRegistry.scrape(), ContentType.Text.Plain)
        }
        get("/") {
            call.respondText("")
        }
        route("/api") {
            authRoutes(authService)
        }
    }
}
