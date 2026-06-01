package com.artemobraz.scripts.checkoutseed

import com.artemobraz.scripts.checkoutseed.CheckoutSeedRunner.email
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random

/**
 * Generates ~20k checkout demo events for the past week, plus experiment / funnel / dashboards.
 *
 * Run: ./gradlew seedCheckoutDemo
 *
 * Defaults: account [CheckoutSeedConfig.EMAIL], project [CheckoutSeedConfig.PROJECT_NAME].
 *
 * Env overrides:
 *   ANALYTICS_BASE_URL   — default http://localhost:8080
 *   ANALYTICS_EMAIL      — override seed email
 *   ANALYTICS_PASSWORD   — override seed password
 *   ANALYTICS_PROJECT_ID — force project id (rotates API key)
 *   ANALYTICS_API_KEY    — use with PROJECT_ID instead of rotate
 *   SEED_RANDOM          — RNG seed
 *   SEED_SERVER_WAIT_SEC — seconds to wait for /health (default 120)
 */
fun main() = runBlocking {
  CheckoutSeedRunner.run()
}

private data class DemoEvent(
  val eventType: String,
  val userId: String,
  val occurredAt: Instant,
  val platform: String,
  val country: String,
  val properties: JsonObject,
)

object CheckoutSeedRunner {
  private val json = Json { ignoreUnknownKeys = true }
  private val http = HttpClient.newBuilder().build()
  private val rng = Random(System.getenv("SEED_RANDOM")?.toLongOrNull() ?: System.currentTimeMillis())

  private val baseUrl = env("ANALYTICS_BASE_URL", "http://localhost:8080").trimEnd('/')
  private val email = env("ANALYTICS_EMAIL", CheckoutSeedConfig.EMAIL)
  private val password = env("ANALYTICS_PASSWORD", CheckoutSeedConfig.PASSWORD)

  suspend fun run() {
    waitForServer()

    val token = ensureAccount()
    val (projectId, apiKey) = ensureProject(token)

    println("Account : $email")
    println("Project : ${CheckoutSeedConfig.PROJECT_NAME} ($projectId)")
    println("Base URL: $baseUrl")

    setupExperiment(token, projectId)
    setupFunnel(token, projectId)
    setupDashboards(token, projectId)

    val events = generateEvents()
    println("Generated ${events.size} events, ingesting…")

    ingestEvents(apiKey, events)

    val weekStart = weekStartInstant()
    val weekEnd = Instant.now()
    println(
      """
      |
      |Done.
      |  Events ingested : ${events.size}
      |  Period          : $weekStart → $weekEnd (UTC)
      |  App version     : ${CheckoutSeedConfig.APP_VERSION}
      |  Account         : $email
      |  Project         : ${CheckoutSeedConfig.PROJECT_NAME} ($projectId)
      |
      |Open admin UI / analysis:
      |  GET $baseUrl/api/projects/$projectId/experiments
      |  GET $baseUrl/api/projects/$projectId/funnels
      |  GET $baseUrl/api/projects/$projectId/dashboards
      |
      """.trimMargin()
    )
  }

