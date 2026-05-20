package com.artemobraz

import com.artemobraz.plugins.*
import io.ktor.server.application.*

fun Application.module() {
    configureSerialization()
    configureCors()
    configureStatusPages()
    configureValidation()
    configureAuth()
    configureMetrics()
    configureDatabase()
    configureRedis()
    configureSwagger()
    configureRouting()
}
