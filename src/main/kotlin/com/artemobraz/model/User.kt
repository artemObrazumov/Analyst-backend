package com.artemobraz.model

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.*

object Users : UUIDTable("users") {
  val email = text("email").uniqueIndex()
  val name = text("name")
  val passwordHash = text("password_hash")
  val role = text("role").default("admin")
  val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
  val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

data class UserRow(
  val id: UUID,
  val email: String,
  val name: String,
  val passwordHash: String,
  val role: String
)
