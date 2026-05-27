package com.artemobraz.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Funnels : UUIDTable("funnels") {
  val projectId = reference("project_id", Projects)
  val createdBy = reference("created_by", Users)
  val name = varchar("name", 255)
  val description = text("description").nullable()
  val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
  val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

object FunnelSteps : UUIDTable("funnel_steps") {
  val funnelId = reference("funnel_id", Funnels)
  val eventType = text("event_type")
  val label = varchar("label", 255)
  val stepOrder = integer("step_order")
}

data class FunnelRow(
  val id: UUID,
  val projectId: UUID,
  val createdBy: UUID,
  val name: String,
  val description: String?,
  val createdAt: Instant,
  val updatedAt: Instant
)

data class FunnelStepRow(
  val id: UUID,
  val funnelId: UUID,
  val eventType: String,
  val label: String,
  val stepOrder: Int
)
