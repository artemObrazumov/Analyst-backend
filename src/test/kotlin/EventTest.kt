import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventTest {

  private suspend fun ApplicationTestBuilder.registerWithProject(): Pair<String, String> {
    val email = "evt_${System.currentTimeMillis()}@test.com"
    val regRes = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val token = Json.parseToJsonElement(regRes.bodyAsText())
      .jsonObject["accessToken"]!!.jsonPrimitive.content

    val projectRes = client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Test Project"}""")
    }
    val projectBody = Json.parseToJsonElement(projectRes.bodyAsText()).jsonObject
    val apiKey = projectBody["apiKey"]!!.jsonObject["key"]!!.jsonPrimitive.content

    return Pair(token, apiKey)
  }

  @Test
  fun `ingest batch with valid API key returns 200`() = testApplication {
    configure()
    val (_, apiKey) = registerWithProject()
    val response = client.post("/api/events/ingest") {
      header("X-API-Key", apiKey)
      contentType(ContentType.Application.Json)
      setBody(
        """
        {
          "events": [
            {"eventType":"button_click","platform":"ios","appVersion":"1.0"},
            {"eventType":"screen_view","platform":"ios"}
          ]
        }
        """.trimIndent()
      )
    }
    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals(2, Json.parseToJsonElement(response.bodyAsText()).jsonObject["accepted"]!!.jsonPrimitive.content.toInt())
  }

  @Test
  fun `ingest empty batch returns 200 with zero accepted`() = testApplication {
    configure()
    val (_, apiKey) = registerWithProject()
    val response = client.post("/api/events/ingest") {
      header("X-API-Key", apiKey)
      contentType(ContentType.Application.Json)
      setBody("""{"events":[]}""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    assertEquals(0, Json.parseToJsonElement(response.bodyAsText()).jsonObject["accepted"]!!.jsonPrimitive.content.toInt())
  }

  @Test
  fun `ingest with invalid API key returns 401`() = testApplication {
    configure()
    val response = client.post("/api/events/ingest") {
      header("X-API-Key", "proj_invalid_key")
      contentType(ContentType.Application.Json)
      setBody("""{"events":[{"eventType":"button_click"}]}""")
    }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `ingest without API key returns 401`() = testApplication {
    configure()
    val response = client.post("/api/events/ingest") {
      contentType(ContentType.Application.Json)
      setBody("""{"events":[{"eventType":"button_click"}]}""")
    }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `list without projectId returns 400`() = testApplication {
    configure()
    val (token, _) = registerWithProject()
    val response = client.get("/api/events") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.BadRequest, response.status)
  }

  @Test
  fun `list with valid projectId returns 200`() = testApplication {
    configure()
    val (token, _) = registerWithProject()
    val projectsRes = client.get("/api/projects") { bearerAuth(token) }
    val projectId = Json.parseToJsonElement(projectsRes.bodyAsText())
      .jsonArray.first().jsonObject["id"]!!.jsonPrimitive.content

    val response = client.get("/api/events?projectId=$projectId") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(Json.parseToJsonElement(response.bodyAsText()).jsonArray.isEmpty())
  }
}
