package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.DashboardRepository
import com.artemobraz.repository.EventRepository
import com.artemobraz.repository.ProjectRepository
import kotlinx.datetime.Instant
import java.time.ZoneOffset
import java.util.*

class DashboardService(
  private val dashboardRepository: DashboardRepository,
  private val projectRepository: ProjectRepository,
  private val eventRepository: EventRepository
) {

  private val validChartTypes = setOf("line", "bar", "area")

  private suspend fun assertProjectAccess(userId: UUID, projectId: UUID) {
    val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
    if (project.ownerId != userId) throw ForbiddenException("Access denied")
  }

  private suspend fun assertDashboardBelongsToProject(dashboardId: UUID, projectId: UUID): DashboardRow {
    val dashboard = dashboardRepository.findById(dashboardId) ?: throw NotFoundException("Dashboard not found")
    if (dashboard.projectId != projectId) throw NotFoundException("Dashboard not found")
    return dashboard
  }

  suspend fun listDashboards(userId: UUID, projectId: UUID): List<DashboardResponse> {
    assertProjectAccess(userId, projectId)
    return dashboardRepository.findAllByProject(projectId).map { it.toResponse() }
  }

  suspend fun createDashboard(userId: UUID, projectId: UUID, name: String, description: String?): DashboardResponse {
    assertProjectAccess(userId, projectId)
    if (name.isBlank()) throw IllegalArgumentException("Dashboard name is required")
    return dashboardRepository.create(projectId, userId, name, description).toResponse()
  }

  suspend fun getDashboard(userId: UUID, projectId: UUID, id: UUID): DashboardDetailResponse {
    assertProjectAccess(userId, projectId)
    val dashboard = assertDashboardBelongsToProject(id, projectId)
    val charts = dashboardRepository.findCharts(id).map { it.toChartResponse() }
    return dashboard.toDetailResponse(charts)
  }

  suspend fun updateDashboard(
    userId: UUID,
    projectId: UUID,
    id: UUID,
    name: String?,
    description: String?
  ): DashboardResponse {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(id, projectId)
    if (name != null && name.isBlank()) throw IllegalArgumentException("Dashboard name cannot be blank")
    return dashboardRepository.update(id, name, description).toResponse()
  }

  suspend fun deleteDashboard(userId: UUID, projectId: UUID, id: UUID) {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(id, projectId)
    dashboardRepository.delete(id)
  }

  suspend fun addChart(
    userId: UUID,
    projectId: UUID,
    dashboardId: UUID,
    title: String,
    chartType: String,
    eventType: String,
    filters: ChartFilters
  ): DashboardChartResponse {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(dashboardId, projectId)
    if (title.isBlank()) throw IllegalArgumentException("Chart title is required")
    if (eventType.isBlank()) throw IllegalArgumentException("Event type is required")
    validateFilters(filters)
    val normalizedType = chartType.lowercase()
    if (normalizedType !in validChartTypes) {
      throw IllegalArgumentException("Chart type must be one of: ${validChartTypes.joinToString()}")
    }
    val nextOrder = dashboardRepository.nextChartOrder(dashboardId)
    return dashboardRepository.addChart(
      dashboardId, title, normalizedType, eventType, nextOrder, filters
    ).toChartResponse()
  }

  private fun validateFilters(filters: ChartFilters) {
    filters.country?.let { country ->
      if (country.length != 2) throw IllegalArgumentException("Country must be a 2-letter ISO code")
    }
    filters.properties.forEach { (key, value) ->
      if (key.isBlank()) throw IllegalArgumentException("Property filter key cannot be blank")
      if (value.isBlank()) throw IllegalArgumentException("Property filter value cannot be blank")
    }
  }

  suspend fun updateChart(
    userId: UUID,
    projectId: UUID,
    dashboardId: UUID,
    chartId: UUID,
    title: String,
    chartType: String,
    eventType: String,
    filters: ChartFilters
  ): DashboardChartResponse {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(dashboardId, projectId)
    val chart = dashboardRepository.findChartById(chartId) ?: throw NotFoundException("Chart not found")
    if (chart.dashboardId != dashboardId) throw NotFoundException("Chart not found")
    if (title.isBlank()) throw IllegalArgumentException("Chart title is required")
    if (eventType.isBlank()) throw IllegalArgumentException("Event type is required")
    validateFilters(filters)
    val normalizedType = chartType.lowercase()
    if (normalizedType !in validChartTypes) {
      throw IllegalArgumentException("Chart type must be one of: ${validChartTypes.joinToString()}")
    }
    return dashboardRepository.updateChart(
      chartId, title, normalizedType, eventType, filters
    ).toChartResponse()
  }

  suspend fun removeChart(userId: UUID, projectId: UUID, dashboardId: UUID, chartId: UUID) {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(dashboardId, projectId)
    val chart = dashboardRepository.findChartById(chartId) ?: throw NotFoundException("Chart not found")
    if (chart.dashboardId != dashboardId) throw NotFoundException("Chart not found")
    dashboardRepository.deleteChart(chartId)
  }

  suspend fun reorderCharts(userId: UUID, projectId: UUID, dashboardId: UUID, chartIds: List<String>) {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(dashboardId, projectId)
    val uuids = chartIds.map { UUID.fromString(it) }
    val existing = dashboardRepository.findCharts(dashboardId).map { it.id }.toSet()
    if (uuids.toSet() != existing) {
      throw IllegalArgumentException("chartIds must contain exactly all charts of the dashboard")
    }
    dashboardRepository.reorderCharts(dashboardId, uuids)
  }

  suspend fun getDashboardPage(
    userId: UUID,
    projectId: UUID,
    dashboardId: UUID,
    from: Instant?,
    to: Instant?
  ): DashboardPageResponse {
    assertProjectAccess(userId, projectId)
    val dashboard = assertDashboardBelongsToProject(dashboardId, projectId)
    if (from != null && to != null && from > to) {
      throw IllegalArgumentException("'from' must be before or equal to 'to'")
    }

    val charts = dashboardRepository.findCharts(dashboardId)
    val chartsWithData = charts.map { chart ->
      val sparse = eventRepository.countEventsByDay(projectId, chart.eventType, chart.filters, from, to)
        .map { ChartDataPoint(date = it.date, count = it.count) }
      val data = if (from != null && to != null) {
        fillDailySeriesWithZeros(sparse, from, to)
      } else {
        sparse
      }
      DashboardChartWithData(
        id = chart.id.toString(),
        title = chart.title,
        chartType = chart.chartType,
        eventType = chart.eventType,
        chartOrder = chart.chartOrder,
        filters = chart.filters,
        data = data
      )
    }

    return DashboardPageResponse(
      id = dashboard.id.toString(),
      name = dashboard.name,
      description = dashboard.description,
      period = DashboardPagePeriod(from?.toString(), to?.toString()),
      charts = chartsWithData
    )
  }

  /**
   * Дополняет ряд точками с count = 0 для каждого календарного дня UTC в [from, to].
   * SQL GROUP BY возвращает только дни с событиями.
   */
  private fun fillDailySeriesWithZeros(
    sparse: List<ChartDataPoint>,
    from: Instant,
    to: Instant
  ): List<ChartDataPoint> {
    val countsByDate = sparse.associateBy { it.date }
    var day = java.time.Instant.ofEpochMilli(from.toEpochMilliseconds())
      .atZone(ZoneOffset.UTC)
      .toLocalDate()
    val endDay = java.time.Instant.ofEpochMilli(to.toEpochMilliseconds())
      .atZone(ZoneOffset.UTC)
      .toLocalDate()
    if (day.isAfter(endDay)) return emptyList()

    val result = ArrayList<ChartDataPoint>()
    while (!day.isAfter(endDay)) {
      val dateStr = day.toString()
      result.add(ChartDataPoint(date = dateStr, count = countsByDate[dateStr]?.count ?: 0L))
      day = day.plusDays(1)
    }
    return result
  }

  private fun DashboardRow.toResponse() = DashboardResponse(
    id = id.toString(),
    projectId = projectId.toString(),
    createdBy = createdBy.toString(),
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
  )

  private fun DashboardRow.toDetailResponse(charts: List<DashboardChartResponse>) = DashboardDetailResponse(
    id = id.toString(),
    projectId = projectId.toString(),
    createdBy = createdBy.toString(),
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    charts = charts
  )

  private fun DashboardChartRow.toChartResponse() = DashboardChartResponse(
    id = id.toString(),
    title = title,
    chartType = chartType,
    eventType = eventType,
    chartOrder = chartOrder,
    filters = filters
  )
}
