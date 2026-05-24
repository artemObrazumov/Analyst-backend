package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.ExperimentRepository
import com.artemobraz.repository.ProjectRepository
import java.util.*

class ExperimentService(
  private val experimentRepository: ExperimentRepository,
  private val projectRepository: ProjectRepository
) {

  private suspend fun assertProjectAccess(userId: UUID, projectId: UUID) {
    val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
    if (project.ownerId != userId) throw ForbiddenException("Access denied")
  }

  private suspend fun assertExperimentBelongsToProject(experimentId: UUID, projectId: UUID): ExperimentRow {
    val experiment = experimentRepository.findById(experimentId) ?: throw NotFoundException("Experiment not found")
    if (experiment.projectId != projectId) throw NotFoundException("Experiment not found")
    return experiment
  }

  suspend fun listExperiments(userId: UUID, projectId: UUID): List<ExperimentResponse> {
    assertProjectAccess(userId, projectId)
    return experimentRepository.findAllByProject(projectId).map { it.toResponse() }
  }

  suspend fun createExperiment(userId: UUID, projectId: UUID, name: String, description: String?): ExperimentResponse {
    assertProjectAccess(userId, projectId)
    if (name.isBlank()) throw IllegalArgumentException("Experiment name is required")
    return experimentRepository.create(projectId, userId, name, description).toResponse()
  }

  suspend fun getExperiment(userId: UUID, projectId: UUID, id: UUID): ExperimentDetailResponse {
    assertProjectAccess(userId, projectId)
    val experiment = assertExperimentBelongsToProject(id, projectId)
    val groups = experimentRepository.findGroups(id).map { it.toGroupResponse() }
    val events = experimentRepository.findExperimentEvents(id).map { it.toEventResponse() }
    return experiment.toDetailResponse(groups, events)
  }

  suspend fun updateExperiment(
    userId: UUID,
    projectId: UUID,
    id: UUID,
    name: String?,
    description: String?,
    result: String?
  ): ExperimentResponse {
    assertProjectAccess(userId, projectId)
    assertExperimentBelongsToProject(id, projectId)
    if (name != null && name.isBlank()) throw IllegalArgumentException("Experiment name cannot be blank")
    return experimentRepository.update(id, name, description, result).toResponse()
  }

  suspend fun updateStatus(userId: UUID, projectId: UUID, id: UUID, status: String): ExperimentResponse {
    assertProjectAccess(userId, projectId)
    assertExperimentBelongsToProject(id, projectId)
    val validStatuses = setOf("draft", "active", "archived")
    if (status !in validStatuses) throw IllegalArgumentException("Status must be one of: ${validStatuses.joinToString()}")
    return experimentRepository.updateStatus(id, status).toResponse()
  }

  suspend fun deleteExperiment(userId: UUID, projectId: UUID, id: UUID) {
    assertProjectAccess(userId, projectId)
    assertExperimentBelongsToProject(id, projectId)
    experimentRepository.delete(id)
  }

  suspend fun addGroup(
    userId: UUID,
    projectId: UUID,
    experimentId: UUID,
    propertyKey: String,
    propertyValue: String,
    label: String
  ): ExperimentGroupResponse {
    assertProjectAccess(userId, projectId)
    assertExperimentBelongsToProject(experimentId, projectId)
    return experimentRepository.addGroup(experimentId, propertyKey, propertyValue, label).toGroupResponse()
  }

  suspend fun removeGroup(userId: UUID, projectId: UUID, experimentId: UUID, groupId: UUID) {
    assertProjectAccess(userId, projectId)
    assertExperimentBelongsToProject(experimentId, projectId)
    val group = experimentRepository.findGroupById(groupId) ?: throw NotFoundException("Group not found")
    if (group.experimentId != experimentId) throw NotFoundException("Group not found")
    experimentRepository.deleteGroup(groupId)
  }

  suspend fun addEvent(
    userId: UUID,
    projectId: UUID,
    experimentId: UUID,
    eventType: String,
    note: String?
  ): ExperimentEventResponse {
    assertProjectAccess(userId, projectId)
    assertExperimentBelongsToProject(experimentId, projectId)
    return experimentRepository.addExperimentEvent(experimentId, eventType, note).toEventResponse()
  }

  suspend fun analyzeExperiment(userId: UUID, projectId: UUID, id: UUID): ExperimentAnalysisResponse {
    assertProjectAccess(userId, projectId)
    val experiment = assertExperimentBelongsToProject(id, projectId)
    val groups = experimentRepository.findGroups(id)
    val eventTypes = experimentRepository.findExperimentEvents(id).map { it.eventType }
    val groupResults = groups.map { group ->
      val (exposed, converted) = experimentRepository.analyzeGroup(
        projectId, eventTypes, group.propertyKey, group.propertyValue
      )
      GroupAnalysisResponse(
        label = group.label,
        propertyKey = group.propertyKey,
        propertyValue = group.propertyValue,
        exposed = exposed,
        converted = converted,
        conversionRate = if (exposed == 0L) 0.0
        else Math.round(converted.toDouble() / exposed * 100 * 100).toDouble() / 100
      )
    }
    return ExperimentAnalysisResponse(
      experimentId = experiment.id.toString(),
      experimentName = experiment.name,
      trackedEvents = eventTypes,
      groups = groupResults
    )
  }

  suspend fun removeEvent(userId: UUID, projectId: UUID, experimentId: UUID, eventId: UUID) {
    assertProjectAccess(userId, projectId)
    assertExperimentBelongsToProject(experimentId, projectId)
    val event =
      experimentRepository.findExperimentEventById(eventId) ?: throw NotFoundException("Experiment event not found")
    if (event.experimentId != experimentId) throw NotFoundException("Experiment event not found")
    experimentRepository.deleteExperimentEvent(eventId)
  }

  private fun ExperimentRow.toResponse() = ExperimentResponse(
    id = id.toString(),
    projectId = projectId.toString(),
    createdBy = createdBy.toString(),
    name = name,
    description = description,
    status = status,
    result = result,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
  )

  private fun ExperimentRow.toDetailResponse(
    groups: List<ExperimentGroupResponse>,
    events: List<ExperimentEventResponse>
  ) = ExperimentDetailResponse(
    id = id.toString(),
    projectId = projectId.toString(),
    createdBy = createdBy.toString(),
    name = name,
    description = description,
    status = status,
    result = result,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    groups = groups,
    events = events
  )

  private fun ExperimentGroupRow.toGroupResponse() = ExperimentGroupResponse(
    id = id.toString(),
    propertyKey = propertyKey,
    propertyValue = propertyValue,
    label = label
  )

  private fun ExperimentEventRow.toEventResponse() = ExperimentEventResponse(
    id = id.toString(),
    eventType = eventType,
    note = note
  )
}
