package com.artemobraz.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Projects : UUIDTable("projects") {
  val ownerId = reference("owner_id", Users)
  val name = varchar("name", 255)
  val description = text("description").nullable()
  val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
  val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

object ApiKeys : UUIDTable("api_keys") {
  val projectId = reference("project_id", Projects)
  val keyHash = varchar("key_hash", 64)
  val label = varchar("label", 255).nullable()
  val isActive = bool("is_active").default(true)
  val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
  val revokedAt = timestamp("revoked_at").nullable()
}

data class ProjectRow(
  val id: UUID,
  val ownerId: UUID,
  val name: String,
  val description: String?,
  val createdAt: Instant,
  val updatedAt: Instant
)

data class ApiKeyRow(
  val id: UUID,
  val projectId: UUID,
  val keyHash: String,
  val label: String?,
  val isActive: Boolean,
  val createdAt: Instant,
  val revokedAt: Instant?
)
