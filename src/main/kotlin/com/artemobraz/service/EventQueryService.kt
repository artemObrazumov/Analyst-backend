package com.artemobraz.service

import com.artemobraz.model.AuthenticationException
import com.artemobraz.model.EventDto
import com.artemobraz.model.EventRow
import com.artemobraz.model.IngestEventRequest
import com.artemobraz.repository.EventRepository
import com.artemobraz.repository.EventsHourlyRepository
import com.artemobraz.repository.ProjectRepository
import com.artemobraz.utils.sha256
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.*

class EventQueryService(
  private val eventRepository: EventRepository,
  private val eventsHourlyRepository: EventsHourlyRepository,
  private val projectRepository: ProjectRepository
) {
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val channel = Channel<PendingEvent>(Channel.UNLIMITED)
  private val consumerJob: Job

  init {
    consumerJob = scope.launch {
      for (pending in channel) {
        runCatching {
          eventRepository.create(
            projectId = pending.projectId,
            eventType = pending.eventType,
            occurredAt = pending.occurredAt,
            sessionId = pending.sessionId,
            deviceId = pending.deviceId,
            userId = pending.userId,
            platform = pending.platform,
            appVersion = pending.appVersion,
            osVersion = pending.osVersion,
            country = pending.country,
            properties = pending.properties
          )
          eventsHourlyRepository.increment(pending.projectId, pending.eventType, pending.occurredAt)
        }
      }
    }
  }

  fun shutdown() {
    if (channel.isClosedForSend) return
    channel.close()
    runBlocking { consumerJob.join() }
    scope.cancel()
  }

  suspend fun ingestBatch(apiKey: String, requests: List<IngestEventRequest>): Int {
    if (requests.isEmpty()) return 0
    val projectId = projectRepository.findProjectIdByKeyHash(sha256(apiKey))
      ?: throw AuthenticationException("Invalid API key")
    for (request in requests) {
      if (request.eventType.isBlank()) throw IllegalArgumentException("eventType is required")
      channel.send(toPending(projectId, request))
    }
    return requests.size
  }

  suspend fun list(
    projectId: UUID,
    limit: Int = 100,
    from: Instant? = null,
    to: Instant? = null
  ): List<EventDto> =
    eventRepository.findByProject(projectId, limit.coerceIn(1, 1000), from, to).map { it.toDto() }

  private fun toPending(projectId: UUID, request: IngestEventRequest) = PendingEvent(
    projectId = projectId,
    eventType = request.eventType,
    occurredAt = request.occurredAt?.let { Instant.parse(it) } ?: Clock.System.now(),
    sessionId = request.sessionId?.let { UUID.fromString(it) },
    deviceId = request.deviceId,
    userId = request.userId,
    platform = request.platform,
    appVersion = request.appVersion,
    osVersion = request.osVersion,
    country = request.country,
    properties = Json.encodeToString(JsonObject.serializer(), request.properties)
  )

  private data class PendingEvent(
    val projectId: UUID,
    val eventType: String,
    val occurredAt: Instant,
    val sessionId: UUID?,
    val deviceId: String?,
    val userId: String?,
    val platform: String?,
    val appVersion: String?,
    val osVersion: String?,
    val country: String?,
    val properties: String
  )

  private fun EventRow.toDto() = EventDto(
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