  private fun env(name: String, default: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

  private suspend fun waitForServer() {
    val waitSec = System.getenv("SEED_SERVER_WAIT_SEC")?.toLongOrNull() ?: 120L
    val deadline = System.currentTimeMillis() + waitSec * 1000
    var attempt = 0
    while (System.currentTimeMillis() < deadline) {
      try {
        if (get("/health").statusCode() == 200) {
          if (attempt > 0) println("Server is up at $baseUrl")
          return
        }
      } catch (e: Exception) {
        if (unwrapConnect(e) == null) throw e
      }
      if (attempt == 0) {
        println("Waiting for backend at $baseUrl …")
        println("  Start in another terminal: ./gradlew run")
      }
      attempt++
      delay(2_000)
    }
    throwIllegalStateCannotConnect(waitSec)
  }

  private fun throwIllegalStateCannotConnect(waitSec: Long): Nothing {
    if (waitSec == 0L) {
      error(
        """
        Cannot connect to $baseUrl.

        Make sure the server is running: ./gradlew run
        Or set ANALYTICS_BASE_URL to the correct API URL.
        """.trimIndent()
      )
    }
    error(
      """
      Cannot connect to $baseUrl after ${waitSec}s.

      1. Start Postgres/Redis and the API server:
           ./gradlew run

      2. In another terminal, run the seed:
           ./gradlew seedCheckoutDemo

      If the server uses another host/port, set ANALYTICS_BASE_URL.
      """.trimIndent()
    )
  }

  private fun unwrapConnect(e: Throwable): ConnectException? {
    var current: Throwable? = e
    while (current != null) {
      if (current is ConnectException) return current
      current = current.cause
    }
    return null
  }

  /** Login; register [email] if missing. */
  private fun ensureAccount(): String {
    login()?.let { return it }
    register()?.let { return it }
    login()?.let { return it }
    error("Could not authenticate as $email. Is the server running at $baseUrl?")
  }

  private fun register(): String? {
    val body =
      """{"name":"${CheckoutSeedConfig.USER_NAME}","email":"$email","password":"$password"}"""
    val response = post("/api/auth/register", body)
    if (response.statusCode() !in 200..299) return null
    println("Registered account: $email")
    return json.parseToJsonElement(response.body()).jsonObject["accessToken"]!!.jsonPrimitive.content
  }

  private fun login(): String? {
    val body = """{"email":"$email","password":"$password"}"""
    val response = post("/api/auth/login", body)
    if (response.statusCode() !in 200..299) return null
    return json.parseToJsonElement(response.body()).jsonObject["accessToken"]!!.jsonPrimitive.content
  }

  /** Find [CheckoutSeedConfig.PROJECT_NAME] or create it; return project id and ingest API key. */
  private fun ensureProject(token: String): Pair<String, String> {
    System.getenv("ANALYTICS_PROJECT_ID")?.takeIf { it.isNotBlank() }?.let { projectId ->
      val apiKey = System.getenv("ANALYTICS_API_KEY")?.takeIf { it.isNotBlank() }
        ?: rotateKey(token, projectId)
      return projectId to apiKey
    }

    val existingId = findProjectByName(token, CheckoutSeedConfig.PROJECT_NAME)
    if (existingId != null) {
      println("Using existing project: ${CheckoutSeedConfig.PROJECT_NAME}")
      return existingId to rotateKey(token, existingId)
    }

    val body =
      """{"name":"${CheckoutSeedConfig.PROJECT_NAME}","description":"${CheckoutSeedConfig.PROJECT_DESCRIPTION}"}"""
    val response = post("/api/projects", body, bearer = token)
    require(response.statusCode() == 201) {
      "Create project failed: ${response.statusCode()} ${response.body()}"
    }
    val root = json.parseToJsonElement(response.body()).jsonObject
    val projectId = root["project"]!!.jsonObject["id"]!!.jsonPrimitive.content
    val apiKey = root["apiKey"]!!.jsonObject["key"]!!.jsonPrimitive.content
    println("Created project: ${CheckoutSeedConfig.PROJECT_NAME}")
    return projectId to apiKey
  }

  private fun findProjectByName(token: String, name: String): String? =
    findResourceIdByName(token, "/api/projects", name)

  private fun rotateKey(token: String, projectId: String): String {
    val response = post("/api/projects/$projectId/key/rotate", "{}", bearer = token)
    require(response.statusCode() in 200..299) {
      "Rotate key failed: ${response.statusCode()} ${response.body()}"
    }
    return json.parseToJsonElement(response.body()).jsonObject["key"]!!.jsonPrimitive.content
  }

  private fun findResourceIdByName(token: String, listPath: String, name: String): String? {
    val response = get(listPath, bearer = token)
    require(response.statusCode() == 200) {
      "List $listPath failed: ${response.statusCode()} ${response.body()}"
    }
    return json.parseToJsonElement(response.body()).jsonArray
      .firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == name }
      ?.jsonObject?.get("id")?.jsonPrimitive?.content
  }

