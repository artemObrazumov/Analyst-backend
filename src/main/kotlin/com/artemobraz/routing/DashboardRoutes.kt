package com.artemobraz.routing

import com.artemobraz.model.*
import com.artemobraz.service.DashboardService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Instant
import java.util.*

fun Route.dashboardRoutes(dashboardService: DashboardService) {
  authenticate("admin-jwt") {
    route("/projects/{projectId}/dashboards") {
      get {
        val userId = call.userId()
        val projectId = call.pathUUID("projectId")
        call.respond(dashboardService.listDashboards(userId, projectId))
      }

      post {
        val userId = call.userId()
        val projectId = call.pathUUID("projectId")
        val body = call.receive<CreateDashboardRequest>()
        call.respond(
          HttpStatusCode.Created,
          dashboardService.createDashboard(userId, projectId, body.name, body.description)
        )
      }

      route("/{id}") {
        get {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          call.respond(dashboardService.getDashboard(userId, projectId, id))
        }

        put {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          val body = call.receive<UpdateDashboardRequest>()
          call.respond(dashboardService.updateDashboard(userId, projectId, id, body.name, body.description))
        }

        delete {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          dashboardService.deleteDashboard(userId, projectId, id)
          call.respond(HttpStatusCode.NoContent)
        }

        get("/page") {
          val userId = call.userId()
          val projectId = call.pathUUID("projectId")
          val id = call.pathUUID("id")
          val from = call.request.queryParameters["from"]?.let { Instant.parse(it) }
          val to = call.request.queryParameters["to"]?.let { Instant.parse(it) }
          call.respond(dashboardService.getDashboardPage(userId, projectId, id, from, to))
        }

        route("/series") {
          post {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val dashboardId = call.pathUUID("id")
            val body = call.receive<AddDashboardSeriesRequest>()
            call.respond(
              HttpStatusCode.Created,
              dashboardService.addSeries(
                userId,
                projectId,
                dashboardId,
                body.label,
                body.period,
                body.eventType,
                body.platform,
                body.osVersion,
                body.appVersion,
                body.country,
                body.propertyFilters
              )
            )
          }

          put("/reorder") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val dashboardId = call.pathUUID("id")
            val body = call.receive<ReorderDashboardSeriesRequest>()
            dashboardService.reorderSeries(userId, projectId, dashboardId, body.seriesIds)
            call.respond(HttpStatusCode.OK, mapOf("message" to "Series reordered"))
          }

          put("/{seriesId}") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val dashboardId = call.pathUUID("id")
            val seriesId = call.pathUUID("seriesId")
            val body = call.receive<UpdateDashboardSeriesRequest>()
            call.respond(
              dashboardService.updateSeries(
                userId,
                projectId,
                dashboardId,
                seriesId,
                body.label,
                body.period,
                body.eventType,
                body.platform,
                body.osVersion,
                body.appVersion,
                body.country,
                body.propertyFilters
              )
            )
          }

          delete("/{seriesId}") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val dashboardId = call.pathUUID("id")
            val seriesId = call.pathUUID("seriesId")
            dashboardService.removeSeries(userId, projectId, dashboardId, seriesId)
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
