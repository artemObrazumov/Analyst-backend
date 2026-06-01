package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.RevokedRefreshTokenRepository
import com.artemobraz.repository.UserRepository
import com.artemobraz.utils.sha256
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.config.*
import java.util.*

class AuthService(
  config: ApplicationConfig,
  private val userRepository: UserRepository,
  private val revokedRefreshTokenRepository: RevokedRefreshTokenRepository
) {
  private val accessSecret = config.property("jwt.accessSecret").getString()
  private val refreshSecret = config.property("jwt.refreshSecret").getString()
  private val jwtIssuer = config.property("jwt.issuer").getString()
  private val jwtAudience = config.property("jwt.audience").getString()
  private val accessTokenTtlMinutes = config.propertyOrNull("jwt.accessTokenTtlMinutes")?.getString()?.toLong() ?: 15L
  private val refreshTokenTtlDays = config.propertyOrNull("jwt.refreshTokenTtlDays")?.getString()?.toLong() ?: 30L
  private val accessTokenTtlSeconds = accessTokenTtlMinutes * 60

  suspend fun register(name: String, email: String, password: String): TokenResponse {
    if (userRepository.findByEmail(email) != null) throw ConflictException("User with this email already exists")
    val hash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
    val user = userRepository.create(name, email, hash)
    return generateTokenPair(user)
  }

  suspend fun login(email: String, password: String): TokenResponse {
    val user = userRepository.findByEmail(email) ?: throw AuthenticationException("Invalid email or password")
    if (!org.mindrot.jbcrypt.BCrypt.checkpw(password, user.passwordHash)) {
      throw AuthenticationException("Invalid email or password")
    }
    return generateTokenPair(user)
  }

  suspend fun refresh(refreshToken: String): TokenResponse {
    val decoded = verifyRefreshJwt(refreshToken)
    val jti = decoded.id ?: throw AuthenticationException("Invalid refresh token")
    val tokenHash = sha256(jti)
    if (revokedRefreshTokenRepository.isRevoked(tokenHash)) {
      throw AuthenticationException("Invalid or expired refresh token")
    }
    val userId = decoded.subject ?: throw AuthenticationException("Invalid refresh token")
    revokedRefreshTokenRepository.revoke(tokenHash, UUID.fromString(userId), decoded.expiresAt)
    val user = userRepository.findById(UUID.fromString(userId)) ?: throw NotFoundException("User not found")
    return generateTokenPair(user)
  }

  suspend fun logout(refreshToken: String) {
    val decoded = verifyRefreshJwt(refreshToken)
    val jti = decoded.id ?: throw AuthenticationException("Invalid refresh token")
    val userId = decoded.subject ?: throw AuthenticationException("Invalid refresh token")
    revokedRefreshTokenRepository.revoke(sha256(jti), UUID.fromString(userId), decoded.expiresAt)
  }

  private fun verifyRefreshJwt(token: String): DecodedJWT {
    return try {
      JWT.require(Algorithm.HMAC256(refreshSecret)).withIssuer(jwtIssuer).build().verify(token)
    } catch (e: Exception) {
      throw AuthenticationException("Invalid or expired refresh token")
    }
  }

  private suspend fun generateTokenPair(user: UserRow): TokenResponse {
    val now = System.currentTimeMillis()
    val accessJti = UUID.randomUUID().toString()
    val accessExpiresAt = Date(now + accessTokenTtlSeconds * 1000)
    val refreshExpiresAt = Date(now + refreshTokenTtlDays * 24 * 3600 * 1000)
    val refreshJti = UUID.randomUUID().toString()

    val accessToken = JWT.create()
      .withJWTId(accessJti)
      .withAudience(jwtAudience)
      .withIssuer(jwtIssuer)
      .withClaim("userId", user.id.toString())
      .withClaim("email", user.email)
      .withClaim("role", user.role)
      .withExpiresAt(accessExpiresAt)
      .sign(Algorithm.HMAC256(accessSecret))

    val refreshToken = JWT.create()
      .withIssuer(jwtIssuer)
      .withSubject(user.id.toString())
      .withJWTId(refreshJti)
      .withExpiresAt(refreshExpiresAt)
      .sign(Algorithm.HMAC256(refreshSecret))

    return TokenResponse(accessToken = accessToken, refreshToken = refreshToken, expiresIn = accessTokenTtlSeconds)
  }
}
