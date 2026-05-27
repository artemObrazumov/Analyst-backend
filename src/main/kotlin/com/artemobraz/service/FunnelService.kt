package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.FunnelRepository
import com.artemobraz.repository.ProjectRepository
import java.util.*

class FunnelService(
  private val funnelRepository: FunnelRepository,
  private val projectRepository: ProjectRepository
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
    label: String
  ): FunnelStepResponse {
    assertProjectAccess(userId, projectId)
    assertFunnelBelongsToProject(funnelId, projectId)
    if (eventType.isBlank()) throw IllegalArgumentException("Event type is required")
    if (label.isBlank()) throw IllegalArgumentException("Step label is required")
    val nextOrder = funnelRepository.nextStepOrder(funnelId)
    return funnelRepository.addStep(funnelId, eventType, label, nextOrder).toStepResponse()
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
    label = label,
    stepOrder = stepOrder
  )
}
