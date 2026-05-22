import com.artemobraz.model.TokenResponse
import com.artemobraz.model.UserResponse
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

class AuthTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun `register returns 200 with token pair`() = testApplication {
    configure()
    val response = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"reg_${System.currentTimeMillis()}@test.com","password":"123456"}""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = json.decodeFromString<TokenResponse>(response.bodyAsText())
    assertTrue(body.accessToken.isNotBlank())
    assertTrue(body.refreshToken.isNotBlank())
  }

  @Test
  fun `login with wrong password returns 401`() = testApplication {
    configure()
    val email = "login_${System.currentTimeMillis()}@test.com"
    client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val response = client.post("/api/auth/login") {
      contentType(ContentType.Application.Json)
      setBody("""{"email":"$email","password":"wrongpassword"}""")
    }
    assertEquals(HttpStatusCode.Unauthorized, response.status)
  }

  @Test
  fun `refresh with valid token returns new rotated pair`() = testApplication {
    configure()
    val email = "refresh_${System.currentTimeMillis()}@test.com"
    val registerResponse = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val original = json.decodeFromString<TokenResponse>(registerResponse.bodyAsText())

    val response = client.post("/api/auth/refresh") {
      contentType(ContentType.Application.Json)
      setBody("""{"refreshToken":"${original.refreshToken}"}""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val rotated = json.decodeFromString<TokenResponse>(response.bodyAsText())
    assertTrue(rotated.accessToken.isNotBlank())
    assertNotEquals(original.refreshToken, rotated.refreshToken)
  }

  @Test
  fun `GET users me returns current user profile`() = testApplication {
    configure()
    val email = "me_${System.currentTimeMillis()}@test.com"
    val registerResponse = client.post("/api/auth/register") {
      contentType(ContentType.Application.Json)
      setBody("""{"name":"Artem Obrazumov","email":"$email","password":"123456"}""")
    }
    val token = Json.parseToJsonElement(registerResponse.bodyAsText())
      .jsonObject["accessToken"]!!.jsonPrimitive.content

    val response = client.get("/api/users/me") {
      bearerAuth(token)
    }
    assertEquals(HttpStatusCode.OK, response.status)
    val user = json.decodeFromString<UserResponse>(response.bodyAsText())
    assertEquals(email, user.email)
    assertEquals("Artem Obrazumov", user.name)
  }
}
