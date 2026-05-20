package com.artemobraz

import org.flywaydb.core.Flyway

fun main() {
  val url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/analytics"
  val user = System.getenv("DB_USER") ?: "analytics"
  val password = System.getenv("DB_PASSWORD") ?: "secret"

  val result = Flyway.configure()
    .dataSource(url, user, password)
    .locations("classpath:db/migrations")
    .load()
    .migrate()

  println("Applied ${result.migrationsExecuted} migration(s). Target: ${result.targetSchemaVersion ?: "latest"}")
}
