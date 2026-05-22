package com.artemobraz.routing

import com.artemobraz.model.RefreshRequest
import com.artemobraz.service.AuthService
import com.artemobraz.service.UserService
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.userRoutes(userService: UserService, authService: AuthService) {
  route("/users") {
    authenticate("admin-jwt") {
      get("/me") {
        val userId = UUID.fromString(
          call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
        )
        call.respond(userService.getMe(userId))
      }

      post("/me/logout") {
        val principal = call.principal<JWTPrincipal>()!!
        val accessJti = principal.payload.id!!
        val accessExp = principal.payload.expiresAt
        val body = call.receive<RefreshRequest>()
        authService.logout(body.refreshToken, accessJti, accessExp)
        call.respond(mapOf("message" to "Logged out"))
      }
    }
  }
}
