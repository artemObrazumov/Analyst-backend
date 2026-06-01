package com.artemobraz.repository

import com.artemobraz.model.EventRow
import com.artemobraz.model.Events
import com.artemobraz.model.SeriesFilters
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

data class UserEventFirstOccurrence(
  val userId: String,
  val eventType: String,
  val firstOccurredAt: Instant,
  val properties: String
)

data class EventCountByDay(
  val date: String,
  val count: Long
)

private const val FIRST_OCCURRENCES_SQL = """
    SELECT DISTINCT ON (user_id, event_type)
           user_id, event_type, occurred_at AS first_at, properties::text AS properties
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
    eventType: String,
    propertyFilters: Map<String, String> = emptyMap(),
    from: Instant? = null,
    to: Instant? = null
  ): List<UserEventFirstOccurrence> {
    val sql = buildString {
      append(
        """
        SELECT DISTINCT ON (user_id)
               user_id, event_type, occurred_at AS first_at, properties::text AS properties
        FROM events
        WHERE project_id = CAST(? AS uuid)
          AND user_id IS NOT NULL
          AND event_type = ?
        """.trimIndent()
      )
      if (propertyFilters.isNotEmpty()) append("\n  AND properties @> CAST(? AS jsonb)")
      if (from != null) append("\n  AND occurred_at >= CAST(? AS timestamptz)")
      if (to != null) append("\n  AND occurred_at <= CAST(? AS timestamptz)")
      append("\nORDER BY user_id, occurred_at ASC")
    }
    val propertiesJson = if (propertyFilters.isNotEmpty()) {
      Json.encodeToString(
        buildJsonObject { propertyFilters.forEach { (key, value) -> put(key, value) } }
      )
    } else null
    val params = buildList {
      add(TextColumnType() to projectId.toString())
      add(TextColumnType() to eventType)
      if (propertiesJson != null) add(TextColumnType() to propertiesJson)
      if (from != null) add(TextColumnType() to from.toString())
      if (to != null) add(TextColumnType() to to.toString())
    }
    return newSuspendedTransaction {
      exec(sql, params, explicitStatementType = StatementType.SELECT) { rs ->
        val result = mutableListOf<UserEventFirstOccurrence>()
        while (rs.next()) {
          result.add(
            UserEventFirstOccurrence(
              userId = rs.getString("user_id"),
              eventType = rs.getString("event_type"),
              firstOccurredAt = rs.getTimestamp("first_at")!!.time.let {
                Instant.fromEpochMilliseconds(it)
              },
              properties = rs.getString("properties")
            )
          )
        }
        result
      } ?: emptyList()
    }
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
      append("\n    ORDER BY user_id, event_type, occurred_at ASC")
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
              },
              properties = rs.getString("properties")
            )
          )
        }
        result
      } ?: emptyList()
    }
  }

  suspend fun countEventsByDay(
    projectId: UUID,
    eventType: String,
    filters: SeriesFilters = SeriesFilters(),
    from: Instant? = null,
    to: Instant? = null
  ): List<EventCountByDay> {
    if (filters.hasDimensionFilters()) {
      return countEventsByDayFromRawEvents(projectId, eventType, filters, from, to)
    }
    return countEventsByDayFromHourly(projectId, eventType, from, to)
  }

  private fun SeriesFilters.hasDimensionFilters(): Boolean =
    platform != null ||
      country != null ||
      osVersion != null ||
      appVersion != null ||
      propertyFilters.isNotEmpty()

  private suspend fun countEventsByDayFromHourly(
    projectId: UUID,
    eventType: String,
    from: Instant?,
    to: Instant?
  ): List<EventCountByDay> {
    val sql = buildString {
      append(
        """
        SELECT DATE_TRUNC('day', bucket_time AT TIME ZONE 'UTC')::date AS bucket, SUM(count) AS cnt
        FROM events_hourly
        WHERE project_id = CAST(? AS uuid)
          AND event_type = ?
        """.trimIndent()
      )
      if (from != null) append("\n  AND bucket_time >= CAST(? AS timestamptz)")
      if (to != null) append("\n  AND bucket_time <= CAST(? AS timestamptz)")
      append("\nGROUP BY bucket\nORDER BY bucket")
    }
    val params = buildList {
      add(TextColumnType() to projectId.toString())
      add(TextColumnType() to eventType)
      if (from != null) add(TextColumnType() to from.toString())
      if (to != null) add(TextColumnType() to to.toString())
    }
    return execCountByDay(sql, params)
  }

  private suspend fun countEventsByDayFromRawEvents(
    projectId: UUID,
    eventType: String,
    filters: SeriesFilters,
    from: Instant?,
    to: Instant?
  ): List<EventCountByDay> {
    val sql = buildString {
      append(
        """
        SELECT DATE_TRUNC('day', occurred_at AT TIME ZONE 'UTC')::date AS bucket, COUNT(*) AS cnt
        FROM events
        WHERE project_id = CAST(? AS uuid)
          AND event_type = ?
        """.trimIndent()
      )
      if (filters.platform != null) append("\n  AND platform = ?")
      if (filters.country != null) append("\n  AND country = ?")
      if (filters.appVersion != null) append("\n  AND app_version = ?")
      if (filters.osVersion != null) append("\n  AND os_version = ?")
      if (filters.propertyFilters.isNotEmpty()) append("\n  AND properties @> CAST(? AS jsonb)")
      if (from != null) append("\n  AND occurred_at >= CAST(? AS timestamptz)")
      if (to != null) append("\n  AND occurred_at <= CAST(? AS timestamptz)")
      append("\nGROUP BY bucket\nORDER BY bucket")
    }
    val propertiesJson = if (filters.propertyFilters.isNotEmpty()) {
      Json.encodeToString(
        buildJsonObject { filters.propertyFilters.forEach { (key, value) -> put(key, value) } }
      )
    } else null
    val params = buildList {
      add(TextColumnType() to projectId.toString())
      add(TextColumnType() to eventType)
      if (filters.platform != null) add(TextColumnType() to filters.platform)
      if (filters.country != null) add(TextColumnType() to filters.country)
      if (filters.appVersion != null) add(TextColumnType() to filters.appVersion)
      if (filters.osVersion != null) add(TextColumnType() to filters.osVersion)
      if (propertiesJson != null) add(TextColumnType() to propertiesJson)
      if (from != null) add(TextColumnType() to from.toString())
      if (to != null) add(TextColumnType() to to.toString())
    }
    return execCountByDay(sql, params)
  }

  private suspend fun execCountByDay(sql: String, params: List<Pair<TextColumnType, String>>): List<EventCountByDay> =
    newSuspendedTransaction {
      exec(sql, params, explicitStatementType = StatementType.SELECT) { rs ->
        val result = mutableListOf<EventCountByDay>()
        while (rs.next()) {
          result.add(
            EventCountByDay(
              date = rs.getDate("bucket").toLocalDate().toString(),
              count = rs.getLong("cnt")
            )
          )
        }
        result
      } ?: emptyList()
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
