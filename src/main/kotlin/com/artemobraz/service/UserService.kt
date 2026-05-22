package com.artemobraz.service

import com.artemobraz.model.NotFoundException
import com.artemobraz.model.UserResponse
import com.artemobraz.repository.UserRepository
import java.util.*

class UserService(private val userRepository: UserRepository) {

  suspend fun getMe(userId: UUID): UserResponse {
    val user = userRepository.findById(userId) ?: throw NotFoundException("User not found")
    return UserResponse(
      id = user.id.toString(),
      email = user.email,
      name = user.name,
      role = user.role
    )
  }
}
