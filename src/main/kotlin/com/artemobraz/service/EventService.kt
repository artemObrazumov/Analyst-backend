package com.artemobraz.service

import com.artemobraz.model.AuthenticationException
import com.artemobraz.model.EventDto
import com.artemobraz.model.IngestEventRequest
import com.artemobraz.repository.EventRepository
import com.artemobraz.repository.ProjectRepository
import com.artemobraz.utils.sha256
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.*

class EventService(
  private val eventRepository: EventRepository,
  private val projectRepository: ProjectRepository
) {
  suspend fun ingest(apiKey: String, request: IngestEventRequest): EventDto {
    val projectId = projectRepository.findProjectIdByKeyHash(sha256(apiKey))
      ?: throw AuthenticationException("Invalid API key")

    val occurredAt = request.occurredAt
      ?.let { Instant.parse(it) }
      ?: Clock.System.now()

    val row = eventRepository.create(
      projectId = projectId,
      eventType = request.eventType,
      occurredAt = occurredAt,
      sessionId = request.sessionId?.let { UUID.fromString(it) },
      deviceId = request.deviceId,
      userId = request.userId,
      platform = request.platform,
      appVersion = request.appVersion,
      osVersion = request.osVersion,
      country = request.country,
      properties = Json.encodeToString(JsonObject.serializer(), request.properties)
    )

    return row.toDto()
  }

  suspend fun list(
    projectId: UUID,
    limit: Int = 100,
    from: Instant? = null,
    to: Instant? = null
  ): List<EventDto> =
    eventRepository.findByProject(projectId, limit.coerceIn(1, 1000), from, to).map { it.toDto() }

  private fun com.artemobraz.model.EventRow.toDto() = EventDto(
    id = id.toString(),
    projectId = projectId.toString(),
    eventType = eventType,
    occurredAt = occurredAt.toString(),
    receivedAt = receivedAt.toString(),
    sessionId = sessionId?.toString(),
    deviceId = deviceId,
    userId = userId,
    platform = platform,
    appVersion = appVersion,
    osVersion = osVersion,
    country = country,
    properties = Json.decodeFromString(JsonObject.serializer(), properties)
  )
}
