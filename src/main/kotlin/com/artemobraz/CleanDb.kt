package com.artemobraz

import io.lettuce.core.RedisClient
import org.flywaydb.core.Flyway
import java.sql.DriverManager

/**
 * Drops all PostgreSQL data, re-runs Flyway migrations, flushes Redis.
 *
 * Run: ./gradlew dbClean
 *
 * Env: DB_URL, DB_USER, DB_PASSWORD, REDIS_URL (same defaults as application.yaml)
 */
fun main() {
  val url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/analytics"
  val user = System.getenv("DB_USER") ?: "analytics"
  val password = System.getenv("DB_PASSWORD") ?: "secret"
  val redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379"

  DriverManager.getConnection(url, user, password).use { conn ->
    conn.autoCommit = true
    conn.createStatement().use { st ->
      st.execute("DROP SCHEMA public CASCADE")
      st.execute("CREATE SCHEMA public")
      st.execute("GRANT ALL ON SCHEMA public TO $user")
      st.execute("GRANT ALL ON SCHEMA public TO public")
    }
  }
  println("PostgreSQL: schema public reset")

  val result = Flyway.configure()
    .dataSource(url, user, password)
    .locations("classpath:db/migrations")
    .load()
    .migrate()
  println("Flyway: applied ${result.migrationsExecuted} migration(s)")

  RedisClient.create(redisUrl).connect().use { connection ->
    connection.sync().flushall()
    println("Redis: all keys removed")
  }

  println("Done — database and Redis are empty.")
}
