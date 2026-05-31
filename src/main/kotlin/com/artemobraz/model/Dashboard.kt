package com.artemobraz.model

import com.artemobraz.utils.jsonb
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Dashboards : UUIDTable("dashboards") {
  val projectId = reference("project_id", Projects)
  val createdBy = reference("created_by", Users)
  val name = varchar("name", 255)
  val description = text("description").nullable()
  val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
  val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

object DashboardCharts : UUIDTable("dashboard_charts") {
  val dashboardId = reference("dashboard_id", Dashboards)
  val title = varchar("title", 255)
  val chartType = varchar("chart_type", 20).default("line")
  val eventType = text("event_type")
  val chartOrder = integer("chart_order")
  val filters = jsonb("filters").default("{}")
}

data class DashboardRow(
  val id: UUID,
  val projectId: UUID,
  val createdBy: UUID,
  val name: String,
  val description: String?,
  val createdAt: Instant,
  val updatedAt: Instant
)

data class DashboardChartRow(
  val id: UUID,
  val dashboardId: UUID,
  val title: String,
  val chartType: String,
  val eventType: String,
  val chartOrder: Int,
  val filters: ChartFilters
)
