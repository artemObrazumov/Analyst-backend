package com.artemobraz.routing

import com.artemobraz.model.AddFunnelStepRequest
import com.artemobraz.model.CreateFunnelRequest
import com.artemobraz.model.ReorderFunnelStepsRequest
import com.artemobraz.model.UpdateFunnelRequest
import com.artemobraz.service.FunnelService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import java.util.*

fun Route.funnelRoutes(funnelService: FunnelService) {
  authenticate("admin-jwt") {
    route("/projects/{projectId}/funnels") {
      get {
        val userId = call.userId()
        val projectId = call.pathUUID("projectId")
        call.respond(funnelService.listFunnels(userId, projectId))
      }

      post {
        val userId = call.userId()
        val projectId = call.pathUUID("projectId")
        val body = call.receive<CreateFunnelRequest>()
        call.respond(
          HttpStatusCode.Created,
          funnelService.createFunnel(userId, projectId, body.name, body.description)
        )
      }

      route("/{id}") {
        get {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          call.respond(funnelService.getFunnel(userId, projectId, id))
        }

        put {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          val body = call.receive<UpdateFunnelRequest>()
          call.respond(funnelService.updateFunnel(userId, projectId, id, body.name, body.description))
        }

        delete {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          funnelService.deleteFunnel(userId, projectId, id)
          call.respond(HttpStatusCode.NoContent)
        }

        get("/analysis") {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          val from = call.request.queryParameters["from"]?.let { Instant.parse(it) }
          val to = call.request.queryParameters["to"]?.let { Instant.parse(it) }
          call.respond(funnelService.analyzeFunnel(userId, projectId, id, from, to))
        }

        route("/steps") {
          post {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val funnelId = call.pathUUID("id")
            val body = call.receive<AddFunnelStepRequest>()
            call.respond(
              HttpStatusCode.Created,
              funnelService.addStep(userId, projectId, funnelId, body.eventType, body.propertyFilters)
            )
          }

          put("/reorder") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val funnelId = call.pathUUID("id")
            val body = call.receive<ReorderFunnelStepsRequest>()
            funnelService.reorderSteps(userId, projectId, funnelId, body.stepIds)
            call.respond(HttpStatusCode.OK, mapOf("message" to "Steps reordered"))
          }

          delete("/{stepId}") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val funnelId = call.pathUUID("id")
            val stepId = call.pathUUID("stepId")
            funnelService.removeStep(userId, projectId, funnelId, stepId)
            call.respond(HttpStatusCode.NoContent)
          }
        }
      }
    }
  }
}

private fun RoutingCall.userId(): UUID =
  UUID.fromString(principal<JWTPrincipal>()!!.payload.getClaim("userId").asString())

private fun RoutingCall.pathUUID(name: String): UUID =
  UUID.fromString(parameters[name] ?: throw IllegalArgumentException("Missing $name"))
