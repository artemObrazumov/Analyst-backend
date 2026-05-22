package com.artemobraz

import com.artemobraz.plugins.*
import io.ktor.server.application.*

fun Application.module() {
    configureSerialization()
    configureCors()
    configureStatusPages()
    configureValidation()
    configureMetrics()
    configureDatabase()
    configureRedis()
    configureAuth()
    configureSwagger()
    configureRouting()
}
