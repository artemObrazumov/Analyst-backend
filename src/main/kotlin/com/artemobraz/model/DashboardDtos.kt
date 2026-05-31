package com.artemobraz.model

import kotlinx.serialization.Serializable

@Serializable
data class ChartFilters(
  val platform: String? = null,
  val country: String? = null,
  val deviceId: String? = null,
  val userId: String? = null,
  val appVersion: String? = null,
  val osVersion: String? = null,
  val properties: Map<String, String> = emptyMap()
)

@Serializable
data class CreateDashboardRequest(
  val name: String,
  val description: String? = null
)

@Serializable
data class UpdateDashboardRequest(
  val name: String? = null,
  val description: String? = null
)

@Serializable
data class DashboardResponse(
  val id: String,
  val projectId: String,
  val createdBy: String,
  val name: String,
  val description: String?,
  val createdAt: String,
  val updatedAt: String
)

@Serializable
data class DashboardDetailResponse(
  val id: String,
  val projectId: String,
  val createdBy: String,
  val name: String,
  val description: String?,
  val createdAt: String,
  val updatedAt: String,
  val charts: List<DashboardChartResponse>
)

@Serializable
data class DashboardChartResponse(
  val id: String,
  val title: String,
  val chartType: String,
  val eventType: String,
  val chartOrder: Int,
  val filters: ChartFilters = ChartFilters()
)

@Serializable
data class AddDashboardChartRequest(
  val title: String,
  val chartType: String = "line",
  val eventType: String,
  val filters: ChartFilters = ChartFilters()
)

@Serializable
data class UpdateDashboardChartRequest(
  val title: String,
  val chartType: String = "line",
  val eventType: String,
  val filters: ChartFilters = ChartFilters()
)

@Serializable
data class ReorderDashboardChartsRequest(
  val chartIds: List<String>
)

@Serializable
data class DashboardPagePeriod(
  val from: String?,
  val to: String?
)

@Serializable
data class ChartDataPoint(
  val date: String,
  val count: Long
)

@Serializable
data class DashboardChartWithData(
  val id: String,
  val title: String,
  val chartType: String,
  val eventType: String,
  val chartOrder: Int,
  val filters: ChartFilters = ChartFilters(),
  val data: List<ChartDataPoint>
)

@Serializable
data class DashboardPageResponse(
  val id: String,
  val name: String,
  val description: String?,
  val period: DashboardPagePeriod,
  val charts: List<DashboardChartWithData>
)
