package com.artemobraz.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateExperimentRequest(
  val name: String,
  val description: String? = null
)

@Serializable
data class UpdateExperimentRequest(
  val name: String? = null,
  val description: String? = null,
  val result: String? = null
)

@Serializable
data class UpdateExperimentStatusRequest(val status: String)

@Serializable
data class ExperimentResponse(
  val id: String,
  val projectId: String,
  val createdBy: String,
  val name: String,
  val description: String?,
  val status: String,
  val result: String?,
  val createdAt: String,
  val updatedAt: String
)

@Serializable
data class ExperimentDetailResponse(
  val id: String,
  val projectId: String,
  val createdBy: String,
  val name: String,
  val description: String?,
  val status: String,
  val result: String?,
  val createdAt: String,
  val updatedAt: String,
  val groups: List<ExperimentGroupResponse>,
  val events: List<ExperimentEventResponse>
)

@Serializable
data class ExperimentGroupResponse(
  val id: String,
  val propertyKey: String,
  val propertyValue: String,
  val label: String
)

@Serializable
data class ExperimentEventResponse(
  val id: String,
  val eventType: String,
  val note: String?
)

@Serializable
data class AddExperimentGroupRequest(
  val propertyKey: String,
  val propertyValue: String,
  val label: String
)

@Serializable
data class AddExperimentEventRequest(
  val eventType: String,
  val note: String? = null
)

@Serializable
data class ExperimentAnalysisResponse(
  val experimentId: String,
  val experimentName: String,
  val trackedEvents: List<String>,
  val groups: List<GroupAnalysisResponse>
)

@Serializable
data class GroupAnalysisResponse(
  val label: String,
  val propertyKey: String,
  val propertyValue: String,
  val exposed: Long,
  val converted: Long,
  val conversionRate: Double
)
