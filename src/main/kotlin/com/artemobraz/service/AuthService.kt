package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.UserRepository
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.config.*
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class AuthService(
  config: ApplicationConfig,
  private val userRepository: UserRepository,
  private val redis: StatefulRedisConnection<String, String>
) {
  private val jwtSecret = config.property("jwt.secret").getString()
  private val jwtIssuer = config.property("jwt.issuer").getString()
  private val jwtAudience = config.property("jwt.audience").getString()
  private val accessTokenTtlMinutes = config.propertyOrNull("jwt.accessTokenTtlMinutes")?.getString()?.toLong() ?: 15L
  private val refreshTokenTtlDays = config.propertyOrNull("jwt.refreshTokenTtlDays")?.getString()?.toLong() ?: 30L
  private val accessTokenTtlSeconds = accessTokenTtlMinutes * 60

  suspend fun register(name: String, email: String, password: String): TokenResponse {
    if (userRepository.findByEmail(email) != null) {
      throw ConflictException("User with this email already exists")
    }
    val hash = BCrypt.hashpw(password, BCrypt.gensalt())
    val user = userRepository.create(name, email, hash)
    return generateTokenPair(user)
  }

  suspend fun login(email: String, password: String): TokenResponse {
    val user = userRepository.findByEmail(email)
      ?: throw AuthenticationException("Invalid email or password")
    if (!BCrypt.checkpw(password, user.passwordHash)) {
      throw AuthenticationException("Invalid email or password")
    }
    return generateTokenPair(user)
  }

  suspend fun refresh(refreshToken: String): TokenResponse {
    val commands = redis.async()
    val userId = commands.get("refresh:$refreshToken").await()
      ?: throw AuthenticationException("Invalid or expired refresh token")
    val user = userRepository.findById(UUID.fromString(userId))
      ?: throw NotFoundException("User not found")
    commands.del("refresh:$refreshToken").await()
    return generateTokenPair(user)
  }

  suspend fun logout(refreshToken: String) {
    redis.async().del("refresh:$refreshToken").await()
  }

  private suspend fun generateTokenPair(user: UserRow): TokenResponse {
    val expiresAt = Date(System.currentTimeMillis() + accessTokenTtlSeconds * 1000)
    val accessToken = JWT.create()
      .withAudience(jwtAudience)
      .withIssuer(jwtIssuer)
      .withClaim("userId", user.id.toString())
      .withClaim("email", user.email)
      .withClaim("role", user.role)
      .withExpiresAt(expiresAt)
      .sign(Algorithm.HMAC256(jwtSecret))

    val refreshToken = UUID.randomUUID().toString()
    val commands = redis.async()
    commands.set("refresh:$refreshToken", user.id.toString()).await()
    commands.expire("refresh:$refreshToken", refreshTokenTtlDays * 24 * 3600).await()

    return TokenResponse(
      accessToken = accessToken,
      refreshToken = refreshToken,
      expiresIn = accessTokenTtlSeconds
    )
  }
}
