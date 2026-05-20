package com.artemobraz.plugins

import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun Application.configureMetrics() {
  install(MicrometerMetrics) {
    registry = prometheusRegistry
    distributionStatisticConfig = DistributionStatisticConfig.Builder()
      .percentilesHistogram(true)
      .percentiles(0.5, 0.95, 0.99)
      .build()
  }
}
