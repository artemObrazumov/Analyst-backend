package com.artemobraz.routing

import com.artemobraz.service.UserService
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.userRoutes(userService: UserService) {
  route("/users") {
    authenticate("admin-jwt") {
      get("/me") {
        val userId = UUID.fromString(
          call.principal<JWTPrincipal>()!!.payload.getClaim("userId").asString()
        )
        call.respond(userService.getMe(userId))
      }
    }
  }
}
