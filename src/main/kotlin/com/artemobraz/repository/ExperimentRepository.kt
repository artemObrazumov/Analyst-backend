package com.artemobraz.repository

import com.artemobraz.model.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class ExperimentRepository {

  suspend fun findAllByProject(projectId: UUID): List<ExperimentRow> = newSuspendedTransaction {
    Experiments.selectAll()
      .where { Experiments.projectId eq projectId }
      .orderBy(Experiments.createdAt, SortOrder.DESC)
      .map { it.toExperimentRow() }
  }

  suspend fun findById(id: UUID): ExperimentRow? = newSuspendedTransaction {
    Experiments.selectAll()
      .where { Experiments.id eq id }
      .firstOrNull()
      ?.toExperimentRow()
  }

  suspend fun create(projectId: UUID, createdBy: UUID, name: String, description: String?): ExperimentRow =
    newSuspendedTransaction {
      val insertedId = Experiments.insertAndGetId {
        it[Experiments.projectId] = projectId
        it[Experiments.createdBy] = createdBy
        it[Experiments.name] = name
        it[Experiments.description] = description
      }
      Experiments.selectAll().where { Experiments.id eq insertedId }.first().toExperimentRow()
    }

  suspend fun update(id: UUID, name: String?, description: String?, result: String?): ExperimentRow =
    newSuspendedTransaction {
      Experiments.update({ Experiments.id eq id }) {
        if (name != null) it[Experiments.name] = name
        if (description != null) it[Experiments.description] = description
        if (result != null) it[Experiments.result] = result
        it[Experiments.updatedAt] = Clock.System.now()
      }
      Experiments.selectAll().where { Experiments.id eq id }.first().toExperimentRow()
    }

  suspend fun updateStatus(id: UUID, status: String): ExperimentRow = newSuspendedTransaction {
    Experiments.update({ Experiments.id eq id }) {
      it[Experiments.status] = status
      it[Experiments.updatedAt] = Clock.System.now()
    }
    Experiments.selectAll().where { Experiments.id eq id }.first().toExperimentRow()
  }

  suspend fun delete(id: UUID) = newSuspendedTransaction {
    Experiments.deleteWhere { Experiments.id eq id }
  }

  suspend fun findGroups(experimentId: UUID): List<ExperimentGroupRow> = newSuspendedTransaction {
    ExperimentGroups.selectAll()
      .where { ExperimentGroups.experimentId eq experimentId }
      .map { it.toGroupRow() }
  }

  suspend fun findGroupById(id: UUID): ExperimentGroupRow? = newSuspendedTransaction {
    ExperimentGroups.selectAll()
      .where { ExperimentGroups.id eq id }
      .firstOrNull()
      ?.toGroupRow()
  }

  suspend fun addGroup(
    experimentId: UUID,
    propertyKey: String,
    propertyValue: String,
    label: String
  ): ExperimentGroupRow = newSuspendedTransaction {
    val insertedId = ExperimentGroups.insertAndGetId {
      it[ExperimentGroups.experimentId] = experimentId
      it[ExperimentGroups.propertyKey] = propertyKey
      it[ExperimentGroups.propertyValue] = propertyValue
      it[ExperimentGroups.label] = label
    }
    ExperimentGroups.selectAll().where { ExperimentGroups.id eq insertedId }.first().toGroupRow()
  }

  suspend fun deleteGroup(id: UUID) = newSuspendedTransaction {
    ExperimentGroups.deleteWhere { ExperimentGroups.id eq id }
  }

  suspend fun findExperimentEvents(experimentId: UUID): List<ExperimentEventRow> = newSuspendedTransaction {
    ExperimentEvents.selectAll()
      .where { ExperimentEvents.experimentId eq experimentId }
      .map { it.toEventRow() }
  }

  suspend fun findExperimentEventById(id: UUID): ExperimentEventRow? = newSuspendedTransaction {
    ExperimentEvents.selectAll()
      .where { ExperimentEvents.id eq id }
      .firstOrNull()
      ?.toEventRow()
  }

  suspend fun addExperimentEvent(experimentId: UUID, eventType: String, note: String?): ExperimentEventRow =
    newSuspendedTransaction {
      val insertedId = ExperimentEvents.insertAndGetId {
        it[ExperimentEvents.experimentId] = experimentId
        it[ExperimentEvents.eventType] = eventType
        it[ExperimentEvents.note] = note
      }
      ExperimentEvents.selectAll().where { ExperimentEvents.id eq insertedId }.first().toEventRow()
    }

  suspend fun deleteExperimentEvent(id: UUID) = newSuspendedTransaction {
    ExperimentEvents.deleteWhere { ExperimentEvents.id eq id }
  }

  private fun ResultRow.toExperimentRow() = ExperimentRow(
    id = this[Experiments.id].value,
    projectId = this[Experiments.projectId].value,
    createdBy = this[Experiments.createdBy].value,
    name = this[Experiments.name],
    description = this[Experiments.description],
    status = this[Experiments.status],
    result = this[Experiments.result],
    createdAt = this[Experiments.createdAt],
    updatedAt = this[Experiments.updatedAt]
  )

  private fun ResultRow.toGroupRow() = ExperimentGroupRow(
    id = this[ExperimentGroups.id].value,
    experimentId = this[ExperimentGroups.experimentId].value,
    propertyKey = this[ExperimentGroups.propertyKey],
    propertyValue = this[ExperimentGroups.propertyValue],
    label = this[ExperimentGroups.label]
  )

  private fun ResultRow.toEventRow() = ExperimentEventRow(
    id = this[ExperimentEvents.id].value,
    experimentId = this[ExperimentEvents.experimentId].value,
    eventType = this[ExperimentEvents.eventType],
    note = this[ExperimentEvents.note]
  )
}
