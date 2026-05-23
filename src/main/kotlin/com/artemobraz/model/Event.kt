package com.artemobraz.model

import com.artemobraz.utils.jsonb
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Events : Table("events") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val projectId = reference("project_id", Projects)
    val occurredAt = timestamp("occurred_at").clientDefault { Clock.System.now() }
    val receivedAt = timestamp("received_at").clientDefault { Clock.System.now() }
    val sessionId = uuid("session_id").nullable()
    val deviceId = text("device_id").nullable()
    val userId = text("user_id").nullable()
    val eventType = text("event_type")
    val platform = text("platform").nullable()
    val appVersion = text("app_version").nullable()
    val osVersion = text("os_version").nullable()
    val country = char("country", 2).nullable()
    val properties = jsonb("properties").default("{}")

    override val primaryKey = PrimaryKey(id, occurredAt)
}

data class EventRow(
    val id: UUID,
    val projectId: UUID,
    val occurredAt: Instant,
    val receivedAt: Instant,
    val sessionId: UUID?,
    val deviceId: String?,
    val userId: String?,
    val eventType: String,
    val platform: String?,
    val appVersion: String?,
    val osVersion: String?,
    val country: String?,
    val properties: String
)
