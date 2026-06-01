import com.artemobraz.model.ExperimentAnalysisResponse
import com.artemobraz.model.ExperimentDetailResponse
import com.artemobraz.model.ExperimentResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentTest {

  private val json = Json { ignoreUnknownKeys = true }

  private suspend fun ApplicationTestBuilder.registerWithProject(): Pair<String, String> {
    val email = "exp_${System.currentTimeMillis()}@test.com"
    val regRes = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val token = Json.parseToJsonElement(regRes.bodyAsText())
      .jsonObject["accessToken"]!!.jsonPrimitive.content

    val projectRes = client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Exp Project"}""")
    }
    val projectId = Json.parseToJsonElement(projectRes.bodyAsText())
      .jsonObject["project"]!!.jsonObject["id"]!!.jsonPrimitive.content

    return token to projectId
  }

  private suspend fun ApplicationTestBuilder.createExperiment(
    token: String,
    projectId: String,
    name: String = "My Experiment"
  ): String {
    val res = client.post("/api/projects/$projectId/experiments") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"$name"}""")
    }
    return Json.parseToJsonElement(res.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.content
  }

  private suspend fun ApplicationTestBuilder.waitForAnalysisExposed(
    token: String,
    projectId: String,
    experimentId: String,
    expectedExposed: Long,
    timeoutMs: Long = 10_000,
  ) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      val response = client.get("/api/projects/$projectId/experiments/$experimentId/analysis") {
        bearerAuth(token)
      }
      val body = json.decodeFromString<ExperimentAnalysisResponse>(response.bodyAsText())
      if (body.groups[0].exposed == expectedExposed) return
      delay(50)
    }
    val response = client.get("/api/projects/$projectId/experiments/$experimentId/analysis") {
      bearerAuth(token)
    }
    val body = json.decodeFromString<ExperimentAnalysisResponse>(response.bodyAsText())
    assertEquals(expectedExposed, body.groups[0].exposed, "async event ingest did not finish in time")
  }

  @Test
  fun `create experiment returns 201 with draft status`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val response = client.post("/api/projects/$projectId/experiments") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"New Experiment","description":"test"}""")
    }
    assertEquals(HttpStatusCode.Created, response.status)
    val body = json.decodeFromString<ExperimentResponse>(response.bodyAsText())
    assertEquals("New Experiment", body.name)
    assertEquals("draft", body.status)
    assertEquals(projectId, body.projectId)
  }

  @Test
  fun `list experiments returns only experiments for given project`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    createExperiment(token, projectId, "Exp A")
    createExperiment(token, projectId, "Exp B")

    val response = client.get("/api/projects/$projectId/experiments") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val list = json.decodeFromString<List<ExperimentResponse>>(response.bodyAsText())
    assertTrue(list.any { it.name == "Exp A" })
    assertTrue(list.any { it.name == "Exp B" })
  }

  @Test
  fun `get experiment returns detail with empty groups and events`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val id = createExperiment(token, projectId)

    val response = client.get("/api/projects/$projectId/experiments/$id") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<ExperimentDetailResponse>(response.bodyAsText())
    assertEquals(id, body.id)
    assertTrue(body.groups.isEmpty())
    assertTrue(body.events.isEmpty())
  }

  @Test
  fun `update experiment returns updated fields`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val id = createExperiment(token, projectId)

    val response = client.put("/api/projects/$projectId/experiments/$id") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Updated","result":"positive"}""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<ExperimentResponse>(response.bodyAsText())
    assertEquals("Updated", body.name)
    assertEquals("positive", body.result)
  }

  @Test
  fun `update status to active succeeds`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val id = createExperiment(token, projectId)

    val response = client.put("/api/projects/$projectId/experiments/$id/status") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"status":"active"}""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<ExperimentResponse>(response.bodyAsText())
    assertEquals("active", body.status)
  }

  @Test
  fun `update status with invalid value returns 400`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val id = createExperiment(token, projectId)

    val response = client.put("/api/projects/$projectId/experiments/$id/status") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"status":"running"}""")
    }
    assertEquals(HttpStatusCode.BadRequest, response.status)
  }

  @Test
  fun `delete experiment returns 204`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val id = createExperiment(token, projectId)

    val response = client.delete("/api/projects/$projectId/experiments/$id") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NoContent, response.status)

    val getResponse = client.get("/api/projects/$projectId/experiments/$id") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NotFound, getResponse.status)
  }

  @Test
  fun `add and remove group succeeds`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val id = createExperiment(token, projectId)

    val addResponse = client.post("/api/projects/$projectId/experiments/$id/groups") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"propertyKey":"platform","propertyValue":"ios","label":"iOS group"}""")
    }
    assertEquals(HttpStatusCode.Created, addResponse.status)
    val groupId = Json.parseToJsonElement(addResponse.bodyAsText())
      .jsonObject["id"]!!.jsonPrimitive.content

    val detailAfterAdd = client.get("/api/projects/$projectId/experiments/$id") { bearerAuth(token) }
    val groups = Json.parseToJsonElement(detailAfterAdd.bodyAsText()).jsonObject["groups"]!!.jsonArray
    assertEquals(1, groups.size)

    val deleteResponse = client.delete("/api/projects/$projectId/experiments/$id/groups/$groupId") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
  }

  @Test
  fun `add and remove event link succeeds`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val id = createExperiment(token, projectId)

    val addResponse = client.post("/api/projects/$projectId/experiments/$id/events") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"button_click","note":"CTA button"}""")
    }
    assertEquals(HttpStatusCode.Created, addResponse.status)
    val eventId = Json.parseToJsonElement(addResponse.bodyAsText())
      .jsonObject["id"]!!.jsonPrimitive.content

    val deleteResponse = client.delete("/api/projects/$projectId/experiments/$id/events/$eventId") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
  }

  @Test
  fun `access to another user's project experiments returns 403`() = testApplication {
    configure()
    val (_, projectId) = registerWithProject()

    val otherEmail = "other_${System.currentTimeMillis()}@test.com"
    val otherToken = Json.parseToJsonElement(
      client.post("/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody("""{"name":"Other User","email":"$otherEmail","password":"123456"}""")
      }.bodyAsText()
    ).jsonObject["accessToken"]!!.jsonPrimitive.content

    val response = client.get("/api/projects/$projectId/experiments") {
      bearerAuth(otherToken)
    }
    assertEquals(HttpStatusCode.Forbidden, response.status)
  }

  @Test
  fun `get non-existent experiment returns 404`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()

    val response = client.get("/api/projects/$projectId/experiments/00000000-0000-0000-0000-000000000000") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun `unauthenticated request returns 401`() = testApplication {
    configure()
    val (_, projectId) = registerWithProject()

    val response = client.get("/api/projects/$projectId/experiments")
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `analysis returns zero counts when no events ingested`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val experimentId = createExperiment(token, projectId)

    client.post("/api/projects/$projectId/experiments/$experimentId/groups") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"propertyKey":"color","propertyValue":"red","label":"Red"}""")
    }
    client.post("/api/projects/$projectId/experiments/$experimentId/events") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"Button.Shown"}""")
    }
    client.post("/api/projects/$projectId/experiments/$experimentId/events") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"Button.Clicked"}""")
    }

    val response = client.get("/api/projects/$projectId/experiments/$experimentId/analysis") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<ExperimentAnalysisResponse>(response.bodyAsText())
    assertEquals(experimentId, body.experimentId)
    assertEquals(listOf("Button.Shown", "Button.Clicked"), body.trackedEvents)
    assertEquals(1, body.groups.size)
    assertEquals("Red", body.groups[0].label)
    assertEquals(0L, body.groups[0].exposed)
    assertEquals(0L, body.groups[0].converted)
    assertEquals(0.0, body.groups[0].conversionRate)
  }

  @Test
  fun `analysis counts exposed and converted users correctly`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val experimentId = createExperiment(token, projectId)

    client.post("/api/projects/$projectId/experiments/$experimentId/groups") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"propertyKey":"color","propertyValue":"red","label":"Red"}""")
    }
    client.post("/api/projects/$projectId/experiments/$experimentId/events") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"Button.Shown"}""")
    }
    client.post("/api/projects/$projectId/experiments/$experimentId/events") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"Button.Clicked"}""")
    }

    val projectsBody = Json.parseToJsonElement(
      client.get("/api/projects") { bearerAuth(token) }.bodyAsText()
    ).jsonArray
    val apiKey = run {
      val pid = projectsBody.first().jsonObject["id"]!!.jsonPrimitive.content
      Json.parseToJsonElement(
        client.post("/api/projects/$pid/key/rotate") { bearerAuth(token) }.bodyAsText()
      ).jsonObject["key"]!!.jsonPrimitive.content
    }

    repeat(3) { i ->
      client.post("/api/events/ingest") {
        header("X-API-Key", apiKey)
        contentType(ContentType.Application.Json)
        setBody("""{"events":[{"eventType":"Button.Shown","userId":"user$i","properties":{"color":"red"}}]}""")
      }
    }
    repeat(2) { i ->
      client.post("/api/events/ingest") {
        header("X-API-Key", apiKey)
        contentType(ContentType.Application.Json)
        setBody("""{"events":[{"eventType":"Button.Clicked","userId":"user$i"}]}""")
      }
    }

    waitForAnalysisExposed(token, projectId, experimentId, expectedExposed = 3L)

    val response = client.get("/api/projects/$projectId/experiments/$experimentId/analysis") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<ExperimentAnalysisResponse>(response.bodyAsText())
    assertEquals(3L, body.groups[0].exposed)
    assertEquals(2L, body.groups[0].converted)
    assertEquals(66.67, body.groups[0].conversionRate)
  }

  @Test
  fun `analysis with two groups shows separate metrics`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val experimentId = createExperiment(token, projectId, "Color A/B Test")

    listOf("red" to "Red", "yellow" to "Yellow").forEach { (value, label) ->
      client.post("/api/projects/$projectId/experiments/$experimentId/groups") {
        contentType(ContentType.Application.Json)
        bearerAuth(token)
        setBody("""{"propertyKey":"color","propertyValue":"$value","label":"$label"}""")
      }
    }
    client.post("/api/projects/$projectId/experiments/$experimentId/events") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"Button.Shown"}""")
    }

    val response = client.get("/api/projects/$projectId/experiments/$experimentId/analysis") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<ExperimentAnalysisResponse>(response.bodyAsText())
    assertEquals("Color A/B Test", body.experimentName)
    assertEquals(2, body.groups.size)
    assertTrue(body.groups.any { it.label == "Red" && it.propertyValue == "red" })
    assertTrue(body.groups.any { it.label == "Yellow" && it.propertyValue == "yellow" })
  }

  @Test
  fun `add group returns full group data`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val experimentId = createExperiment(token, projectId)

    val response = client.post("/api/projects/$projectId/experiments/$experimentId/groups") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"propertyKey":"platform","propertyValue":"android","label":"Android users"}""")
    }

    assertEquals(HttpStatusCode.Created, response.status)
    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    assertEquals("platform", body["propertyKey"]!!.jsonPrimitive.content)
    assertEquals("android", body["propertyValue"]!!.jsonPrimitive.content)
    assertEquals("Android users", body["label"]!!.jsonPrimitive.content)
    assertTrue(body["id"]!!.jsonPrimitive.content.isNotBlank())
  }

  @Test
  fun `add event returns full event data`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val experimentId = createExperiment(token, projectId)

    val response = client.post("/api/projects/$projectId/experiments/$experimentId/events") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"eventType":"purchase","note":"Tracks conversion"}""")
    }

    assertEquals(HttpStatusCode.Created, response.status)
    val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
    assertEquals("purchase", body["eventType"]!!.jsonPrimitive.content)
    assertEquals("Tracks conversion", body["note"]!!.jsonPrimitive.content)
    assertTrue(body["id"]!!.jsonPrimitive.content.isNotBlank())
  }

  @Test
  fun `non-owner cannot add group to experiment`() = testApplication {
    configure()
    val (ownerToken, projectId) = registerWithProject()
    val ownerExperimentId = createExperiment(ownerToken, projectId)

    val strangerEmail = "stranger_${System.currentTimeMillis()}@test.com"
    val strangerToken = Json.parseToJsonElement(
      client.post("/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody("""{"name":"Stranger","email":"$strangerEmail","password":"123456"}""")
      }.bodyAsText()
    ).jsonObject["accessToken"]!!.jsonPrimitive.content

    val response = client.post("/api/projects/$projectId/experiments/$ownerExperimentId/groups") {
      contentType(ContentType.Application.Json)
      bearerAuth(strangerToken)
      setBody("""{"propertyKey":"os","propertyValue":"ios","label":"iOS"}""")
    }
    assertEquals(HttpStatusCode.Forbidden, response.status)
  }

  @Test
  fun `non-owner cannot add event to experiment`() = testApplication {
    configure()
    val (token, projectId) = registerWithProject()
    val experimentId = createExperiment(token, projectId)

    val strangerEmail = "stranger2_${System.currentTimeMillis()}@test.com"
    val strangerToken = Json.parseToJsonElement(
      client.post("/api/auth/register") {
        contentType(ContentType.Application.Json)
        setBody("""{"name":"Stranger","email":"$strangerEmail","password":"123456"}""")
      }.bodyAsText()
    ).jsonObject["accessToken"]!!.jsonPrimitive.content

    val response = client.post("/api/projects/$projectId/experiments/$experimentId/events") {
      contentType(ContentType.Application.Json)
      bearerAuth(strangerToken)
      setBody("""{"eventType":"purchase"}""")
    }
    assertEquals(HttpStatusCode.Forbidden, response.status)
  }
}
