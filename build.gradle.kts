plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(ktorLibs.plugins.ktor)
}

group = "com.artemobraz"
version = "1.0.0-SNAPSHOT"

application {
  mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  // Ktor server
  implementation(ktorLibs.server.core)
  implementation(ktorLibs.server.netty)
  implementation(ktorLibs.server.config.yaml)
  implementation(ktorLibs.server.contentNegotiation)
  implementation(ktorLibs.serialization.kotlinx.json)
  implementation(ktorLibs.server.auth)
  implementation(ktorLibs.server.auth.jwt)
  implementation(ktorLibs.server.auth.apiKey)
  implementation(ktorLibs.server.cors)
  implementation(ktorLibs.server.statusPages)
  implementation(ktorLibs.server.callLogging)
  implementation(ktorLibs.server.metrics.micrometer)
  implementation(ktorLibs.server.openapi)
  implementation(ktorLibs.server.swagger)
  implementation(ktorLibs.server.requestValidation)
  implementation(ktorLibs.server.rateLimit)
  implementation(ktorLibs.server.sse)

  // Logging
  implementation(libs.logback.classic)

  // Database
  implementation(libs.exposed.core)
  implementation(libs.exposed.dao)
  implementation(libs.exposed.jdbc)
  implementation(libs.exposed.kotlin.datetime)
  implementation(libs.postgresql)
  implementation(libs.flyway.core)
  implementation(libs.flyway.postgresql)
  implementation(libs.hikaricp)

  // Redis
  implementation(libs.lettuce.core)

  // Metrics
  implementation(libs.micrometer.prometheus)

  // Security
  implementation(libs.bcrypt)

  // Coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.jdk8)

  // Tests
  testImplementation(kotlin("test"))
  testImplementation(ktorLibs.server.testHost)
}
