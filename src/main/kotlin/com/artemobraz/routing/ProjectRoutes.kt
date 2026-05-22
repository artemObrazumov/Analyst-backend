package com.artemobraz.routing

import com.artemobraz.model.CreateProjectRequest
import com.artemobraz.model.UpdateProjectRequest
import com.artemobraz.service.ProjectService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.projectRoutes(projectService: ProjectService) {
  authenticate("admin-jwt") {
    route("/projects") {
      get {
        val userId = call.userId()
        call.respond(projectService.listProjects(userId))
      }

      post {
        val userId = call.userId()
        val body = call.receive<CreateProjectRequest>()
        val result = projectService.createProject(userId, body.name, body.description)
        call.respond(HttpStatusCode.Created, result)
      }

      route("/{id}") {
        get {
          val userId = call.userId()
          val projectId = call.projectId()
          call.respond(projectService.getProject(userId, projectId))
        }

        put {
          val userId = call.userId()
          val projectId = call.projectId()
          val body = call.receive<UpdateProjectRequest>()
          call.respond(projectService.updateProject(userId, projectId, body.name, body.description))
        }

        delete {
          val userId = call.userId()
          val projectId = call.projectId()
          projectService.deleteProject(userId, projectId)
          call.respond(HttpStatusCode.NoContent)
        }

        route("/key") {
          get {
            val userId = call.userId()
            val projectId = call.projectId()
            call.respond(projectService.getKeyMeta(userId, projectId))
          }

          post("/rotate") {
            val userId = call.userId()
            val projectId = call.projectId()
            call.respond(projectService.rotateKey(userId, projectId))
          }
        }
      }
    }
  }
}

private fun RoutingCall.userId(): UUID =
  UUID.fromString(principal<JWTPrincipal>()!!.payload.getClaim("userId").asString())

private fun RoutingCall.projectId(): UUID =
  UUID.fromString(parameters["id"] ?: throw IllegalArgumentException("Missing project id"))
