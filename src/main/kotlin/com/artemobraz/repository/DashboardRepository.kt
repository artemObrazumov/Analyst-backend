package com.artemobraz.repository

import com.artemobraz.model.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class DashboardRepository {

  suspend fun findAllByProject(projectId: UUID): List<DashboardRow> = newSuspendedTransaction {
    Dashboards.selectAll()
      .where { Dashboards.projectId eq projectId }
      .orderBy(Dashboards.createdAt, SortOrder.DESC)
      .map { it.toDashboardRow() }
  }

  suspend fun findById(id: UUID): DashboardRow? = newSuspendedTransaction {
    Dashboards.selectAll()
      .where { Dashboards.id eq id }
      .firstOrNull()
      ?.toDashboardRow()
  }

  suspend fun create(projectId: UUID, createdBy: UUID, name: String, description: String?): DashboardRow =
    newSuspendedTransaction {
      val insertedId = Dashboards.insertAndGetId {
        it[Dashboards.projectId] = projectId
        it[Dashboards.createdBy] = createdBy
        it[Dashboards.name] = name
        it[Dashboards.description] = description
      }
      Dashboards.selectAll().where { Dashboards.id eq insertedId }.first().toDashboardRow()
    }

  suspend fun update(id: UUID, name: String?, description: String?): DashboardRow =
    newSuspendedTransaction {
      Dashboards.update({ Dashboards.id eq id }) {
        if (name != null) it[Dashboards.name] = name
        if (description != null) it[Dashboards.description] = description
        it[Dashboards.updatedAt] = Clock.System.now()
      }
      Dashboards.selectAll().where { Dashboards.id eq id }.first().toDashboardRow()
    }

  suspend fun delete(id: UUID) = newSuspendedTransaction {
    Dashboards.deleteWhere { Dashboards.id eq id }
  }

  suspend fun findCharts(dashboardId: UUID): List<DashboardChartRow> = newSuspendedTransaction {
    DashboardCharts.selectAll()
      .where { DashboardCharts.dashboardId eq dashboardId }
      .orderBy(DashboardCharts.chartOrder, SortOrder.ASC)
      .map { it.toChartRow() }
  }

  suspend fun findChartById(id: UUID): DashboardChartRow? = newSuspendedTransaction {
    DashboardCharts.selectAll()
      .where { DashboardCharts.id eq id }
      .firstOrNull()
      ?.toChartRow()
  }

  suspend fun addChart(
    dashboardId: UUID,
    title: String,
    chartType: String,
    eventType: String,
    chartOrder: Int,
    filters: ChartFilters
  ): DashboardChartRow = newSuspendedTransaction {
    val insertedId = DashboardCharts.insertAndGetId {
      it[DashboardCharts.dashboardId] = dashboardId
      it[DashboardCharts.title] = title
      it[DashboardCharts.chartType] = chartType
      it[DashboardCharts.eventType] = eventType
      it[DashboardCharts.chartOrder] = chartOrder
      it[DashboardCharts.filters] = filters.toJsonString()
    }
    DashboardCharts.selectAll().where { DashboardCharts.id eq insertedId }.first().toChartRow()
  }

  suspend fun updateChart(
    id: UUID,
    title: String,
    chartType: String,
    eventType: String,
    filters: ChartFilters
  ): DashboardChartRow = newSuspendedTransaction {
    DashboardCharts.update({ DashboardCharts.id eq id }) {
      it[DashboardCharts.title] = title
      it[DashboardCharts.chartType] = chartType
      it[DashboardCharts.eventType] = eventType
      it[DashboardCharts.filters] = filters.toJsonString()
    }
    DashboardCharts.selectAll().where { DashboardCharts.id eq id }.first().toChartRow()
  }

  suspend fun deleteChart(id: UUID) = newSuspendedTransaction {
    DashboardCharts.deleteWhere { DashboardCharts.id eq id }
  }

  suspend fun reorderCharts(dashboardId: UUID, chartIds: List<UUID>) = newSuspendedTransaction {
    val chartPredicate = { chartId: UUID ->
      (DashboardCharts.id eq chartId) and (DashboardCharts.dashboardId eq dashboardId)
    }
    chartIds.forEachIndexed { index, chartId ->
      DashboardCharts.update({ chartPredicate(chartId) }) {
        it[DashboardCharts.chartOrder] = -(index + 1)
      }
    }
    chartIds.forEachIndexed { index, chartId ->
      DashboardCharts.update({ chartPredicate(chartId) }) {
        it[DashboardCharts.chartOrder] = index + 1
      }
    }
  }

  suspend fun nextChartOrder(dashboardId: UUID): Int = newSuspendedTransaction {
    DashboardCharts.selectAll()
      .where { DashboardCharts.dashboardId eq dashboardId }
      .maxOfOrNull { it[DashboardCharts.chartOrder] }
      ?.plus(1) ?: 1
  }

  private fun ResultRow.toDashboardRow() = DashboardRow(
    id = this[Dashboards.id].value,
    projectId = this[Dashboards.projectId].value,
    createdBy = this[Dashboards.createdBy].value,
    name = this[Dashboards.name],
    description = this[Dashboards.description],
    createdAt = this[Dashboards.createdAt],
    updatedAt = this[Dashboards.updatedAt]
  )

  private fun ResultRow.toChartRow() = DashboardChartRow(
    id = this[DashboardCharts.id].value,
    dashboardId = this[DashboardCharts.dashboardId].value,
    title = this[DashboardCharts.title],
    chartType = this[DashboardCharts.chartType],
    eventType = this[DashboardCharts.eventType],
    chartOrder = this[DashboardCharts.chartOrder],
    filters = this[DashboardCharts.filters].toChartFilters()
  )
}
