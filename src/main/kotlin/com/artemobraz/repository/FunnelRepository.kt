package com.artemobraz.repository

import com.artemobraz.model.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class FunnelRepository {

  suspend fun findAllByProject(projectId: UUID): List<FunnelRow> = newSuspendedTransaction {
    Funnels.selectAll()
      .where { Funnels.projectId eq projectId }
      .orderBy(Funnels.createdAt, SortOrder.DESC)
      .map { it.toFunnelRow() }
  }

  suspend fun findById(id: UUID): FunnelRow? = newSuspendedTransaction {
    Funnels.selectAll()
      .where { Funnels.id eq id }
      .firstOrNull()
      ?.toFunnelRow()
  }

  suspend fun create(projectId: UUID, createdBy: UUID, name: String, description: String?): FunnelRow =
    newSuspendedTransaction {
      val insertedId = Funnels.insertAndGetId {
        it[Funnels.projectId] = projectId
        it[Funnels.createdBy] = createdBy
        it[Funnels.name] = name
        it[Funnels.description] = description
      }
      Funnels.selectAll().where { Funnels.id eq insertedId }.first().toFunnelRow()
    }

  suspend fun update(id: UUID, name: String?, description: String?): FunnelRow =
    newSuspendedTransaction {
      Funnels.update({ Funnels.id eq id }) {
        if (name != null) it[Funnels.name] = name
        if (description != null) it[Funnels.description] = description
        it[Funnels.updatedAt] = Clock.System.now()
      }
      Funnels.selectAll().where { Funnels.id eq id }.first().toFunnelRow()
    }

  suspend fun delete(id: UUID) = newSuspendedTransaction {
    Funnels.deleteWhere { Funnels.id eq id }
  }

  suspend fun findSteps(funnelId: UUID): List<FunnelStepRow> = newSuspendedTransaction {
    FunnelSteps.selectAll()
      .where { FunnelSteps.funnelId eq funnelId }
      .orderBy(FunnelSteps.stepOrder, SortOrder.ASC)
      .map { it.toStepRow() }
  }

  suspend fun findStepById(id: UUID): FunnelStepRow? = newSuspendedTransaction {
    FunnelSteps.selectAll()
      .where { FunnelSteps.id eq id }
      .firstOrNull()
      ?.toStepRow()
  }

  suspend fun addStep(
    funnelId: UUID,
    eventType: String,
    propertyFilters: Map<String, String>,
    stepOrder: Int
  ): FunnelStepRow =
    newSuspendedTransaction {
      val insertedId = FunnelSteps.insertAndGetId {
        it[FunnelSteps.funnelId] = funnelId
        it[FunnelSteps.eventType] = eventType
        it[FunnelSteps.propertyFilters] = propertyFilters.toPropertyFiltersJson()
        it[FunnelSteps.stepOrder] = stepOrder
      }
      FunnelSteps.selectAll().where { FunnelSteps.id eq insertedId }.first().toStepRow()
    }

  suspend fun deleteStep(id: UUID) = newSuspendedTransaction {
    FunnelSteps.deleteWhere { FunnelSteps.id eq id }
  }

  suspend fun reorderSteps(funnelId: UUID, stepIds: List<UUID>) = newSuspendedTransaction {
    val stepPredicate = { stepId: UUID ->
      (FunnelSteps.id eq stepId) and (FunnelSteps.funnelId eq funnelId)
    }
    stepIds.forEachIndexed { index, stepId ->
      FunnelSteps.update({ stepPredicate(stepId) }) {
        it[FunnelSteps.stepOrder] = -(index + 1)
      }
    }
    stepIds.forEachIndexed { index, stepId ->
      FunnelSteps.update({ stepPredicate(stepId) }) {
        it[FunnelSteps.stepOrder] = index + 1
      }
    }
  }

  suspend fun nextStepOrder(funnelId: UUID): Int = newSuspendedTransaction {
    FunnelSteps.selectAll()
      .where { FunnelSteps.funnelId eq funnelId }
      .maxOfOrNull { it[FunnelSteps.stepOrder] }
      ?.plus(1) ?: 1
  }

  private fun ResultRow.toFunnelRow() = FunnelRow(
    id = this[Funnels.id].value,
    projectId = this[Funnels.projectId].value,
    createdBy = this[Funnels.createdBy].value,
    name = this[Funnels.name],
    description = this[Funnels.description],
    createdAt = this[Funnels.createdAt],
    updatedAt = this[Funnels.updatedAt]
  )

  private fun ResultRow.toStepRow() = FunnelStepRow(
    id = this[FunnelSteps.id].value,
    funnelId = this[FunnelSteps.funnelId].value,
    eventType = this[FunnelSteps.eventType],
    propertyFilters = this[FunnelSteps.propertyFilters].toPropertyFilters(),
    stepOrder = this[FunnelSteps.stepOrder]
  )
}
