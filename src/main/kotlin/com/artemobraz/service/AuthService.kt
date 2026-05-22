package com.artemobraz.service

import com.artemobraz.model.*
import com.artemobraz.repository.UserRepository
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
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
  private val accessSecret = config.property("jwt.accessSecret").getString()
  private val refreshSecret = config.property("jwt.refreshSecret").getString()
  private val jwtIssuer = config.property("jwt.issuer").getString()
  private val jwtAudience = config.property("jwt.audience").getString()
  private val accessTokenTtlMinutes = config.propertyOrNull("jwt.accessTokenTtlMinutes")?.getString()?.toLong() ?: 15L
  private val refreshTokenTtlDays = config.propertyOrNull("jwt.refreshTokenTtlDays")?.getString()?.toLong() ?: 30L
  private val accessTokenTtlSeconds = accessTokenTtlMinutes * 60

  suspend fun register(name: String, email: String, password: String): TokenResponse {
    if (userRepository.findByEmail(email) != null) throw ConflictException("User with this email already exists")
    val hash = BCrypt.hashpw(password, BCrypt.gensalt())
    val user = userRepository.create(name, email, hash)
    return generateTokenPair(user)
  }

  suspend fun login(email: String, password: String): TokenResponse {
    val user = userRepository.findByEmail(email) ?: throw AuthenticationException("Invalid email or password")
    if (!BCrypt.checkpw(password, user.passwordHash)) throw AuthenticationException("Invalid email or password")
    return generateTokenPair(user)
  }

  suspend fun refresh(refreshToken: String): TokenResponse {
    val decoded = verifyRefreshJwt(refreshToken)
    val jti = decoded.id ?: throw AuthenticationException("Invalid refresh token")
    val cmd = redis.async()
    val userId = cmd.get("refresh:$jti").await() ?: throw AuthenticationException("Invalid or expired refresh token")
    cmd.del("refresh:$jti").await()
    val user = userRepository.findById(UUID.fromString(userId)) ?: throw NotFoundException("User not found")
    return generateTokenPair(user)
  }

  suspend fun logout(refreshToken: String) {
    val decoded = verifyRefreshJwt(refreshToken)
    val jti = decoded.id ?: throw AuthenticationException("Invalid refresh token")
    revokeRefreshJti(jti, decoded.expiresAt)
  }

  suspend fun logout(refreshToken: String, accessJti: String, accessExp: Date) {
    val decoded = verifyRefreshJwt(refreshToken)
    val jti = decoded.id ?: throw AuthenticationException("Invalid refresh token")
    revokeRefreshJti(jti, decoded.expiresAt)
    val accessRemainingSeconds = maxOf(0L, (accessExp.time - System.currentTimeMillis()) / 1000)
    if (accessRemainingSeconds > 0) {
      redis.async().setex("revoked:access:$accessJti", accessRemainingSeconds, "1").await()
    }
  }

  private suspend fun revokeRefreshJti(jti: String, expiresAt: Date?) {
    val cmd = redis.async()
    cmd.del("refresh:$jti").await()
    if (expiresAt != null) {
      val remaining = maxOf(0L, (expiresAt.time - System.currentTimeMillis()) / 1000)
      if (remaining > 0) cmd.setex("revoked:refresh:$jti", remaining, "1").await()
    }
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

    val cmd = redis.async()
    cmd.set("refresh:$refreshJti", user.id.toString()).await()
    cmd.expire("refresh:$refreshJti", refreshTokenTtlDays * 24 * 3600).await()

    return TokenResponse(accessToken = accessToken, refreshToken = refreshToken, expiresIn = accessTokenTtlSeconds)
  }
}
