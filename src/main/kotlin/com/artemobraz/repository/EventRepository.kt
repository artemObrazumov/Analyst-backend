package com.artemobraz.repository

import com.artemobraz.model.EventRow
import com.artemobraz.model.Events
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
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
