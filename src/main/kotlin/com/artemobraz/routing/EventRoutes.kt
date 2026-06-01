package com.artemobraz.routing

import com.artemobraz.model.IngestEventsRequest
import com.artemobraz.model.IngestEventsResponse
import com.artemobraz.service.EventQueryService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import java.util.*

fun Route.eventRoutes(eventQueryService: EventQueryService) {
    route("/events") {
        post("/ingest") {
            val apiKey = call.request.headers["X-API-Key"]
                ?: return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "UNAUTHORIZED", "message" to "Missing X-API-Key header")
                )
            val body = call.receive<IngestEventsRequest>()
            val accepted = eventQueryService.ingestBatch(apiKey, body.events)
            call.respond(HttpStatusCode.OK, IngestEventsResponse(accepted = accepted))
        }

        authenticate("admin-jwt") {
            get {
                val projectId = UUID.fromString(
                    call.request.queryParameters["projectId"]
                        ?: return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "BAD_REQUEST", "message" to "projectId is required")
                        )
                )
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val from = call.request.queryParameters["from"]?.let { Instant.parse(it) }
                val to = call.request.queryParameters["to"]?.let { Instant.parse(it) }
                call.respond(eventQueryService.list(projectId, limit, from, to))
            }
        }
    }
}
