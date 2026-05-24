package com.artemobraz.repository

import com.artemobraz.model.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

private val ANALYSIS_SQL = """
    WITH exposed_users AS (
        SELECT DISTINCT user_id
        FROM events
        WHERE project_id = CAST(? AS uuid)
          AND user_id IS NOT NULL
          AND event_type = ANY(CAST(? AS text[]))
          AND properties @> CAST(? AS jsonb)
    ),
    user_event_counts AS (
        SELECT eu.user_id, COUNT(DISTINCT e.event_type) AS event_count
        FROM exposed_users eu
        JOIN events e ON e.user_id = eu.user_id
                     AND e.project_id = CAST(? AS uuid)
                     AND e.event_type = ANY(CAST(? AS text[]))
        GROUP BY eu.user_id
    )
    SELECT
        (SELECT COUNT(*) FROM exposed_users) AS exposed,
        (SELECT COUNT(*) FROM user_event_counts WHERE event_count = ?) AS converted
""".trimIndent()

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

  suspend fun analyzeGroup(
    projectId: UUID,
    eventTypes: List<String>,
    groupPropertyKey: String,
    groupPropertyValue: String
  ): Pair<Long, Long> {
    if (eventTypes.isEmpty()) return Pair(0L, 0L)
    val pgArray = eventTypes.distinct()
      .joinToString(",", "{", "}") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
    val groupFilter = """{"$groupPropertyKey":"$groupPropertyValue"}"""
    val distinctCount = eventTypes.distinct().size
    return newSuspendedTransaction {
      exec(
        ANALYSIS_SQL,
        listOf(
          TextColumnType() to projectId.toString(),
          TextColumnType() to pgArray,
          TextColumnType() to groupFilter,
          TextColumnType() to projectId.toString(),
          TextColumnType() to pgArray,
          IntegerColumnType() to distinctCount
        ),
        explicitStatementType = StatementType.SELECT
      ) { rs ->
        if (rs.next()) Pair(rs.getLong("exposed"), rs.getLong("converted"))
        else Pair(0L, 0L)
      } ?: Pair(0L, 0L)
    }
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
