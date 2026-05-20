package com.artemobraz

import com.artemobraz.com.artemobraz.configureRouting
import com.artemobraz.plugins.*
import io.ktor.server.application.*

fun Application.module() {
  configureSerialization()
  configureCors()
  configureStatusPages()
  configureAuth()
  configureMetrics()
  configureDatabase()
  configureRedis()
  configureSwagger()
  configureRouting()
}
