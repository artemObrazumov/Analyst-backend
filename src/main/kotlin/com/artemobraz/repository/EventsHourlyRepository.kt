package com.artemobraz.repository

import com.artemobraz.model.EventsHourly
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class EventsHourlyRepository {

  suspend fun increment(
    projectId: UUID,
    eventType: String,
    occurredAt: Instant
  ) = newSuspendedTransaction {
    val bucketTime = truncateToHour(occurredAt)
    val updated = EventsHourly.update({
      (EventsHourly.bucketTime eq bucketTime) and
        (EventsHourly.projectId eq projectId) and
        (EventsHourly.eventType eq eventType)
    }) {
      it[EventsHourly.count] = EventsHourly.count + 1L
    }
    if (updated == 0) {
      EventsHourly.insert {
        it[EventsHourly.bucketTime] = bucketTime
        it[EventsHourly.projectId] = projectId
        it[EventsHourly.eventType] = eventType
        it[EventsHourly.count] = 1L
      }
    }
  }

  private fun truncateToHour(instant: Instant): Instant {
    val epochSeconds = instant.epochSeconds
    val truncated = epochSeconds - (epochSeconds % 3600)
    return Instant.fromEpochSeconds(truncated)
  }
}
