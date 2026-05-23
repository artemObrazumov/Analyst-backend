package com.artemobraz.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class IngestEventRequest(
  val eventType: String,
  val occurredAt: String? = null,
  val sessionId: String? = null,
  val deviceId: String? = null,
  val userId: String? = null,
  val platform: String? = null,
  val appVersion: String? = null,
  val osVersion: String? = null,
  val country: String? = null,
  val properties: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class EventDto(
  val id: String,
  val projectId: String,
  val eventType: String,
  val occurredAt: String,
  val receivedAt: String,
  val sessionId: String?,
  val deviceId: String?,
  val userId: String?,
  val platform: String?,
  val appVersion: String?,
  val osVersion: String?,
  val country: String?,
  val properties: JsonObject
)
