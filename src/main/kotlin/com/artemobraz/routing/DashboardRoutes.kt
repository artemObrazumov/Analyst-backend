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

        route("/charts") {
          post {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val dashboardId = call.pathUUID("id")
            val body = call.receive<AddDashboardChartRequest>()
            call.respond(
              HttpStatusCode.Created,
              dashboardService.addChart(
                userId, projectId, dashboardId, body.title, body.chartType, body.eventType, body.filters
              )
            )
          }

          put("/reorder") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val dashboardId = call.pathUUID("id")
            val body = call.receive<ReorderDashboardChartsRequest>()
            dashboardService.reorderCharts(userId, projectId, dashboardId, body.chartIds)
            call.respond(HttpStatusCode.OK, mapOf("message" to "Charts reordered"))
          }

          put("/{chartId}") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val dashboardId = call.pathUUID("id")
            val chartId = call.pathUUID("chartId")
            val body = call.receive<UpdateDashboardChartRequest>()
            call.respond(
              dashboardService.updateChart(
                userId,
                projectId,
                dashboardId,
                chartId,
                body.title,
                body.chartType,
                body.eventType,
                body.filters
              )
            )
          }

          delete("/{chartId}") {
            val userId = call.userId()
            val projectId = call.pathUUID("projectId")
            val dashboardId = call.pathUUID("id")
            val chartId = call.pathUUID("chartId")
            dashboardService.removeChart(userId, projectId, dashboardId, chartId)
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
