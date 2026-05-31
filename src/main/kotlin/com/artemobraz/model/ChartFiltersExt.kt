package com.artemobraz.model

import kotlinx.serialization.json.Json

private val chartFiltersJson = Json { ignoreUnknownKeys = true }

fun ChartFilters.toJsonString(): String = chartFiltersJson.encodeToString(this)

fun String.toChartFilters(): ChartFilters =
  if (isBlank() || this == "{}") ChartFilters()
  else chartFiltersJson.decodeFromString<ChartFilters>(this)

fun ChartFilters.isEmpty(): Boolean =
  platform == null &&
    country == null &&
    deviceId == null &&
    userId == null &&
    appVersion == null &&
    osVersion == null &&
    properties.isEmpty()
