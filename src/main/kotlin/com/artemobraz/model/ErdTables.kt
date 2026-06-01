package com.artemobraz.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object EventsHourly : Table("events_hourly") {
  val bucketTime = timestamp("bucket_time")
  val projectId = reference("project_id", Projects)
  val eventType = text("event_type")
  val count = long("count")

  override val primaryKey = PrimaryKey(bucketTime, projectId, eventType)
}

object RevokedRefreshTokens : Table("revoked_refresh_tokens") {
  val tokenHash = varchar("token_hash", 64)
  val userId = reference("user_id", Users)
  val revokedAt = timestamp("revoked_at")
  val expiresAt = timestamp("expires_at")

  override val primaryKey = PrimaryKey(tokenHash)
}
