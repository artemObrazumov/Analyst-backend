package com.artemobraz.repository

import com.artemobraz.model.EventRow
import com.artemobraz.model.Events
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

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
    val id = Events.insertAndGetId {
      it[Events.projectId] = projectId
      it[Events.eventType] = eventType
      it[Events.occurredAt] = occurredAt
      it[Events.sessionId] = sessionId
      it[Events.deviceId] = deviceId
      it[Events.userId] = userId
      it[Events.platform] = platform
      it[Events.appVersion] = appVersion
      it[Events.osVersion] = osVersion
      it[Events.country] = country
      it[Events.properties] = properties
    }
    Events.selectAll().where { Events.id eq id }.first().toRow()
  }

  suspend fun findByProject(projectId: UUID, limit: Int): List<EventRow> = newSuspendedTransaction {
    Events.selectAll()
      .where { Events.projectId eq projectId }
      .orderBy(Events.receivedAt, SortOrder.DESC)
      .limit(limit)
      .map { it.toRow() }
  }

  private fun ResultRow.toRow() = EventRow(
    id = this[Events.id].value,
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