  private fun jsonEscape(value: String): String =
    buildString(value.length + 8) {
      append('"')
      value.forEach { ch ->
        when (ch) {
          '\\' -> append("\\\\")
          '"' -> append("\\\"")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else -> append(ch)
        }
      }
      append('"')
    }

  private fun setupExperiment(token: String, projectId: String) {
    val listPath = "/api/projects/$projectId/experiments"
    findResourceIdByName(token, listPath, CheckoutSeedConfig.EXPERIMENT_NAME)?.let { id ->
      println("Эксперимент уже существует: ${CheckoutSeedConfig.EXPERIMENT_NAME} ($id)")
      return
    }

    val create = post(
      listPath,
      """
      {
        "name": ${jsonEscape(CheckoutSeedConfig.EXPERIMENT_NAME)},
        "description": ${jsonEscape(CheckoutSeedConfig.EXPERIMENT_DESCRIPTION)}
      }
      """.trimIndent(),
      bearer = token
    )
    require(create.statusCode() == 201) { "Create experiment failed: ${create.body()}" }
    val experimentId = json.parseToJsonElement(create.body()).jsonObject["id"]!!.jsonPrimitive.content

    listOf(
      Triple("checkout_layout", "scroll", CheckoutSeedConfig.GROUP_SCROLL_LABEL),
      Triple("checkout_layout", "steps", CheckoutSeedConfig.GROUP_STEPS_LABEL),
    ).forEach { (key, value, label) ->
      val body = """
        {
          "propertyKey": "$key",
          "propertyValue": "$value",
          "label": ${jsonEscape(label)}
        }
      """.trimIndent()
      val r = post("/api/projects/$projectId/experiments/$experimentId/groups", body, bearer = token)
      require(r.statusCode() == 201) { "Add group failed: ${r.body()}" }
    }

    listOf(
      CheckoutEventTypes.STARTED to CheckoutSeedConfig.EXPERIMENT_NOTE_STARTED,
      CheckoutEventTypes.COMPLETED to CheckoutSeedConfig.EXPERIMENT_NOTE_COMPLETED,
    ).forEach { (eventType, note) ->
      val body = """
        {
          "eventType": "$eventType",
          "note": ${jsonEscape(note)}
        }
      """.trimIndent()
      val r = post("/api/projects/$projectId/experiments/$experimentId/events", body, bearer = token)
      require(r.statusCode() == 201) { "Add experiment event failed: ${r.body()}" }
    }

    val status = put(
      "/api/projects/$projectId/experiments/$experimentId/status",
      """{"status":"active"}""",
      bearer = token
    )
    require(status.statusCode() == 200) { "Activate experiment failed: ${status.body()}" }
    println("Создан эксперимент: ${CheckoutSeedConfig.EXPERIMENT_NAME} ($experimentId)")
  }

