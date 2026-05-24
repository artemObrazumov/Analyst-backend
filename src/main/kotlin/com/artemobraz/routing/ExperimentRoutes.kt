package com.artemobraz.routing

import com.artemobraz.model.*
import com.artemobraz.service.ExperimentService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.experimentRoutes(experimentService: ExperimentService) {
  authenticate("admin-jwt") {
    route("/projects/{projectId}/experiments") {
      get {
        val userId = call.userId()
        val projectId = call.pathUUID("projectId")
        call.respond(experimentService.listExperiments(userId, projectId))
      }

      post {
        val userId = call.userId()
        val projectId = call.pathUUID("projectId")
        val body = call.receive<CreateExperimentRequest>()
        call.respond(
          HttpStatusCode.Created,
          experimentService.createExperiment(userId, projectId, body.name, body.description)
        )
      }

      route("/{id}") {
        get {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          call.respond(experimentService.getExperiment(userId, projectId, id))
        }

        put {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          val body = call.receive<UpdateExperimentRequest>()
          call.respond(
            experimentService.updateExperiment(
              userId,
              projectId,
              id,
              body.name,
              body.description,
              body.result
            )
          )
        }

        delete {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          experimentService.deleteExperiment(userId, projectId, id)
          call.respond(HttpStatusCode.NoContent)
        }

        get("/analysis") {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          call.respond(experimentService.analyzeExperiment(userId, projectId, id))
        }

        put("/status") {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          val body = call.receive<UpdateExperimentStatusRequest>()
          call.respond(experimentService.updateStatus(userId, projectId, id, body.status))
        }

        route("/groups") {
          post {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val experimentId = call.pathUUID("id")
            val body = call.receive<AddExperimentGroupRequest>()
            call.respond(
              HttpStatusCode.Created,
              experimentService.addGroup(
                userId,
                projectId,
                experimentId,
                body.propertyKey,
                body.propertyValue,
                body.label
              )
            )
          }

          delete("/{gid}") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val experimentId = call.pathUUID("id")
            val groupId = call.pathUUID("gid")
            experimentService.removeGroup(userId, projectId, experimentId, groupId)
            call.respond(HttpStatusCode.NoContent)
          }
        }

        route("/events") {
          post {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val experimentId = call.pathUUID("id")
            val body = call.receive<AddExperimentEventRequest>()
            call.respond(
              HttpStatusCode.Created,
              experimentService.addEvent(userId, projectId, experimentId, body.eventType, body.note)
            )
          }

          delete("/{eid}") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val experimentId = call.pathUUID("id")
            val eventId = call.pathUUID("eid")
            experimentService.removeEvent(userId, projectId, experimentId, eventId)
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
