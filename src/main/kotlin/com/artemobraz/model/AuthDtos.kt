package com.artemobraz.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenResponse(
  val accessToken: String,
  val refreshToken: String,
  val tokenType: String = "Bearer",
  val expiresIn: Long
)

@Serializable
data class UserResponse(val id: String, val email: String, val name: String, val role: String)
