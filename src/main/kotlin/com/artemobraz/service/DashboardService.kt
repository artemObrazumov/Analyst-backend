package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.DashboardRepository
import com.artemobraz.repository.EventRepository
import com.artemobraz.repository.ProjectRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import java.time.ZoneOffset
import java.util.*

class DashboardService(
  private val dashboardRepository: DashboardRepository,
  private val projectRepository: ProjectRepository,
  private val eventRepository: EventRepository
) {

  private val validPeriods = setOf("7d", "30d", "90d")

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
    return dashboardRepository.create(projectId, name, description).toResponse()
  }

  suspend fun getDashboard(userId: UUID, projectId: UUID, id: UUID): DashboardDetailResponse {
    assertProjectAccess(userId, projectId)
    val dashboard = assertDashboardBelongsToProject(id, projectId)
    val series = dashboardRepository.findSeries(id).map { it.toSeriesResponse() }
    return dashboard.toDetailResponse(series)
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

  suspend fun addSeries(
    userId: UUID,
    projectId: UUID,
    dashboardId: UUID,
    label: String,
    period: String,
    eventType: String,
    platform: String?,
    osVersion: String?,
    appVersion: String?,
    country: String?,
    propertyFilters: Map<String, String>
  ): DashboardSeriesResponse {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(dashboardId, projectId)
    if (label.isBlank()) throw IllegalArgumentException("Series label is required")
    if (eventType.isBlank()) throw IllegalArgumentException("Event type is required")
    validatePeriod(period)
    validateSeriesFilters(country, propertyFilters)
    val nextPosition = dashboardRepository.nextSeriesPosition(dashboardId)
    return dashboardRepository.addSeries(
      dashboardId, label, period, eventType, platform, osVersion, appVersion, country, propertyFilters, nextPosition
    ).toSeriesResponse()
  }

  suspend fun updateSeries(
    userId: UUID,
    projectId: UUID,
    dashboardId: UUID,
    seriesId: UUID,
    label: String,
    period: String,
    eventType: String,
    platform: String?,
    osVersion: String?,
    appVersion: String?,
    country: String?,
    propertyFilters: Map<String, String>
  ): DashboardSeriesResponse {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(dashboardId, projectId)
    val series = dashboardRepository.findSeriesById(seriesId) ?: throw NotFoundException("Series not found")
    if (series.dashboardId != dashboardId) throw NotFoundException("Series not found")
    if (label.isBlank()) throw IllegalArgumentException("Series label is required")
    if (eventType.isBlank()) throw IllegalArgumentException("Event type is required")
    validatePeriod(period)
    validateSeriesFilters(country, propertyFilters)
    return dashboardRepository.updateSeries(
      seriesId, label, period, eventType, platform, osVersion, appVersion, country, propertyFilters
    ).toSeriesResponse()
  }

  suspend fun removeSeries(userId: UUID, projectId: UUID, dashboardId: UUID, seriesId: UUID) {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(dashboardId, projectId)
    val series = dashboardRepository.findSeriesById(seriesId) ?: throw NotFoundException("Series not found")
    if (series.dashboardId != dashboardId) throw NotFoundException("Series not found")
    dashboardRepository.deleteSeries(seriesId)
  }

  suspend fun reorderSeries(userId: UUID, projectId: UUID, dashboardId: UUID, seriesIds: List<String>) {
    assertProjectAccess(userId, projectId)
    assertDashboardBelongsToProject(dashboardId, projectId)
    val uuids = seriesIds.map { UUID.fromString(it) }
    val existing = dashboardRepository.findSeries(dashboardId).map { it.id }.toSet()
    if (uuids.toSet() != existing) {
      throw IllegalArgumentException("seriesIds must contain exactly all series of the dashboard")
    }
    dashboardRepository.reorderSeries(dashboardId, uuids)
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

    val seriesList = dashboardRepository.findSeries(dashboardId)
    val seriesWithData = seriesList.map { series ->
      val (resolvedFrom, resolvedTo) = resolvePeriodRange(series.period, from, to)
      val filters = series.toSeriesFilters()
      val sparse = eventRepository.countEventsByDay(projectId, series.eventType, filters, resolvedFrom, resolvedTo)
        .map { SeriesDataPoint(date = it.date, count = it.count) }
      val data = if (resolvedFrom != null && resolvedTo != null) {
        fillDailySeriesWithZeros(sparse, resolvedFrom, resolvedTo)
      } else {
        sparse
      }
      DashboardSeriesWithData(
        id = series.id.toString(),
        label = series.label,
        period = series.period,
        eventType = series.eventType,
        platform = series.platform,
        osVersion = series.osVersion,
        appVersion = series.appVersion,
        country = series.country,
        propertyFilters = series.propertyFilters,
        position = series.position,
        data = data
      )
    }

    return DashboardPageResponse(
      id = dashboard.id.toString(),
      name = dashboard.name,
      description = dashboard.description,
      period = DashboardPagePeriod(from?.toString(), to?.toString()),
      series = seriesWithData
    )
  }

  private fun validatePeriod(period: String) {
    if (period !in validPeriods) {
      throw IllegalArgumentException("Period must be one of: ${validPeriods.joinToString()}")
    }
  }

  private fun validateSeriesFilters(country: String?, propertyFilters: Map<String, String>) {
    country?.let {
      if (it.length != 2) throw IllegalArgumentException("Country must be a 2-letter ISO code")
    }
    propertyFilters.forEach { (key, value) ->
      if (key.isBlank()) throw IllegalArgumentException("Property filter key cannot be blank")
      if (value.isBlank()) throw IllegalArgumentException("Property filter value cannot be blank")
    }
  }

  private fun resolvePeriodRange(
    seriesPeriod: String,
    overrideFrom: Instant?,
    overrideTo: Instant?
  ): Pair<Instant?, Instant?> {
    if (overrideFrom != null || overrideTo != null) {
      return overrideFrom to overrideTo
    }
    validatePeriod(seriesPeriod)
    val periodTo = Clock.System.now()
    val days = when (seriesPeriod) {
      "7d" -> 7
      "30d" -> 30
      "90d" -> 90
      else -> throw IllegalArgumentException("Unsupported period: $seriesPeriod")
    }
    val periodFrom = periodTo.minus(days, DateTimeUnit.DAY, TimeZone.UTC)
    return periodFrom to periodTo
  }

  private fun fillDailySeriesWithZeros(
    sparse: List<SeriesDataPoint>,
    from: Instant,
    to: Instant
  ): List<SeriesDataPoint> {
    val countsByDate = sparse.associateBy { it.date }
    var day = java.time.Instant.ofEpochMilli(from.toEpochMilliseconds())
      .atZone(ZoneOffset.UTC)
      .toLocalDate()
    val endDay = java.time.Instant.ofEpochMilli(to.toEpochMilliseconds())
      .atZone(ZoneOffset.UTC)
      .toLocalDate()
    if (day.isAfter(endDay)) return emptyList()

    val result = ArrayList<SeriesDataPoint>()
    while (!day.isAfter(endDay)) {
      val dateStr = day.toString()
      result.add(SeriesDataPoint(date = dateStr, count = countsByDate[dateStr]?.count ?: 0L))
      day = day.plusDays(1)
    }
    return result
  }

  private fun DashboardRow.toResponse() = DashboardResponse(
    id = id.toString(),
    projectId = projectId.toString(),
    name = name,
    description = description
  )

  private fun DashboardRow.toDetailResponse(series: List<DashboardSeriesResponse>) = DashboardDetailResponse(
    id = id.toString(),
    projectId = projectId.toString(),
    name = name,
    description = description,
    series = series
  )

  private fun DashboardSeriesRow.toSeriesResponse() = DashboardSeriesResponse(
    id = id.toString(),
    label = label,
    period = period,
    eventType = eventType,
    platform = platform,
    osVersion = osVersion,
    appVersion = appVersion,
    country = country,
    propertyFilters = propertyFilters,
    position = position
  )
}
