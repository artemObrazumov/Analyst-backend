import com.artemobraz.model.ApiKeyCreatedResponse
import com.artemobraz.model.ProjectResponse
import com.artemobraz.model.ProjectWithKeyResponse
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProjectTest {

  private val json = Json { ignoreUnknownKeys = true }

  private suspend fun ApplicationTestBuilder.registerAndGetToken(): String {
    val email = "proj_${System.currentTimeMillis()}@test.com"
    val res = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    return Json.parseToJsonElement(res.bodyAsText()).jsonObject["accessToken"]!!.jsonPrimitive.content
  }

  @Test
  fun `create project returns 201 with one-time key`() = testApplication {
    configure()
    val token = registerAndGetToken()
    val response = client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"My App","description":"test project"}""")
    }
    assertEquals(HttpStatusCode.Created, response.status)
    val body = json.decodeFromString<ProjectWithKeyResponse>(response.bodyAsText())
    assertEquals("My App", body.project.name)
    assertTrue(body.apiKey.key.startsWith("proj_"))
  }

  @Test
  fun `list projects returns only own projects`() = testApplication {
    configure()
    val token = registerAndGetToken()
    client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Listed Project"}""")
    }
    val response = client.get("/api/projects") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val projects = json.decodeFromString<List<ProjectResponse>>(response.bodyAsText())
    assertTrue(projects.any { it.name == "Listed Project" })
  }

  @Test
  fun `rotate key returns new key different from original`() = testApplication {
    configure()
    val token = registerAndGetToken()
    val createResponse = client.post("/api/projects") {
      contentType(ContentType.Application.Json)
      bearerAuth(token)
      setBody("""{"name":"Key Rotation Test"}""")
    }
    val created = json.decodeFromString<ProjectWithKeyResponse>(createResponse.bodyAsText())
    val projectId = created.project.id
    val originalKey = created.apiKey.key

    val rotateResponse = client.post("/api/projects/$projectId/key/rotate") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, rotateResponse.status)
    val rotated = json.decodeFromString<ApiKeyCreatedResponse>(rotateResponse.bodyAsText())
    assertTrue(rotated.key.startsWith("proj_"))
    assertNotEquals(originalKey, rotated.key)
  }
}
