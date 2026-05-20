package com.artemobraz.plugins

import io.ktor.server.application.*
import io.ktor.util.*
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

private val RedisConnectionKey = AttributeKey<StatefulRedisConnection<String, String>>("RedisConnection")
private val RedisClientKey = AttributeKey<RedisClient>("RedisClient")

val Application.redis: StatefulRedisConnection<String, String>
  get() = attributes[RedisConnectionKey]

fun Application.configureRedis() {
  val url = environment.config.property("redis.url").getString()
  val client = RedisClient.create(url)
  val connection = client.connect()

  attributes.put(RedisClientKey, client)
  attributes.put(RedisConnectionKey, connection)

  monitor.subscribe(ApplicationStopped) {
    connection.close()
    client.shutdown()
  }
}
