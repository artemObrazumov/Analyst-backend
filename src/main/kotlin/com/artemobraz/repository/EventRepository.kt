package com.artemobraz.repository

import com.artemobraz.model.EventRow
import com.artemobraz.model.Events
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

data class UserEventFirstOccurrence(
  val userId: String,
  val eventType: String,
  val firstOccurredAt: Instant
)

private const val FIRST_OCCURRENCES_SQL = """
    SELECT user_id, event_type, MIN(occurred_at) AS first_at
    FROM events
    WHERE project_id = CAST(? AS uuid)
      AND user_id IS NOT NULL
      AND event_type = ANY(CAST(? AS text[]))
"""

class EventRepository {

    suspend fun create(
        projectId: UUID,
        eventType: String,
        occurredAt: Instant,
        sessionId: UUID?,
        deviceId: String?,
        userId: String?,
        platform: String?,
        appVersion: String?,
        osVersion: String?,
        country: String?,
        properties: String
    ): EventRow = newSuspendedTransaction {
        val newId = UUID.randomUUID()
        val receivedAt = Clock.System.now()
        Events.insert {
            it[Events.id] = newId
            it[Events.projectId] = projectId
            it[Events.occurredAt] = occurredAt
            it[Events.receivedAt] = receivedAt
            it[Events.sessionId] = sessionId
            it[Events.deviceId] = deviceId
            it[Events.userId] = userId
            it[Events.eventType] = eventType
            it[Events.platform] = platform
            it[Events.appVersion] = appVersion
            it[Events.osVersion] = osVersion
            it[Events.country] = country
            it[Events.properties] = properties
        }
        EventRow(
            id = newId,
            projectId = projectId,
            occurredAt = occurredAt,
            receivedAt = receivedAt,
            sessionId = sessionId,
            deviceId = deviceId,
            userId = userId,
            eventType = eventType,
            platform = platform,
            appVersion = appVersion,
            osVersion = osVersion,
            country = country,
            properties = properties
        )
    }

  suspend fun findFirstOccurrencesByUserAndType(
    projectId: UUID,
    eventTypes: List<String>,
    from: Instant? = null,
    to: Instant? = null
  ): List<UserEventFirstOccurrence> {
    if (eventTypes.isEmpty()) return emptyList()
    val pgArray = eventTypes.distinct()
      .joinToString(",", "{", "}") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
    val sql = buildString {
      append(FIRST_OCCURRENCES_SQL.trim())
      if (from != null) append("\n      AND occurred_at >= CAST(? AS timestamptz)")
      if (to != null) append("\n      AND occurred_at <= CAST(? AS timestamptz)")
      append("\n    GROUP BY user_id, event_type")
    }
    val params = buildList {
      add(TextColumnType() to projectId.toString())
      add(TextColumnType() to pgArray)
      if (from != null) add(TextColumnType() to from.toString())
      if (to != null) add(TextColumnType() to to.toString())
    }
    return newSuspendedTransaction {
      exec(
        sql,
        params,
        explicitStatementType = StatementType.SELECT
      ) { rs ->
        val result = mutableListOf<UserEventFirstOccurrence>()
        while (rs.next()) {
          result.add(
            UserEventFirstOccurrence(
              userId = rs.getString("user_id"),
              eventType = rs.getString("event_type"),
              firstOccurredAt = rs.getTimestamp("first_at")!!.time.let {
                Instant.fromEpochMilliseconds(it)
              }
            )
          )
        }
        result
      } ?: emptyList()
    }
  }

    suspend fun findByProject(
        projectId: UUID,
        limit: Int,
        from: Instant? = null,
        to: Instant? = null
    ): List<EventRow> = newSuspendedTransaction {
        Events.selectAll()
            .where { Events.projectId eq projectId }
            .apply {
                if (from != null) andWhere { Events.occurredAt greaterEq from }
                if (to != null) andWhere { Events.occurredAt lessEq to }
            }
            .orderBy(Events.occurredAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toRow() }
    }

    private fun ResultRow.toRow() = EventRow(
        id = this[Events.id],
        projectId = this[Events.projectId].value,
        occurredAt = this[Events.occurredAt],
        receivedAt = this[Events.receivedAt],
        sessionId = this[Events.sessionId],
        deviceId = this[Events.deviceId],
        userId = this[Events.userId],
        eventType = this[Events.eventType],
        platform = this[Events.platform],
        appVersion = this[Events.appVersion],
        osVersion = this[Events.osVersion],
        country = this[Events.country],
        properties = this[Events.properties]
    )
}
