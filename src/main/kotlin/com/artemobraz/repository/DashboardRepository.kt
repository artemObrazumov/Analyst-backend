package com.artemobraz.repository

import com.artemobraz.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class DashboardRepository {

  suspend fun findAllByProject(projectId: UUID): List<DashboardRow> = newSuspendedTransaction {
    Dashboards.selectAll()
      .where { Dashboards.projectId eq projectId }
      .orderBy(Dashboards.name, SortOrder.ASC)
      .map { it.toDashboardRow() }
  }

  suspend fun findById(id: UUID): DashboardRow? = newSuspendedTransaction {
    Dashboards.selectAll()
      .where { Dashboards.id eq id }
      .firstOrNull()
      ?.toDashboardRow()
  }

  suspend fun create(projectId: UUID, name: String, description: String?): DashboardRow =
    newSuspendedTransaction {
      val insertedId = Dashboards.insertAndGetId {
        it[Dashboards.projectId] = projectId
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
      }
      Dashboards.selectAll().where { Dashboards.id eq id }.first().toDashboardRow()
    }

  suspend fun delete(id: UUID) = newSuspendedTransaction {
    Dashboards.deleteWhere { Dashboards.id eq id }
  }

  suspend fun findSeries(dashboardId: UUID): List<DashboardSeriesRow> = newSuspendedTransaction {
    DashboardSeriesTable.selectAll()
      .where { DashboardSeriesTable.dashboardId eq dashboardId }
      .orderBy(DashboardSeriesTable.position, SortOrder.ASC)
      .map { it.toSeriesRow() }
  }

  suspend fun findSeriesById(id: UUID): DashboardSeriesRow? = newSuspendedTransaction {
    DashboardSeriesTable.selectAll()
      .where { DashboardSeriesTable.id eq id }
      .firstOrNull()
      ?.toSeriesRow()
  }

  suspend fun addSeries(
    dashboardId: UUID,
    label: String,
    period: String,
    eventType: String,
    platform: String?,
    osVersion: String?,
    appVersion: String?,
    country: String?,
    propertyFilters: Map<String, String>,
    position: Int
  ): DashboardSeriesRow = newSuspendedTransaction {
    val insertedId = DashboardSeriesTable.insertAndGetId {
      it[DashboardSeriesTable.dashboardId] = dashboardId
      it[DashboardSeriesTable.label] = label
      it[DashboardSeriesTable.period] = period
      it[DashboardSeriesTable.eventType] = eventType
      it[DashboardSeriesTable.platform] = platform
      it[DashboardSeriesTable.osVersion] = osVersion
      it[DashboardSeriesTable.appVersion] = appVersion
      it[DashboardSeriesTable.country] = country
      it[DashboardSeriesTable.propertyFilters] = propertyFilters.toPropertyFiltersJson()
      it[DashboardSeriesTable.position] = position
    }
    DashboardSeriesTable.selectAll().where { DashboardSeriesTable.id eq insertedId }.first().toSeriesRow()
  }

  suspend fun updateSeries(
    id: UUID,
    label: String,
    period: String,
    eventType: String,
    platform: String?,
    osVersion: String?,
    appVersion: String?,
    country: String?,
    propertyFilters: Map<String, String>
  ): DashboardSeriesRow = newSuspendedTransaction {
    DashboardSeriesTable.update({ DashboardSeriesTable.id eq id }) {
      it[DashboardSeriesTable.label] = label
      it[DashboardSeriesTable.period] = period
      it[DashboardSeriesTable.eventType] = eventType
      it[DashboardSeriesTable.platform] = platform
      it[DashboardSeriesTable.osVersion] = osVersion
      it[DashboardSeriesTable.appVersion] = appVersion
      it[DashboardSeriesTable.country] = country
      it[DashboardSeriesTable.propertyFilters] = propertyFilters.toPropertyFiltersJson()
    }
    DashboardSeriesTable.selectAll().where { DashboardSeriesTable.id eq id }.first().toSeriesRow()
  }

  suspend fun deleteSeries(id: UUID) = newSuspendedTransaction {
    DashboardSeriesTable.deleteWhere { DashboardSeriesTable.id eq id }
  }

  suspend fun reorderSeries(dashboardId: UUID, seriesIds: List<UUID>) = newSuspendedTransaction {
    val seriesPredicate = { seriesId: UUID ->
      (DashboardSeriesTable.id eq seriesId) and (DashboardSeriesTable.dashboardId eq dashboardId)
    }
    seriesIds.forEachIndexed { index, seriesId ->
      DashboardSeriesTable.update({ seriesPredicate(seriesId) }) {
        it[DashboardSeriesTable.position] = -(index + 1)
      }
    }
    seriesIds.forEachIndexed { index, seriesId ->
      DashboardSeriesTable.update({ seriesPredicate(seriesId) }) {
        it[DashboardSeriesTable.position] = index + 1
      }
    }
  }

  suspend fun nextSeriesPosition(dashboardId: UUID): Int = newSuspendedTransaction {
    DashboardSeriesTable.selectAll()
      .where { DashboardSeriesTable.dashboardId eq dashboardId }
      .maxOfOrNull { it[DashboardSeriesTable.position] }
      ?.plus(1) ?: 1
  }

  private fun ResultRow.toDashboardRow() = DashboardRow(
    id = this[Dashboards.id].value,
    projectId = this[Dashboards.projectId].value,
    name = this[Dashboards.name],
    description = this[Dashboards.description]
  )

  private fun ResultRow.toSeriesRow() = DashboardSeriesRow(
    id = this[DashboardSeriesTable.id].value,
    dashboardId = this[DashboardSeriesTable.dashboardId].value,
    label = this[DashboardSeriesTable.label],
    period = this[DashboardSeriesTable.period],
    eventType = this[DashboardSeriesTable.eventType],
    platform = this[DashboardSeriesTable.platform],
    osVersion = this[DashboardSeriesTable.osVersion],
    appVersion = this[DashboardSeriesTable.appVersion],
    country = this[DashboardSeriesTable.country],
    propertyFilters = this[DashboardSeriesTable.propertyFilters].toPropertyFilters(),
    position = this[DashboardSeriesTable.position]
  )
}
