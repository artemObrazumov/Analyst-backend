package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.ProjectRepository
import java.security.MessageDigest
import java.util.*

class ProjectService(private val projectRepository: ProjectRepository) {

  suspend fun listProjects(userId: UUID): List<ProjectResponse> =
    projectRepository.findAllByOwner(userId).map { it.toResponse() }

  suspend fun getProject(userId: UUID, projectId: UUID): ProjectResponse {
    val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
    if (project.ownerId != userId) throw ForbiddenException("Access denied")
    return project.toResponse()
  }

  suspend fun createProject(userId: UUID, name: String, description: String?): ProjectWithKeyResponse {
    if (name.isBlank()) throw IllegalArgumentException("Project name is required")
    val project = projectRepository.create(userId, name, description)
    val rawKey = generateKey()
    val keyRow = projectRepository.createKey(project.id, sha256(rawKey), label = null)
    return ProjectWithKeyResponse(
      project = project.toResponse(),
      apiKey = ApiKeyCreatedResponse(
        id = keyRow.id.toString(),
        key = rawKey,
        label = keyRow.label,
        createdAt = keyRow.createdAt.toString()
      )
    )
  }

  suspend fun updateProject(userId: UUID, projectId: UUID, name: String?, description: String?): ProjectResponse {
    val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
    if (project.ownerId != userId) throw ForbiddenException("Access denied")
    if (name != null && name.isBlank()) throw IllegalArgumentException("Project name cannot be blank")
    return projectRepository.update(projectId, name, description).toResponse()
  }

  suspend fun deleteProject(userId: UUID, projectId: UUID) {
    val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
    if (project.ownerId != userId) throw ForbiddenException("Access denied")
    projectRepository.delete(projectId)
  }

  suspend fun getKeyMeta(userId: UUID, projectId: UUID): ApiKeyMetaResponse {
    val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
    if (project.ownerId != userId) throw ForbiddenException("Access denied")
    val key = projectRepository.getActiveKey(projectId) ?: throw NotFoundException("No active API key")
    return ApiKeyMetaResponse(
      id = key.id.toString(),
      label = key.label,
      isActive = key.isActive,
      createdAt = key.createdAt.toString()
    )
  }

  suspend fun rotateKey(userId: UUID, projectId: UUID): ApiKeyCreatedResponse {
    val project = projectRepository.findById(projectId) ?: throw NotFoundException("Project not found")
    if (project.ownerId != userId) throw ForbiddenException("Access denied")
    projectRepository.revokeActiveKey(projectId)
    val rawKey = generateKey()
    val keyRow = projectRepository.createKey(projectId, sha256(rawKey), label = null)
    return ApiKeyCreatedResponse(
      id = keyRow.id.toString(),
      key = rawKey,
      label = keyRow.label,
      createdAt = keyRow.createdAt.toString()
    )
  }

  private fun generateKey(): String {
    val bytes = ByteArray(24).also { java.security.SecureRandom().nextBytes(it) }
    return "proj_" + bytes.joinToString("") { "%02x".format(it) }
  }

  private fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun ProjectRow.toResponse() = ProjectResponse(
    id = id.toString(),
    name = name,
    description = description,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
  )
}