  private fun setupFunnel(token: String, projectId: String) {
    val listPath = "/api/projects/$projectId/funnels"
    findResourceIdByName(token, listPath, CheckoutSeedConfig.FUNNEL_NAME)?.let { id ->
      println("Воронка уже существует: ${CheckoutSeedConfig.FUNNEL_NAME} ($id)")
      return
    }

    val create = post(
      listPath,
      """
      {
        "name": ${jsonEscape(CheckoutSeedConfig.FUNNEL_NAME)},
        "description": ${jsonEscape(CheckoutSeedConfig.FUNNEL_DESCRIPTION)}
      }
      """.trimIndent(),
      bearer = token
    )
    require(create.statusCode() == 201) { "Create funnel failed: ${create.body()}" }
    val funnelId = json.parseToJsonElement(create.body()).jsonObject["id"]!!.jsonPrimitive.content

    listOf(
      CheckoutEventTypes.STARTED to CheckoutSeedConfig.FUNNEL_STEP_STARTED,
      CheckoutEventTypes.DELIVERY_VIEWED to CheckoutSeedConfig.FUNNEL_STEP_DELIVERY,
      CheckoutEventTypes.PAYMENT_VIEWED to CheckoutSeedConfig.FUNNEL_STEP_PAYMENT,
      CheckoutEventTypes.CONFIRMATION_VIEWED to CheckoutSeedConfig.FUNNEL_STEP_CONFIRMATION,
      CheckoutEventTypes.COMPLETED to CheckoutSeedConfig.FUNNEL_STEP_COMPLETED,
    ).forEach { (eventType, _) ->
      val body = """
        {
          "eventType": "$eventType"
        }
      """.trimIndent()
      val r = post("/api/projects/$projectId/funnels/$funnelId/steps", body, bearer = token)
      require(r.statusCode() == 201) { "Add funnel step failed: ${r.body()}" }
    }
    println("Создана воронка: ${CheckoutSeedConfig.FUNNEL_NAME} ($funnelId)")
  }

  private data class SeriesSpec(val label: String, val eventType: String, val platform: String)

  private fun errorSeriesSpecs(): List<SeriesSpec> = listOf(
    SeriesSpec(
      CheckoutSeedConfig.CHART_PAGE_LOAD_ERRORS_IOS,
      CheckoutEventTypes.PAGE_LOAD_ERROR,
      CheckoutSeedConfig.PLATFORM_IOS
    ),
    SeriesSpec(
      CheckoutSeedConfig.CHART_PAGE_LOAD_ERRORS_ANDROID,
      CheckoutEventTypes.PAGE_LOAD_ERROR,
      CheckoutSeedConfig.PLATFORM_ANDROID
    ),
    SeriesSpec(
      CheckoutSeedConfig.CHART_PAYMENT_ERRORS_IOS,
      CheckoutEventTypes.PAYMENT_ERROR,
      CheckoutSeedConfig.PLATFORM_IOS
    ),
    SeriesSpec(
      CheckoutSeedConfig.CHART_PAYMENT_ERRORS_ANDROID,
      CheckoutEventTypes.PAYMENT_ERROR,
      CheckoutSeedConfig.PLATFORM_ANDROID
    ),
  )

  private fun findDashboardSeriesLabels(token: String, projectId: String, dashboardId: String): Set<String> {
    val response = get("/api/projects/$projectId/dashboards/$dashboardId", bearer = token)
    require(response.statusCode() == 200) {
      "Get dashboard failed: ${response.statusCode()} ${response.body()}"
    }
    return json.parseToJsonElement(response.body()).jsonObject["series"]!!.jsonArray
      .mapNotNull { it.jsonObject["label"]?.jsonPrimitive?.content }
      .toSet()
  }

  private fun addSeries(token: String, projectId: String, dashboardId: String, spec: SeriesSpec) {
    val body = """
      {
        "label": ${jsonEscape(spec.label)},
        "eventType": "${spec.eventType}",
        "platform": "${spec.platform}"
      }
    """.trimIndent()
    val r = post("/api/projects/$projectId/dashboards/$dashboardId/series", body, bearer = token)
    require(r.statusCode() == 201) { "Add series '${spec.label}' failed: ${r.body()}" }
  }

