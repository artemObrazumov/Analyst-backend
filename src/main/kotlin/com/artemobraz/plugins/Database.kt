package com.artemobraz.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabase() {
  val config = environment.config

  val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = config.property("database.url").getString()
    driverClassName = "org.postgresql.Driver"
    username = config.property("database.user").getString()
    password = config.property("database.password").getString()
    maximumPoolSize = config.propertyOrNull("database.pool.maximumPoolSize")?.getString()?.toInt() ?: 10
    minimumIdle = config.propertyOrNull("database.pool.minimumIdle")?.getString()?.toInt() ?: 2
    isAutoCommit = false
    transactionIsolation = "TRANSACTION_REPEATABLE_READ"
    validate()
  })

  Flyway.configure()
    .dataSource(dataSource)
    .locations("classpath:db/migrations")
    .load()
    .migrate()

  Database.connect(dataSource)

  monitor.subscribe(ApplicationStopped) { dataSource.close() }
}
