import com.artemobraz.model.FunnelAnalysisResponse
import com.artemobraz.model.FunnelDetailResponse
import com.artemobraz.model.FunnelResponse
import com.artemobraz.model.FunnelStepResponse
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FunnelTest {

  private val json = Json { ignoreUnknownKeys = true }

  private suspend fun ApplicationTestBuilder.registerWithProject(): Pair<String, String> {
    val email = "funnel_${System.currentTimeMillis()}@test.com"
    val regRes = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val token = Json.parseToJsonElement(regRes.bodyAsText())
      .jsonObject["accessToken"]!!.jsonPrimitive.content

    val projectRes = client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Funnel Project"}""")
    }
    val projectId = Json.parseToJsonElement(projectRes.bodyAsText())
      .jsonObject["project"]!!.jsonObject["id"]!!.jsonPrimitive.content

    return token to projectId
  }

  private suspend fun ApplicationTestBuilder.registerWithProjectAndKey(): Triple<String, String, String> {
    val email = "funnel_ingest_${System.currentTimeMillis()}@test.com"
    val regRes = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val token = Json.parseToJsonElement(regRes.bodyAsText())
      .jsonObject["accessToken"]!!.jsonPrimitive.content

    val projectRes = client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Funnel Ingest Project"}""")
    }
    val projectBody = Json.parseToJsonElement(projectRes.bodyAsText()).jsonObject
    val projectId = projectBody["project"]!!.jsonObject["id"]!!.jsonPrimitive.content
    val apiKey = projectBody["apiKey"]!!.jsonObject["key"]!!.jsonPrimitive.content
    return Triple(token, projectId, apiKey)
  }

  private suspend fun ApplicationTestBuilder.createFunnel(
    token: String,
    projectId: String,
    name: String = "Purchase Funnel"
  ): String {
    val res = client.post("/api/projects/$projectId/funnels") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"$name"}""")
    }
    return Json.parseToJsonElement(res.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
  }

  private suspend fun ApplicationTestBuilder.ingestEvent(
    apiKey: String,
    eventType: String,
    userId: String,
    occurredAt: String
  ) {
    client.post("/api/events/ingest") {
      header("X-API-Key", apiKey)
      contentType(ContentType.Application.Json)
      setBody("""{"events":[{"eventType":"$eventType","userId":"$userId","occurredAt":"$occurredAt"}]}""")
    }
  }

  @Test
  fun `create funnel returns 201`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val response = client.post("/api/projects/$projectId/funnels") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Checkout Funnel","description":"View to purchase"}""")
    }
    assertEquals(HttpStatusCode.Created, response.status)
    val body = json.decodeFromString<FunnelResponse>(response.bodyAsText())
    assertEquals("Checkout Funnel", body.name)
    assertEquals("View to purchase", body.description)
    assertEquals(projectId, body.projectId)
  }

  @Test
  fun `list funnels returns funnels for project`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    createFunnel(token, projectId, "Funnel A")
    createFunnel(token, projectId, "Funnel B")

    val response = client.get("/api/projects/$projectId/funnels") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val list = json.decodeFromString<List<FunnelResponse>>(response.bodyAsText())
    assertTrue(list.any { it.name == "Funnel A" })
    assertTrue(list.any { it.name == "Funnel B" })
  }

  @Test
  fun `get funnel returns detail with steps`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val funnelId = createFunnel(token, projectId)

    client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"page_view"}""")
    }
    client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"button_click"}""")
    }

    val response = client.get("/api/projects/$projectId/funnels/$funnelId") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<FunnelDetailResponse>(response.bodyAsText())
    assertEquals(funnelId, body.id)
    assertEquals(2, body.steps.size)
    assertEquals(1, body.steps[0].stepOrder)
    assertEquals("page_view", body.steps[0].eventType)
    assertEquals(2, body.steps[1].stepOrder)
  }

  @Test
  fun `update funnel returns updated fields`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val funnelId = createFunnel(token, projectId)

    val response = client.put("/api/projects/$projectId/funnels/$funnelId") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Updated Funnel","description":"new desc"}""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<FunnelResponse>(response.bodyAsText())
    assertEquals("Updated Funnel", body.name)
    assertEquals("new desc", body.description)
  }

  @Test
  fun `delete funnel returns 204`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val funnelId = createFunnel(token, projectId)

    val deleteResponse = client.delete("/api/projects/$projectId/funnels/$funnelId") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

    val getResponse = client.get("/api/projects/$projectId/funnels/$funnelId") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NotFound, getResponse.status)
  }

  @Test
  fun `add and remove step succeeds`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val funnelId = createFunnel(token, projectId)

    val addResponse = client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"signup"}""")
    }
    assertEquals(HttpStatusCode.Created, addResponse.status)
    val step = json.decodeFromString<FunnelStepResponse>(addResponse.bodyAsText())
    assertEquals("signup", step.eventType)
    assertEquals(1, step.stepOrder)

    val deleteResponse = client.delete("/api/projects/$projectId/funnels/$funnelId/steps/${step.id}") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

    val detail = client.get("/api/projects/$projectId/funnels/$funnelId") { bearerAuth(token) }
    val body = json.decodeFromString<FunnelDetailResponse>(detail.bodyAsText())
    assertTrue(body.steps.isEmpty())
  }

  @Test
  fun `reorder steps updates step order`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val funnelId = createFunnel(token, projectId)

    val step1 = json.decodeFromString<FunnelStepResponse>(
      client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
        contentType(ContentType.Application.Json)
        bearerAuth(token)
        setBody("""{"eventType":"a"}""")
      }.bodyAsText()
    )
    val step2 = json.decodeFromString<FunnelStepResponse>(
      client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
        contentType(ContentType.Application.Json)
        bearerAuth(token)
        setBody("""{"eventType":"b"}""")
      }.bodyAsText()
    )

    val reorderResponse = client.put("/api/projects/$projectId/funnels/$funnelId/steps/reorder") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"stepIds":["${step2.id}","${step1.id}"]}""")
    }
    assertEquals(HttpStatusCode.OK, reorderResponse.status)

    val detail = json.decodeFromString<FunnelDetailResponse>(
      client.get("/api/projects/$projectId/funnels/$funnelId") { bearerAuth(token) }.bodyAsText()
    )
    assertEquals(step2.id, detail.steps[0].id)
    assertEquals(1, detail.steps[0].stepOrder)
    assertEquals(step1.id, detail.steps[1].id)
    assertEquals(2, detail.steps[1].stepOrder)
  }

  @Test
  fun `access to another users project funnels returns 403`() = testApplication {
    configure()
    val (_, projectId) = registerWithProject()

    val otherEmail = "other_funnel_${System.currentTimeMillis()}@test.com"
    val otherToken = Json.parseToJsonElement(
      client.post("/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody("""{"name":"Other","email":"$otherEmail","password":"123456"}""")
      }.bodyAsText()
    ).jsonObject["accessToken"]!!.jsonPrimitive.content

    val response = client.get("/api/projects/$projectId/funnels") {
      bearerAuth(otherToken)
    }
    assertEquals(HttpStatusCode.Forbidden, response.status)
  }

  @Test
  fun `unauthenticated request returns 401`() = testApplication {
    configure()
    val (_, projectId) = registerWithProject()

    val response = client.get("/api/projects/$projectId/funnels")
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `analysis returns zero counts when no events ingested`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val funnelId = createFunnel(token, projectId)

    client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"step_one"}""")
    }
    client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"step_two"}""")
    }

    val response = client.get("/api/projects/$projectId/funnels/$funnelId/analysis") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<FunnelAnalysisResponse>(response.bodyAsText())
    assertEquals(2, body.steps.size)
    assertEquals(0L, body.steps[0].usersCount)
    assertEquals(0L, body.steps[1].usersCount)
    assertEquals(0.0, body.overallConversion)
    assertNull(body.steps[0].conversionFromPrevious)
  }

  @Test
  fun `analysis counts users conversion and avg time between steps`() = testApplication {
    configure()
    val (token, projectId, apiKey) = registerWithProjectAndKey()
    val funnelId = createFunnel(token, projectId)

    client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"funnel_view"}""")
    }
    client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"funnel_cart"}""")
    }
    client.post("/api/projects/$projectId/funnels/$funnelId/steps") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"funnel_buy"}""")
    }

    ingestEvent(apiKey, "funnel_view", "u1", "2026-05-20T10:00:00Z")
    ingestEvent(apiKey, "funnel_view", "u2", "2026-05-20T10:00:00Z")
    ingestEvent(apiKey, "funnel_view", "u3", "2026-05-20T10:00:00Z")
    ingestEvent(apiKey, "funnel_cart", "u1", "2026-05-20T10:05:00Z")
    ingestEvent(apiKey, "funnel_cart", "u2", "2026-05-20T10:10:00Z")
    ingestEvent(apiKey, "funnel_buy", "u1", "2026-05-20T10:15:00Z")

    delay(500)

    val response = client.get("/api/projects/$projectId/funnels/$funnelId/analysis") {
      bearerAuth(token)
      parameter("from", "2026-05-20T00:00:00Z")
      parameter("to", "2026-05-21T00:00:00Z")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<FunnelAnalysisResponse>(response.bodyAsText())
    assertEquals(3L, body.steps[0].usersCount)
    assertEquals(2L, body.steps[1].usersCount)
    assertEquals(1L, body.steps[2].usersCount)
    assertEquals(66.67, body.steps[1].conversionFromPrevious)
    assertEquals(33.33, body.steps[1].dropOffFromPrevious)
    assertEquals(50.0, body.steps[2].conversionFromPrevious)
    assertEquals(450.0, body.steps[1].avgSecondsFromPrevious)
    assertEquals(600.0, body.steps[2].avgSecondsFromPrevious)
    assertEquals(33.33, body.overallConversion)
  }

}