  private fun ensureErrorSeries(token: String, projectId: String, dashboardId: String) {
    val existing = findDashboardSeriesLabels(token, projectId, dashboardId)
    var added = 0
    errorSeriesSpecs().forEach { spec ->
      if (spec.label !in existing) {
        addSeries(token, projectId, dashboardId, spec)
        added++
      }
    }
    when {
      added > 0 -> println("Добавлено графиков в «${CheckoutSeedConfig.DASHBOARD_ERRORS_NAME}»: $added")
      existing.isEmpty() -> println("Создан дэшборд: ${CheckoutSeedConfig.DASHBOARD_ERRORS_NAME} ($dashboardId)")
      else -> println("Графики уже есть: ${CheckoutSeedConfig.DASHBOARD_ERRORS_NAME} ($dashboardId)")
    }
  }

  private fun setupDashboards(token: String, projectId: String) {
    val listPath = "/api/projects/$projectId/dashboards"

    val errorsDashId = findResourceIdByName(token, listPath, CheckoutSeedConfig.DASHBOARD_ERRORS_NAME)
      ?: run {
        val errorsDash = post(
          listPath,
          """
          {
            "name": ${jsonEscape(CheckoutSeedConfig.DASHBOARD_ERRORS_NAME)},
            "description": ${jsonEscape(CheckoutSeedConfig.DASHBOARD_ERRORS_DESCRIPTION)}
          }
          """.trimIndent(),
          bearer = token
        )
        require(errorsDash.statusCode() == 201) { "Create errors dashboard failed: ${errorsDash.body()}" }
        json.parseToJsonElement(errorsDash.body()).jsonObject["id"]!!.jsonPrimitive.content
      }

    ensureErrorSeries(token, projectId, errorsDashId)

    val successDashId = findResourceIdByName(token, listPath, CheckoutSeedConfig.DASHBOARD_SUCCESS_NAME)
    if (successDashId != null) {
      println("Дэшборд уже существует: ${CheckoutSeedConfig.DASHBOARD_SUCCESS_NAME} ($successDashId)")
    } else {
      val successDash = post(
        listPath,
        """
        {
          "name": ${jsonEscape(CheckoutSeedConfig.DASHBOARD_SUCCESS_NAME)},
          "description": ${jsonEscape(CheckoutSeedConfig.DASHBOARD_SUCCESS_DESCRIPTION)}
        }
        """.trimIndent(),
        bearer = token
      )
      require(successDash.statusCode() == 201) { "Create success dashboard failed: ${successDash.body()}" }
      val dashId = json.parseToJsonElement(successDash.body()).jsonObject["id"]!!.jsonPrimitive.content

      val body = """
        {
          "label": ${jsonEscape(CheckoutSeedConfig.CHART_SUCCESSFUL_CHECKOUT)},
          "eventType": "${CheckoutEventTypes.COMPLETED}"
        }
      """.trimIndent()
      val series = post("/api/projects/$projectId/dashboards/$dashId/series", body, bearer = token)
      require(series.statusCode() == 201) { "Add success series failed: ${series.body()}" }
      println("Создан дэшборд: ${CheckoutSeedConfig.DASHBOARD_SUCCESS_NAME} ($dashId)")
    }
  }

