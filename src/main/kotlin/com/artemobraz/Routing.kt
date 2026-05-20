package com.artemobraz.com.artemobraz

import com.artemobraz.plugins.prometheusRegistry
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val version: String = "1.0.0")

fun Application.configureRouting() {
  routing {
    get("/health") {
      call.respond(HealthResponse("ok"))
    }
    get("/metrics") {
      call.respondText(prometheusRegistry.scrape(), ContentType.Text.Plain)
    }
  }
}
