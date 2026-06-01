package com.artemobraz.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureAuth() {
  val config = environment.config
  val accessSecret = config.property("jwt.accessSecret").getString()
  val issuer = config.property("jwt.issuer").getString()
  val audience = config.property("jwt.audience").getString()
  val realm = config.property("jwt.realm").getString()

  install(Authentication) {
    jwt("admin-jwt") {
      this.realm = realm
      verifier(
        JWT.require(Algorithm.HMAC256(accessSecret))
          .withAudience(audience)
          .withIssuer(issuer)
          .build()
      )
      validate { credential ->
        val userId = credential.payload.getClaim("userId").asString()
        if (userId != null) JWTPrincipal(credential.payload) else null
      }
    }
  }
}
