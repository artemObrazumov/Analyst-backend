package com.artemobraz.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Experiments : UUIDTable("experiments") {
  val projectId = reference("project_id", Projects)
  val createdBy = reference("created_by", Users)
  val name = varchar("name", 255)
  val description = text("description").nullable()
  val status = varchar("status", 20).default("draft")
  val result = text("result").nullable()
  val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
  val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

object ExperimentEvents : UUIDTable("experiment_events") {
  val experimentId = reference("experiment_id", Experiments)
  val eventType = text("event_type")
  val note = text("note").nullable()
}

object ExperimentGroups : UUIDTable("experiment_groups") {
  val experimentId = reference("experiment_id", Experiments)
  val propertyKey = text("property_key")
  val propertyValue = text("property_value")
  val label = varchar("label", 255)
}

data class ExperimentRow(
  val id: UUID,
  val projectId: UUID,
  val createdBy: UUID,
  val name: String,
  val description: String?,
  val status: String,
  val result: String?,
  val createdAt: Instant,
  val updatedAt: Instant
)

data class ExperimentEventRow(
  val id: UUID,
  val experimentId: UUID,
  val eventType: String,
  val note: String?
)

data class ExperimentGroupRow(
  val id: UUID,
  val experimentId: UUID,
  val propertyKey: String,
  val propertyValue: String,
  val label: String
)
