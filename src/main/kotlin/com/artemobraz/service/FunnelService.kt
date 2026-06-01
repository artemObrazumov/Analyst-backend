package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.EventRepository
import com.artemobraz.repository.FunnelRepository
import com.artemobraz.repository.ProjectRepository
import kotlinx.datetime.Instant
import java.util.*

class FunnelService(
  private val funnelRepository: FunnelRepository,
  private val projectRepository: ProjectRepository,
  private val eventRepository: EventRepository
) {

  private suspend fun assertProjectAccess(userId: UUID, projectId: UUID) {
    val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
    if (project.ownerId != userId) throw ForbiddenException("Access denied")
  }

  private suspend fun assertFunnelBelongsToProject(funnelId: UUID, projectId: UUID): FunnelRow {
    val funnel = funnelRepository.findById(funnelId) ?: throw NotFoundException("Funnel not found")
    if (funnel.projectId != projectId) throw NotFoundException("Funnel not found")
    return funnel
  }

  suspend fun listFunnels(userId: UUID, projectId: UUID): List<FunnelResponse> {
    assertProjectAccess(userId, projectId)
    return funnelRepository.findAllByProject(projectId).map { it.toResponse() }
  }

  suspend fun createFunnel(userId: UUID, projectId: UUID, name: String, description: String?): FunnelResponse {
    assertProjectAccess(userId, projectId)
    if (name.isBlank()) throw IllegalArgumentException("Funnel name is required")
    return funnelRepository.create(projectId, userId, name, description).toResponse()
  }

  suspend fun getFunnel(userId: UUID, projectId: UUID, id: UUID): FunnelDetailResponse {
    assertProjectAccess(userId, projectId)
    val funnel = assertFunnelBelongsToProject(id, projectId)
    val steps = funnelRepository.findSteps(id).map { it.toStepResponse() }
    return funnel.toDetailResponse(steps)
  }

  suspend fun updateFunnel(
    userId: UUID,
    projectId: UUID,
    id: UUID,
    name: String?,
    description: String?
  ): FunnelResponse {
    assertProjectAccess(userId, projectId)
    assertFunnelBelongsToProject(id, projectId)
    if (name != null && name.isBlank()) throw IllegalArgumentException("Funnel name cannot be blank")
    return funnelRepository.update(id, name, description).toResponse()
  }

  suspend fun deleteFunnel(userId: UUID, projectId: UUID, id: UUID) {
    assertProjectAccess(userId, projectId)
    assertFunnelBelongsToProject(id, projectId)
    funnelRepository.delete(id)
  }

  suspend fun addStep(
    userId: UUID,
    projectId: UUID,
    funnelId: UUID,
    eventType: String,
    propertyFilters: Map<String, String>
  ): FunnelStepResponse {
    assertProjectAccess(userId, projectId)
    assertFunnelBelongsToProject(funnelId, projectId)
    if (eventType.isBlank()) throw IllegalArgumentException("Event type is required")
    validatePropertyFilters(propertyFilters)
    val nextOrder = funnelRepository.nextStepOrder(funnelId)
    return funnelRepository.addStep(funnelId, eventType, propertyFilters, nextOrder).toStepResponse()
  }

  suspend fun removeStep(userId: UUID, projectId: UUID, funnelId: UUID, stepId: UUID) {
    assertProjectAccess(userId, projectId)
    assertFunnelBelongsToProject(funnelId, projectId)
    val step = funnelRepository.findStepById(stepId) ?: throw NotFoundException("Step not found")
    if (step.funnelId != funnelId) throw NotFoundException("Step not found")
    funnelRepository.deleteStep(stepId)
  }

  suspend fun reorderSteps(userId: UUID, projectId: UUID, funnelId: UUID, stepIds: List<String>) {
    assertProjectAccess(userId, projectId)
    assertFunnelBelongsToProject(funnelId, projectId)
    val uuids = stepIds.map { UUID.fromString(it) }
    val existing = funnelRepository.findSteps(funnelId).map { it.id }.toSet()
    if (uuids.toSet() != existing) throw IllegalArgumentException("stepIds must contain exactly all steps of the funnel")
    funnelRepository.reorderSteps(funnelId, uuids)
  }

  suspend fun analyzeFunnel(
    userId: UUID,
    projectId: UUID,
    funnelId: UUID,
    from: Instant?,
    to: Instant?
  ): FunnelAnalysisResponse {
    assertProjectAccess(userId, projectId)
    val funnel = assertFunnelBelongsToProject(funnelId, projectId)
    if (from != null && to != null && from > to) {
      throw IllegalArgumentException("'from' must be before or equal to 'to'")
    }

    val steps = funnelRepository.findSteps(funnelId)
    if (steps.isEmpty()) {
      return FunnelAnalysisResponse(
        funnelId = funnel.id.toString(),
        funnelName = funnel.name,
        period = FunnelAnalysisPeriod(from?.toString(), to?.toString()),
        steps = emptyList(),
        overallConversion = 0.0
      )
    }

    val stepCounts = LongArray(steps.size)
    val transitionSeconds = Array(steps.size - 1) { mutableListOf<Double>() }

    val occurrencesByStep = steps.map { step ->
      eventRepository.findFirstOccurrencesByUserAndType(
        projectId,
        step.eventType,
        step.propertyFilters,
        from,
        to
      ).associateBy { it.userId }
    }

    val allUserIds = occurrencesByStep.flatMap { it.keys }.toSet()
    for (userId in allUserIds) {
      var previousTime: Instant? = null
      for (i in steps.indices) {
        val stepTime = occurrencesByStep[i][userId]?.firstOccurredAt ?: break
        if (previousTime != null && stepTime <= previousTime) break
        stepCounts[i]++
        if (i > 0 && previousTime != null) {
          val deltaSeconds = (stepTime - previousTime).inWholeSeconds.toDouble()
          transitionSeconds[i - 1].add(deltaSeconds)
        }
        previousTime = stepTime
      }
    }

    val stepAnalyses = steps.mapIndexed { index, step ->
      val usersCount = stepCounts[index]
      val conversionFromPrevious = if (index == 0) {
        null
      } else {
        val previousCount = stepCounts[index - 1]
        if (previousCount == 0L) 0.0 else percent(usersCount, previousCount)
      }
      val dropOffFromPrevious = conversionFromPrevious?.let { roundPercent(100.0 - it) }
      val avgSecondsFromPrevious = if (index == 0) {
        null
      } else {
        val deltas = transitionSeconds[index - 1]
        if (deltas.isEmpty()) null else roundSeconds(deltas.average())
      }
      FunnelStepAnalysis(
        stepId = step.id.toString(),
        eventType = step.eventType,
        propertyFilters = step.propertyFilters,
        stepOrder = step.stepOrder,
        usersCount = usersCount,
        conversionFromPrevious = conversionFromPrevious,
        dropOffFromPrevious = dropOffFromPrevious,
        avgSecondsFromPrevious = avgSecondsFromPrevious
      )
    }

    val overallConversion = if (stepCounts.isEmpty() || stepCounts[0] == 0L) {
      0.0
    } else {
      percent(stepCounts.last(), stepCounts[0])
    }

    return FunnelAnalysisResponse(
      funnelId = funnel.id.toString(),
      funnelName = funnel.name,
      period = FunnelAnalysisPeriod(from?.toString(), to?.toString()),
      steps = stepAnalyses,
      overallConversion = overallConversion
    )
  }

  private fun validatePropertyFilters(propertyFilters: Map<String, String>) {
    propertyFilters.forEach { (key, value) ->
      if (key.isBlank()) throw IllegalArgumentException("Property filter key cannot be blank")
      if (value.isBlank()) throw IllegalArgumentException("Property filter value cannot be blank")
    }
  }

  private fun percent(part: Long, total: Long): Double =
    roundPercent(part.toDouble() / total * 100)

  private fun roundPercent(value: Double): Double =
    kotlin.math.round(value * 100).toDouble() / 100

  private fun roundSeconds(value: Double): Double =
    kotlin.math.round(value * 100).toDouble() / 100

  private fun FunnelRow.toResponse() = FunnelResponse(
    id = id.toString(),
    projectId = projectId.toString(),
    createdBy = createdBy.toString(),
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
  )

  private fun FunnelRow.toDetailResponse(steps: List<FunnelStepResponse>) = FunnelDetailResponse(
    id = id.toString(),
    projectId = projectId.toString(),
    createdBy = createdBy.toString(),
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    steps = steps
  )

  private fun FunnelStepRow.toStepResponse() = FunnelStepResponse(
    id = id.toString(),
    eventType = eventType,
    propertyFilters = propertyFilters,
    stepOrder = stepOrder
  )
}