  private fun weekStartInstant(): Instant =
    Instant.now().atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).minusDays(6).toInstant()

  private fun randomInstantInDay(dayIndex: Int): Instant {
    val dayStart = weekStartInstant().plus(dayIndex.toLong(), ChronoUnit.DAYS)
    val secondsInDay = 24 * 3600
    return dayStart.plus(rng.nextInt(secondsInDay).toLong(), ChronoUnit.SECONDS)
  }

  private fun generateEvents(): List<DemoEvent> {
    val events = mutableListOf<DemoEvent>()
    val countries = listOf("RU", "US", "DE", "KZ")

    for (dayIndex in 0..6) {
      val daySessions = rng.nextInt(120, 221) * (dayIndex + 1)
      repeat(daySessions) {
        val platform = CheckoutSeedConfig.PLATFORMS.random(rng)
        events += simulateSession(dayIndex, platform, countries.random(rng))
      }
    }

    val invalid = events.filter { it.platform !in CheckoutSeedConfig.PLATFORMS }
    require(invalid.isEmpty()) {
      "Every event must have platform ios|android, found: ${invalid.map { it.eventType to it.platform }}"
    }

    return events.sortedBy { it.occurredAt }
  }

  private fun randomPaymentMethod(platform: String): String {
    require(platform in CheckoutSeedConfig.PLATFORMS)
    return when (platform) {
      CheckoutSeedConfig.PLATFORM_IOS ->
        listOf("card", "apple_pay", "sbp").random(rng)

      CheckoutSeedConfig.PLATFORM_ANDROID ->
        listOf("card", "google_pay", "sbp").random(rng)

      else -> error("unsupported platform: $platform")
    }
  }

  private fun simulateSession(dayIndex: Int, platform: String, country: String): List<DemoEvent> {
    require(platform in CheckoutSeedConfig.PLATFORMS) { "platform must be ios or android, got: $platform" }
    val layout = if (rng.nextDouble() < 0.5) "scroll" else "steps"
    val userId = "demo-${UUID.randomUUID()}"
    val orderId = "ord-${UUID.randomUUID().toString().take(12)}"
    val baseTime = randomInstantInDay(dayIndex)
    var t = baseTime
    val out = mutableListOf<DemoEvent>()

    fun baseProps(extra: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject {
      put("checkout_layout", layout)
      put("order_id", orderId)
      extra()
    }

    fun emit(type: String, props: JsonObject, minutesAfter: Int = 0): Instant {
      t = t.plus(minutesAfter.toLong(), ChronoUnit.MINUTES)
      out += DemoEvent(type, userId, t, platform, country, props)
      return t
    }

    emit(
      CheckoutEventTypes.STARTED,
      baseProps {
        put("cart_items_count", rng.nextInt(1, 6).toString())
        put("currency", "RUB")
      }
    )

    val pageLoadErrorBase = if (layout == "steps") 0.065 else 0.025
    val paymentErrorBase = if (isMidWeekSpikeDay(dayIndex)) 0.045 else 0.004

    fun maybePageLoadError(pageNumber: Int, pageId: String) {
      if (rng.nextDouble() < pageLoadErrorBase) {
        emit(
          CheckoutEventTypes.PAGE_LOAD_ERROR,
          baseProps {
            put("page_number", pageNumber.toString())
            put("page_id", pageId)
            put("error_code", listOf("network_timeout", "api_500", "empty_response").random(rng))
          },
          minutesAfter = rng.nextInt(0, 2)
        )
      }
    }

    fun pageViewed(pageNumber: Int, pageId: String, funnelType: String) {
      emit(
        CheckoutEventTypes.PAGE_VIEWED,
        baseProps {
          put("page_number", pageNumber.toString())
          put("page_id", pageId)
        },
        minutesAfter = rng.nextInt(1, 4)
      )
      emit(funnelType, baseProps(), minutesAfter = 0)
    }

    val reachPage1 = rng.nextDouble() < 0.94
    if (!reachPage1) return out
    maybePageLoadError(1, "delivery")
    pageViewed(1, "delivery", CheckoutEventTypes.DELIVERY_VIEWED)

    val reachPage2Prob = if (layout == "scroll") 0.92 else 0.78
    if (rng.nextDouble() >= reachPage2Prob) return out
    maybePageLoadError(2, "payment")
    pageViewed(2, "payment", CheckoutEventTypes.PAYMENT_VIEWED)

    val reachPage3Prob = if (layout == "scroll") 0.88 else 0.72
    if (rng.nextDouble() >= reachPage3Prob) return out
    maybePageLoadError(3, "confirmation")
    pageViewed(3, "confirmation", CheckoutEventTypes.CONFIRMATION_VIEWED)

    val completeProb = if (layout == "steps") 0.68 else 0.32
    if (rng.nextDouble() < completeProb) {
      emit(
        CheckoutEventTypes.COMPLETED,
        baseProps {
          put("payment_method", randomPaymentMethod(platform))
          put("currency", "RUB")
        },
        minutesAfter = rng.nextInt(1, 5)
      )
    } else if (rng.nextDouble() < paymentErrorBase) {
      emit(
        CheckoutEventTypes.PAYMENT_ERROR,
        baseProps {
          put("page_number", "3")
          put("page_id", "confirmation")
          put("error_code", if (isMidWeekSpikeDay(dayIndex)) "provider_error" else "card_declined")
          put("payment_method", "card")
        },
        minutesAfter = rng.nextInt(1, 3)
      )
    }

    return out
  }

  private fun isMidWeekSpikeDay(dayIndex: Int): Boolean = dayIndex in 3..4

  private suspend fun ingestEvents(apiKey: String, events: List<DemoEvent>) = coroutineScope {
    val semaphore = Semaphore(48)
    events.chunked(200).forEachIndexed { batchIdx, batch ->
      batch.map { event ->
        async(Dispatchers.IO) {
          semaphore.withPermit { ingestOne(apiKey, event) }
        }
      }.awaitAll()
      if ((batchIdx + 1) % 10 == 0) {
        println("  … ${(batchIdx + 1) * 200} / ${events.size}")
      }
    }
  }

  private suspend fun ingestOne(apiKey: String, event: DemoEvent) {
    require(event.platform in CheckoutSeedConfig.PLATFORMS) {
      "platform must be ios or android for ${event.eventType}, got: ${event.platform}"
    }
    val propsJson = json.encodeToString(JsonObject.serializer(), event.properties)
    val body = buildString {
      append("""{"eventType":"${event.eventType}",""")
      append(""""userId":"${event.userId}",""")
      append(""""platform":"${event.platform}",""")
      append(""""appVersion":"${CheckoutSeedConfig.APP_VERSION}",""")
      append(""""country":"${event.country}",""")
      append(""""occurredAt":"${event.occurredAt}",""")
      append(""""properties":$propsJson""")
      append("}")
    }

    for (attempt in 0..INGEST_MAX_RETRIES) {
      val response = post("/api/events/ingest", body, apiKey = apiKey)
      if (response.statusCode() == 202) return
      if (response.statusCode() == 503 && isQueueFull(response) && attempt < INGEST_MAX_RETRIES) {
        delay(INGEST_RETRY_DELAY_MS)
        continue
      }
      error("Ingest failed (${response.statusCode()}): ${response.body()} for ${event.eventType}")
    }
  }

  private fun isQueueFull(response: HttpResponse<String>): Boolean =
    response.statusCode() == 503 && response.body().contains("QUEUE_FULL")

  private const val INGEST_MAX_RETRIES = 5
  private const val INGEST_RETRY_DELAY_MS = 2_000L

  private fun get(path: String, bearer: String? = null): HttpResponse<String> =
    request("GET", path, null, bearer, null)

  private fun post(path: String, body: String, bearer: String? = null, apiKey: String? = null): HttpResponse<String> =
    request("POST", path, body, bearer, apiKey)

  private fun put(path: String, body: String, bearer: String? = null): HttpResponse<String> =
    request("PUT", path, body, bearer, null)

  private fun request(
    method: String,
    path: String,
    body: String?,
    bearer: String?,
    apiKey: String?
  ): HttpResponse<String> {
    val builder = HttpRequest.newBuilder()
      .uri(URI.create("$baseUrl$path"))
      .header("Content-Type", "application/json")

    if (body != null) {
      builder.method(method, HttpRequest.BodyPublishers.ofString(body))
    } else {
      builder.GET()
    }

    bearer?.let { builder.header("Authorization", "Bearer $it") }
    apiKey?.let { builder.header("X-API-Key", it) }

    return try {
      http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    } catch (e: Exception) {
      unwrapConnect(e)?.let { throwIllegalStateCannotConnect(0) }
      throw e
    }
  }
}
