package com.artemobraz.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateProjectRequest(val name: String, val description: String? = null)

@Serializable
data class UpdateProjectRequest(val name: String? = null, val description: String? = null)

@Serializable
data class ProjectResponse(
  val id: String,
  val name: String,
  val description: String?,
  val createdAt: String,
  val updatedAt: String
)

@Serializable
data class ProjectWithKeyResponse(
  val project: ProjectResponse,
  val apiKey: ApiKeyCreatedResponse
)

@Serializable
data class ApiKeyMetaResponse(
  val id: String,
  val label: String?,
  val isActive: Boolean,
  val createdAt: String
)

@Serializable
data class ApiKeyCreatedResponse(
  val id: String,
  val key: String,
  val label: String?,
  val createdAt: String
)
