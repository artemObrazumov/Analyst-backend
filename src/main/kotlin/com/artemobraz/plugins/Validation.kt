package com.artemobraz.plugins

import com.artemobraz.model.LoginRequest
import com.artemobraz.model.RegisterRequest
import io.ktor.server.application.*
import io.ktor.server.plugins.requestvalidation.*

fun Application.configureValidation() {
  install(RequestValidation) {
    validate<LoginRequest> { req ->
      val errors = buildList {
        if (req.email.isBlank()) add("Email is required")
        if (req.password.isBlank()) add("Password is required")
      }
      if (errors.isEmpty()) ValidationResult.Valid
      else ValidationResult.Invalid(errors)
    }
    validate<RegisterRequest> { req ->
      val errors = buildList {
        if (req.name.isBlank()) add("Name is required")
        if (req.email.isBlank()) add("Email is required")
        if (!req.email.contains("@")) add("Email is invalid")
        if (req.password.length < 6) add("Password must be at least 6 characters")
      }
      if (errors.isEmpty()) ValidationResult.Valid
      else ValidationResult.Invalid(errors)
    }
  }
}
