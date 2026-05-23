package com.artemobraz

import com.artemobraz.plugins.prometheusRegistry
import com.artemobraz.plugins.redis
import com.artemobraz.repository.EventRepository
import com.artemobraz.repository.ProjectRepository
import com.artemobraz.repository.UserRepository
import com.artemobraz.routing.authRoutes
import com.artemobraz.routing.eventRoutes
import com.artemobraz.routing.projectRoutes
import com.artemobraz.routing.userRoutes
import com.artemobraz.service.AuthService
import com.artemobraz.service.EventQueryService
import com.artemobraz.service.ProjectService
import com.artemobraz.service.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val version: String = "1.0.0")

fun Application.configureRouting() {
    val userRepository = UserRepository()
    val projectRepository = ProjectRepository()
    val eventRepository = EventRepository()
    val authService = AuthService(environment.config, userRepository, redis)
    val userService = UserService(userRepository)
    val projectService = ProjectService(projectRepository)
    val eventQueryService = EventQueryService(eventRepository, projectRepository)
    monitor.subscribe(ApplicationStopped) { eventQueryService.shutdown() }

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
            userRoutes(userService, authService)
            projectRoutes(projectService)
            eventRoutes(eventQueryService)
        }
    }
}
