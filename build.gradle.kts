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

// ── Flyway migrations ─────────────────────────────────────────────────────────

val dbUrl = project.findProperty("db.url") as? String ?: "jdbc:postgresql://localhost:5432/analytics"
val dbUser = project.findProperty("db.user") as? String ?: "analytics"
val dbPassword = project.findProperty("db.password") as? String ?: "secret"

tasks.register<JavaExec>("dbClean") {
  group = "database"
  description = "Drop all PostgreSQL data, re-migrate schema, flush Redis"
  dependsOn("classes", "dockerUp")
  classpath = sourceSets["main"].runtimeClasspath
  mainClass = "com.artemobraz.CleanDbKt"
  environment("DB_URL", dbUrl)
  environment("DB_USER", dbUser)
  environment("DB_PASSWORD", dbPassword)
  environment("REDIS_URL", project.findProperty("redis.url") as? String ?: "redis://localhost:6379")
}

tasks.register<JavaExec>("dbMigrate") {
  group = "database"
  description = "Run Flyway migrations against the local database"
  dependsOn("classes")
  classpath = sourceSets["main"].runtimeClasspath
  mainClass = "com.artemobraz.MigrateDbKt"
  environment("DB_URL", dbUrl)
  environment("DB_USER", dbUser)
  environment("DB_PASSWORD", dbPassword)
}

// ── Docker via Podman ─────────────────────────────────────────────────────────

val podmanMachine = project.findProperty("podmanMachine") as? String ?: "default"
val composeFile = "${rootProject.projectDir.parent}/docker-compose.yml"

fun findPodman(): String {
  val fromEnv = System.getenv("PODMAN_PATH")
  if (!fromEnv.isNullOrBlank()) return fromEnv
  val searchPaths = (System.getenv("PATH") ?: "").split(File.pathSeparator) +
    listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin")
  return searchPaths.map { File(it, "podman") }.firstOrNull { it.canExecute() }?.absolutePath
    ?: throw GradleException("podman not found. Set PODMAN_PATH env variable or add podman to PATH.")
}

val podmanBin: String by lazy { findPodman() }

fun podmanQuery(machine: String, format: String): String {
  val proc = ProcessBuilder(podmanBin, "machine", "inspect", machine, "--format", format).start()
  val output = proc.inputStream.bufferedReader().readText().trim()
  proc.waitFor()
  return output
}

fun podmanDockerHost(machine: String): String {
  val sock = podmanQuery(machine, "{{.ConnectionInfo.PodmanSocket.Path}}")
  return "unix://$sock"
}

tasks.register("dockerUp") {
  group = "docker"
  description = "Start dev services (Postgres, Redis) via Podman"
  doLast {
    val state = podmanQuery(podmanMachine, "{{.State}}")
    if (state != "running") {
      println("Podman machine '$podmanMachine' is '$state' — starting...")
      val code = ProcessBuilder(podmanBin, "machine", "start", podmanMachine)
        .inheritIO().start().waitFor()
      // 125 = already running (race condition), treat as success
      if (code != 0 && code != 125) throw GradleException("podman machine start failed (exit $code)")
    }

    val dockerHost = podmanDockerHost(podmanMachine)
    val code = ProcessBuilder(podmanBin, "compose", "-f", composeFile, "up", "-d", "--wait")
      .apply { environment()["DOCKER_HOST"] = dockerHost }
      .inheritIO().start().waitFor()
    if (code != 0) throw GradleException("podman compose up failed (exit $code)")
  }
}

tasks.register("dockerDown") {
  group = "docker"
  description = "Stop dev services"
  doLast {
    val dockerHost = podmanDockerHost(podmanMachine)
    ProcessBuilder(podmanBin, "compose", "-f", composeFile, "down")
      .apply { environment()["DOCKER_HOST"] = dockerHost }
      .inheritIO().start().waitFor()
  }
}

tasks.named("dbMigrate") { dependsOn("dockerUp") }
tasks.named("run") { dependsOn("dbMigrate") }
tasks.named("test") { dependsOn("dbMigrate") }

tasks.register<JavaExec>("seedCheckoutDemo") {
  group = "demo"
  description = "Seed checkout A/B demo events, experiment, funnel and dashboards"
  dependsOn("classes", "dbMigrate")
  classpath = sourceSets["main"].runtimeClasspath
  mainClass = "com.artemobraz.scripts.checkoutseed.CheckoutSeedRunnerKt"
}
