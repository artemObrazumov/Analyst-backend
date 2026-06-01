import com.artemobraz.model.DashboardDetailResponse
import com.artemobraz.model.DashboardPageResponse
import com.artemobraz.model.DashboardResponse
import com.artemobraz.model.DashboardSeriesResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardTest {

  private val json = Json { ignoreUnknownKeys = true }

  private suspend fun ApplicationTestBuilder.registerWithProject(): Pair<String, String> {
    val email = "dash_${System.currentTimeMillis()}@test.com"
    val regRes = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val token = Json.parseToJsonElement(regRes.bodyAsText())
      .jsonObject["accessToken"]!!.jsonPrimitive.content

    val projectRes = client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Dashboard Project"}""")
    }
    val projectId = Json.parseToJsonElement(projectRes.bodyAsText())
      .jsonObject["project"]!!.jsonObject["id"]!!.jsonPrimitive.content

    return token to projectId
  }

  private suspend fun ApplicationTestBuilder.registerWithProjectAndKey(): Triple<String, String, String> {
    val email = "dash_ingest_${System.currentTimeMillis()}@test.com"
    val regRes = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val token = Json.parseToJsonElement(regRes.bodyAsText())
      .jsonObject["accessToken"]!!.jsonPrimitive.content

    val projectRes = client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Dashboard Ingest Project"}""")
    }
    val projectBody = Json.parseToJsonElement(projectRes.bodyAsText()).jsonObject
    val projectId = projectBody["project"]!!.jsonObject["id"]!!.jsonPrimitive.content
    val apiKey = projectBody["apiKey"]!!.jsonObject["key"]!!.jsonPrimitive.content
    return Triple(token, projectId, apiKey)
  }

  private suspend fun ApplicationTestBuilder.createDashboard(
    token: String,
    projectId: String,
    name: String = "Main Dashboard"
  ): String {
    val res = client.post("/api/projects/$projectId/dashboards") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"$name"}""")
    }
    return Json.parseToJsonElement(res.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
  }

  private suspend fun ApplicationTestBuilder.ingestEvent(
    apiKey: String,
    body: String
  ) {
    client.post("/api/events/ingest") {
      header("X-API-Key", apiKey)
      contentType(ContentType.Application.Json)
      setBody(body)
    }
  }

  private suspend fun ApplicationTestBuilder.ingestEvent(
    apiKey: String,
    eventType: String,
    occurredAt: String
  ) {
    ingestEvent(
      apiKey,
      """{"eventType":"$eventType","userId":"u1","occurredAt":"$occurredAt"}"""
    )
  }

  private suspend fun ApplicationTestBuilder.waitForDashboardTotalCount(
    token: String,
    projectId: String,
    dashboardId: String,
    expectedTotal: Long,
    from: String,
    to: String,
    timeoutMs: Long = 10_000,
  ) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val response = client.get("/api/projects/$projectId/dashboards/$dashboardId/page") {
        bearerAuth(token)
        parameter("from", from)
        parameter("to", to)
      }
      val body = json.decodeFromString<DashboardPageResponse>(response.bodyAsText())
      val total = body.series.firstOrNull()?.data?.sumOf { it.count } ?: 0L
      if (total == expectedTotal) return
      delay(50)
    }
    val response = client.get("/api/projects/$projectId/dashboards/$dashboardId/page") {
      bearerAuth(token)
      parameter("from", from)
      parameter("to", to)
    }
    val body = json.decodeFromString<DashboardPageResponse>(response.bodyAsText())
    val total = body.series.firstOrNull()?.data?.sumOf { it.count } ?: 0L
    assertEquals(expectedTotal, total, "async event ingest did not finish in time")
  }

  @Test
  fun `create dashboard returns 201`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val response = client.post("/api/projects/$projectId/dashboards") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Overview","description":"KPIs"}""")
    }
    assertEquals(HttpStatusCode.Created, response.status)
    val body = json.decodeFromString<DashboardResponse>(response.bodyAsText())
    assertEquals("Overview", body.name)
    assertEquals("KPIs", body.description)
  }

  @Test
  fun `list dashboards returns dashboards for project`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    createDashboard(token, projectId, "Dash A")
    createDashboard(token, projectId, "Dash B")

    val response = client.get("/api/projects/$projectId/dashboards") { bearerAuth(token) }
    assertEquals(HttpStatusCode.OK, response.status)
    val list = json.decodeFromString<List<DashboardResponse>>(response.bodyAsText())
    assertTrue(list.any { it.name == "Dash A" })
    assertTrue(list.any { it.name == "Dash B" })
  }

  @Test
  fun `get dashboard returns detail with series config`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"label":"Clicks","eventType":"button_click"}""")
    }

    val response = client.get("/api/projects/$projectId/dashboards/$dashboardId") { bearerAuth(token) }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<DashboardDetailResponse>(response.bodyAsText())
    assertEquals(1, body.series.size)
    assertEquals("button_click", body.series[0].eventType)
  }

  @Test
  fun `update and delete dashboard succeed`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val updateResponse = client.put("/api/projects/$projectId/dashboards/$dashboardId") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Updated"}""")
    }
    assertEquals(HttpStatusCode.OK, updateResponse.status)
    assertEquals("Updated", json.decodeFromString<DashboardResponse>(updateResponse.bodyAsText()).name)

    val deleteResponse = client.delete("/api/projects/$projectId/dashboards/$dashboardId") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
  }

  @Test
  fun `page returns name and series data for period`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val dashboardId = createDashboard(token, projectId, "Analytics Page")
    val eventType = "page_view_dash_${System.currentTimeMillis()}"
    val from = "2026-05-20T00:00:00Z"
    val to = "2026-05-21T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"label":"Views","eventType":"$eventType"}""")
    }

    repeat(3) {
      ingestEvent(apiKey, eventType, "2026-05-20T10:00:00Z")
    }
    ingestEvent(apiKey, eventType, "2026-05-21T12:00:00Z")

    waitForDashboardTotalCount(token, projectId, dashboardId, expectedTotal = 4L, from, to)

    val response = client.get("/api/projects/$projectId/dashboards/$dashboardId/page") {
      bearerAuth(token)
      parameter("from", from)
      parameter("to", to)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<DashboardPageResponse>(response.bodyAsText())
    assertEquals("Analytics Page", body.name)
    assertEquals(1, body.series.size)
    assertEquals("Views", body.series[0].label)
    assertEquals(4L, body.series[0].data.sumOf { it.count })
    assertEquals(2, body.series[0].data.size)
    assertTrue(body.series[0].data.any { it.date == "2026-05-20" && it.count == 3L })
    assertTrue(body.series[0].data.any { it.date == "2026-05-21" && it.count == 1L })
  }

  @Test
  fun `page fills zero counts for days without events in period`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val dashboardId = createDashboard(token, projectId)
    val eventType = "sparse_dash_${System.currentTimeMillis()}"
    val from = "2026-05-20T00:00:00Z"
    val to = "2026-05-22T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"label":"Sparse","eventType":"$eventType"}""")
    }

    ingestEvent(apiKey, eventType, "2026-05-21T12:00:00Z")

    waitForDashboardTotalCount(token, projectId, dashboardId, expectedTotal = 1L, from, to)

    val body = json.decodeFromString<DashboardPageResponse>(
      client.get("/api/projects/$projectId/dashboards/$dashboardId/page") {
        bearerAuth(token)
        parameter("from", from)
        parameter("to", to)
      }.bodyAsText()
    )

    val data = body.series.single().data
    assertEquals(3, data.size)
    assertEquals(0L, data.first { it.date == "2026-05-20" }.count)
    assertEquals(1L, data.first { it.date == "2026-05-21" }.count)
    assertEquals(0L, data.first { it.date == "2026-05-22" }.count)
  }

  @Test
  fun `page returns all zero days when no events in period`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)
    val from = "2026-07-01T00:00:00Z"
    val to = "2026-07-02T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"label":"Empty","eventType":"no_events_${System.currentTimeMillis()}"}""")
    }

    val body = json.decodeFromString<DashboardPageResponse>(
      client.get("/api/projects/$projectId/dashboards/$dashboardId/page") {
        bearerAuth(token)
        parameter("from", from)
        parameter("to", to)
      }.bodyAsText()
    )

    val data = body.series.single().data
    assertEquals(2, data.size)
    assertTrue(data.all { it.count == 0L })
  }

  @Test
  fun `update series succeeds`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val addResponse = client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"label":"Old","eventType":"click"}""")
    }
    val series = json.decodeFromString<DashboardSeriesResponse>(addResponse.bodyAsText())

    val updateResponse = client.put("/api/projects/$projectId/dashboards/$dashboardId/series/${series.id}") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody(
        """
        {
          "label":"New label",
          "period":"30d",
          "eventType":"purchase",
          "platform":"ios"
        }
        """.trimIndent()
      )
    }
    assertEquals(HttpStatusCode.OK, updateResponse.status)
    val updated = json.decodeFromString<DashboardSeriesResponse>(updateResponse.bodyAsText())
    assertEquals("New label", updated.label)
    assertEquals("30d", updated.period)
    assertEquals("purchase", updated.eventType)
    assertEquals("ios", updated.platform)

    val detail = json.decodeFromString<DashboardDetailResponse>(
      client.get("/api/projects/$projectId/dashboards/$dashboardId") { bearerAuth(token) }.bodyAsText()
    )
    assertEquals("New label", detail.series.single().label)
  }

  @Test
  fun `add and remove series succeeds`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val addResponse = client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"label":"Signups","eventType":"signup"}""")
    }
    assertEquals(HttpStatusCode.Created, addResponse.status)
    val series = json.decodeFromString<DashboardSeriesResponse>(addResponse.bodyAsText())

    val deleteResponse = client.delete("/api/projects/$projectId/dashboards/$dashboardId/series/${series.id}") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
  }

  @Test
  fun `access to another users dashboards returns 403`() = testApplication {
    configure()
    val (_, projectId) = registerWithProject()

    val otherToken = Json.parseToJsonElement(
      client.post("/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody("""{"name":"Other","email":"other_dash_${System.currentTimeMillis()}@test.com","password":"123456"}""")
      }.bodyAsText()
    ).jsonObject["accessToken"]!!.jsonPrimitive.content

    val response = client.get("/api/projects/$projectId/dashboards") { bearerAuth(otherToken) }
    assertEquals(HttpStatusCode.Forbidden, response.status)
  }

  @Test
  fun `add series with filters returns filters in response`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val response = client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody(
        """
        {
          "label":"iOS clicks",
          "eventType":"button_click",
          "platform":"ios",
          "country":"US"
        }
        """.trimIndent()
      )
    }
    assertEquals(HttpStatusCode.Created, response.status)
    val series = json.decodeFromString<DashboardSeriesResponse>(response.bodyAsText())
    assertEquals("ios", series.platform)
    assertEquals("US", series.country)
  }

  @Test
  fun `page applies platform filter to series data`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val dashboardId = createDashboard(token, projectId)
    val eventType = "click_filter_${System.currentTimeMillis()}"
    val from = "2026-06-01T00:00:00Z"
    val to = "2026-06-02T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody(
        """
        {
          "label":"iOS only",
          "eventType":"$eventType",
          "platform":"ios"
        }
        """.trimIndent()
      )
    }

    ingestEvent(
      apiKey,
      """{"eventType":"$eventType","platform":"ios","occurredAt":"2026-06-01T10:00:00Z"}"""
    )
    ingestEvent(
      apiKey,
      """{"eventType":"$eventType","platform":"ios","occurredAt":"2026-06-01T11:00:00Z"}"""
    )
    ingestEvent(
      apiKey,
      """{"eventType":"$eventType","platform":"android","occurredAt":"2026-06-01T12:00:00Z"}"""
    )

    waitForDashboardTotalCount(token, projectId, dashboardId, expectedTotal = 2L, from, to)

    val body = json.decodeFromString<DashboardPageResponse>(
      client.get("/api/projects/$projectId/dashboards/$dashboardId/page") {
        bearerAuth(token)
        parameter("from", from)
        parameter("to", to)
      }.bodyAsText()
    )
    assertEquals("ios", body.series[0].platform)
    assertEquals(2L, body.series[0].data.sumOf { it.count })
  }

  @Test
  fun `page applies country and property filters`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val dashboardId = createDashboard(token, projectId)
    val eventType = "purchase_filter_${System.currentTimeMillis()}"
    val from = "2026-06-10T00:00:00Z"
    val to = "2026-06-11T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody(
        """
        {
          "label":"US premium",
          "eventType":"$eventType",
          "country":"US",
          "propertyFilters":{"plan":"premium"}
        }
        """.trimIndent()
      )
    }

    ingestEvent(
      apiKey,
      """
      {
        "eventType":"$eventType",
        "country":"US",
        "properties":{"plan":"premium"},
        "occurredAt":"2026-06-10T10:00:00Z"
      }
      """.trimIndent()
    )
    ingestEvent(
      apiKey,
      """
      {
        "eventType":"$eventType",
        "country":"US",
        "properties":{"plan":"basic"},
        "occurredAt":"2026-06-10T11:00:00Z"
      }
      """.trimIndent()
    )
    ingestEvent(
      apiKey,
      """
      {
        "eventType":"$eventType",
        "country":"RU",
        "properties":{"plan":"premium"},
        "occurredAt":"2026-06-10T12:00:00Z"
      }
      """.trimIndent()
    )

    waitForDashboardTotalCount(token, projectId, dashboardId, expectedTotal = 1L, from, to)
  }

  @Test
  fun `add series with invalid country returns 400`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val response = client.post("/api/projects/$projectId/dashboards/$dashboardId/series") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"label":"Bad","eventType":"x","country":"USA"}""")
    }
    assertEquals(HttpStatusCode.BadRequest, response.status)
  }

}
