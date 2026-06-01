package com.artemobraz.model

import com.artemobraz.utils.jsonb
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Dashboards : UUIDTable("dashboards") {
  val projectId = reference("project_id", Projects)
  val name = varchar("name", 255)
  val description = text("description").nullable()
}

object DashboardSeriesTable : UUIDTable("dashboard_series") {
  val dashboardId = reference("dashboard_id", Dashboards)
  val label = varchar("label", 255)
  val period = varchar("period", 20)
  val eventType = text("event_type")
  val platform = text("platform").nullable()
  val osVersion = text("os_version").nullable()
  val appVersion = text("app_version").nullable()
  val country = char("country", 2).nullable()
  val propertyFilters = jsonb("property_filters").default("{}")
  val position = integer("position")
}

data class DashboardRow(
  val id: UUID,
  val projectId: UUID,
  val name: String,
  val description: String?
)

data class DashboardSeriesRow(
  val id: UUID,
  val dashboardId: UUID,
  val label: String,
  val period: String,
  val eventType: String,
  val platform: String?,
  val osVersion: String?,
  val appVersion: String?,
  val country: String?,
  val propertyFilters: Map<String, String>,
  val position: Int
)

data class SeriesFilters(
  val platform: String? = null,
  val osVersion: String? = null,
  val appVersion: String? = null,
  val country: String? = null,
  val propertyFilters: Map<String, String> = emptyMap()
)

fun DashboardSeriesRow.toSeriesFilters() = SeriesFilters(
  platform = platform,
  osVersion = osVersion,
  appVersion = appVersion,
  country = country,
  propertyFilters = propertyFilters
)
