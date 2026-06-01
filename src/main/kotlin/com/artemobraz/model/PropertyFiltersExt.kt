package com.artemobraz.model

import kotlinx.serialization.json.Json

private val propertyFiltersJson = Json { ignoreUnknownKeys = true }

fun Map<String, String>.toPropertyFiltersJson(): String = propertyFiltersJson.encodeToString(this)

fun String.toPropertyFilters(): Map<String, String> =
  if (isBlank() || this == "{}") emptyMap()
  else propertyFiltersJson.decodeFromString(this)

fun Map<String, String>.matchesEventProperties(propertiesJson: String): Boolean {
  if (isEmpty()) return true
  val json = propertyFiltersJson.parseToJsonElement(propertiesJson)
  if (json !is kotlinx.serialization.json.JsonObject) return false
  return all { (key, value) ->
    json[key]?.let { element ->
      element is kotlinx.serialization.json.JsonPrimitive && element.content == value
    } ?: false
  }
}
