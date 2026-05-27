package com.artemobraz.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateFunnelRequest(
  val name: String,
  val description: String? = null
)

@Serializable
data class UpdateFunnelRequest(
  val name: String? = null,
  val description: String? = null
)

@Serializable
data class FunnelResponse(
  val id: String,
  val projectId: String,
  val createdBy: String,
  val name: String,
  val description: String?,
  val createdAt: String,
  val updatedAt: String
)

@Serializable
data class FunnelDetailResponse(
  val id: String,
  val projectId: String,
  val createdBy: String,
  val name: String,
  val description: String?,
  val createdAt: String,
  val updatedAt: String,
  val steps: List<FunnelStepResponse>
)

@Serializable
data class FunnelStepResponse(
  val id: String,
  val eventType: String,
  val label: String,
  val stepOrder: Int
)

@Serializable
data class AddFunnelStepRequest(
  val eventType: String,
  val label: String
)

@Serializable
data class ReorderFunnelStepsRequest(
  val stepIds: List<String>
)
