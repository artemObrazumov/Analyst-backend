package com.artemobraz.model

import kotlinx.serialization.Serializable

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
  val name: String,
  val description: String?
)

@Serializable
data class DashboardDetailResponse(
  val id: String,
  val projectId: String,
  val name: String,
  val description: String?,
  val series: List<DashboardSeriesResponse>
)

@Serializable
data class DashboardSeriesResponse(
  val id: String,
  val label: String,
  val period: String,
  val eventType: String,
  val platform: String? = null,
  val osVersion: String? = null,
  val appVersion: String? = null,
  val country: String? = null,
  val propertyFilters: Map<String, String> = emptyMap(),
  val position: Int
)

@Serializable
data class AddDashboardSeriesRequest(
  val label: String,
  val period: String = "7d",
  val eventType: String,
  val platform: String? = null,
  val osVersion: String? = null,
  val appVersion: String? = null,
  val country: String? = null,
  val propertyFilters: Map<String, String> = emptyMap()
)

@Serializable
data class UpdateDashboardSeriesRequest(
  val label: String,
  val period: String,
  val eventType: String,
  val platform: String? = null,
  val osVersion: String? = null,
  val appVersion: String? = null,
  val country: String? = null,
  val propertyFilters: Map<String, String> = emptyMap()
)

@Serializable
data class ReorderDashboardSeriesRequest(
  val seriesIds: List<String>
)

@Serializable
data class DashboardPagePeriod(
  val from: String?,
  val to: String?
)

@Serializable
data class SeriesDataPoint(
  val date: String,
  val count: Long
)

@Serializable
data class DashboardSeriesWithData(
  val id: String,
  val label: String,
  val period: String,
  val eventType: String,
  val platform: String? = null,
  val osVersion: String? = null,
  val appVersion: String? = null,
  val country: String? = null,
  val propertyFilters: Map<String, String> = emptyMap(),
  val position: Int,
  val data: List<SeriesDataPoint>
)

@Serializable
data class DashboardPageResponse(
  val id: String,
  val name: String,
  val description: String?,
  val period: DashboardPagePeriod,
  val series: List<DashboardSeriesWithData>
)
