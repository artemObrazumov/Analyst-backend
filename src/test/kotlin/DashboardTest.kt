import com.artemobraz.model.DashboardChartResponse
import com.artemobraz.model.DashboardDetailResponse
import com.artemobraz.model.DashboardPageResponse
import com.artemobraz.model.DashboardResponse
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
      val total = body.charts.firstOrNull()?.data?.sumOf { it.count } ?: 0L
      if (total == expectedTotal) return
      delay(50)
    }
    val response = client.get("/api/projects/$projectId/dashboards/$dashboardId/page") {
      bearerAuth(token)
      parameter("from", from)
      parameter("to", to)
    }
    val body = json.decodeFromString<DashboardPageResponse>(response.bodyAsText())
    val total = body.charts.firstOrNull()?.data?.sumOf { it.count } ?: 0L
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
  fun `get dashboard returns detail with charts config`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"title":"Clicks","chartType":"line","eventType":"button_click"}""")
    }

    val response = client.get("/api/projects/$projectId/dashboards/$dashboardId") { bearerAuth(token) }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<DashboardDetailResponse>(response.bodyAsText())
    assertEquals(1, body.charts.size)
    assertEquals("button_click", body.charts[0].eventType)
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
  fun `page returns name and chart data for period`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val dashboardId = createDashboard(token, projectId, "Analytics Page")
    val eventType = "page_view_dash_${System.currentTimeMillis()}"
    val from = "2026-05-20T00:00:00Z"
    val to = "2026-05-21T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"title":"Views","chartType":"bar","eventType":"$eventType"}""")
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
    assertEquals(1, body.charts.size)
    assertEquals("Views", body.charts[0].title)
    assertEquals("bar", body.charts[0].chartType)
    assertEquals(4L, body.charts[0].data.sumOf { it.count })
    assertEquals(2, body.charts[0].data.size)
    assertTrue(body.charts[0].data.any { it.date == "2026-05-20" && it.count == 3L })
    assertTrue(body.charts[0].data.any { it.date == "2026-05-21" && it.count == 1L })
  }

  @Test
  fun `page fills zero counts for days without events in period`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val dashboardId = createDashboard(token, projectId)
    val eventType = "sparse_dash_${System.currentTimeMillis()}"
    val from = "2026-05-20T00:00:00Z"
    val to = "2026-05-22T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"title":"Sparse","eventType":"$eventType"}""")
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

    val data = body.charts.single().data
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

    client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"title":"Empty","eventType":"no_events_${System.currentTimeMillis()}"}""")
    }

    val body = json.decodeFromString<DashboardPageResponse>(
      client.get("/api/projects/$projectId/dashboards/$dashboardId/page") {
        bearerAuth(token)
        parameter("from", from)
        parameter("to", to)
      }.bodyAsText()
    )

    val data = body.charts.single().data
    assertEquals(2, data.size)
    assertTrue(data.all { it.count == 0L })
  }

  @Test
  fun `update chart succeeds`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val addResponse = client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"title":"Old","chartType":"line","eventType":"click"}""")
    }
    val chart = json.decodeFromString<DashboardChartResponse>(addResponse.bodyAsText())

    val updateResponse = client.put("/api/projects/$projectId/dashboards/$dashboardId/charts/${chart.id}") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody(
        """
        {
          "title":"New title",
          "chartType":"bar",
          "eventType":"purchase",
          "filters":{"platform":"ios"}
        }
        """.trimIndent()
      )
    }
    assertEquals(HttpStatusCode.OK, updateResponse.status)
    val updated = json.decodeFromString<DashboardChartResponse>(updateResponse.bodyAsText())
    assertEquals("New title", updated.title)
    assertEquals("bar", updated.chartType)
    assertEquals("purchase", updated.eventType)
    assertEquals("ios", updated.filters.platform)

    val detail = json.decodeFromString<DashboardDetailResponse>(
      client.get("/api/projects/$projectId/dashboards/$dashboardId") { bearerAuth(token) }.bodyAsText()
    )
    assertEquals("New title", detail.charts.single().title)
  }

  @Test
  fun `add and remove chart succeeds`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val addResponse = client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"title":"Signups","eventType":"signup"}""")
    }
    assertEquals(HttpStatusCode.Created, addResponse.status)
    val chart = json.decodeFromString<DashboardChartResponse>(addResponse.bodyAsText())

    val deleteResponse = client.delete("/api/projects/$projectId/dashboards/$dashboardId/charts/${chart.id}") {
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
  fun `add chart with filters returns filters in response`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val response = client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody(
        """
        {
          "title":"iOS clicks",
          "eventType":"button_click",
          "filters":{"platform":"ios","country":"US"}
        }
        """.trimIndent()
      )
    }
    assertEquals(HttpStatusCode.Created, response.status)
    val chart = json.decodeFromString<DashboardChartResponse>(response.bodyAsText())
    assertEquals("ios", chart.filters.platform)
    assertEquals("US", chart.filters.country)
  }

  @Test
  fun `page applies platform filter to chart data`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val dashboardId = createDashboard(token, projectId)
    val eventType = "click_filter_${System.currentTimeMillis()}"
    val from = "2026-06-01T00:00:00Z"
    val to = "2026-06-02T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody(
        """
        {
          "title":"iOS only",
          "eventType":"$eventType",
          "filters":{"platform":"ios"}
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
    assertEquals("ios", body.charts[0].filters.platform)
    assertEquals(2L, body.charts[0].data.sumOf { it.count })
  }

  @Test
  fun `page applies country and properties filters`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val dashboardId = createDashboard(token, projectId)
    val eventType = "purchase_filter_${System.currentTimeMillis()}"
    val from = "2026-06-10T00:00:00Z"
    val to = "2026-06-11T23:59:59Z"

    client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody(
        """
        {
          "title":"US premium",
          "eventType":"$eventType",
          "filters":{"country":"US","properties":{"plan":"premium"}}
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
  fun `add chart with invalid country returns 400`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val dashboardId = createDashboard(token, projectId)

    val response = client.post("/api/projects/$projectId/dashboards/$dashboardId/charts") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"title":"Bad","eventType":"x","filters":{"country":"USA"}}""")
    }
    assertEquals(HttpStatusCode.BadRequest, response.status)
  }

}
