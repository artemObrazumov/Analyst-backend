package com.artemobraz.model

class ConflictException(message: String) : RuntimeException(message)
class AuthenticationException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : RuntimeException(message)
class ForbiddenException(message: String) : RuntimeException(message)
