package com.artemobraz.routing

import com.artemobraz.model.LoginRequest
import com.artemobraz.model.RefreshRequest
import com.artemobraz.model.RegisterRequest
import com.artemobraz.service.AuthService
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
  route("/auth") {
    post("/register") {
      val body = call.receive<RegisterRequest>()
      call.respond(authService.register(body.name, body.email, body.password))
    }

    post("/login") {
      val body = call.receive<LoginRequest>()
      call.respond(authService.login(body.email, body.password))
    }

    post("/refresh") {
      val body = call.receive<RefreshRequest>()
      call.respond(authService.refresh(body.refreshToken))
    }

    post("/logout") {
      val body = call.receive<RefreshRequest>()
      authService.logout(body.refreshToken)
      call.respond(mapOf("message" to "Logged out"))
    }
  }
}
