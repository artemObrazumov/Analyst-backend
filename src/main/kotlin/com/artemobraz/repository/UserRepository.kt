package com.artemobraz.repository

import com.artemobraz.model.UserRow
import com.artemobraz.model.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class UserRepository {

  suspend fun findByEmail(email: String): UserRow? = newSuspendedTransaction {
    Users.selectAll()
      .where { Users.email eq email }
      .firstOrNull()
      ?.toUserRow()
  }

  suspend fun findById(id: UUID): UserRow? = newSuspendedTransaction {
    Users.selectAll()
      .where { Users.id eq id }
      .firstOrNull()
      ?.toUserRow()
  }

  suspend fun create(name: String, email: String, passwordHash: String): UserRow = newSuspendedTransaction {
    val insertedId = Users.insertAndGetId {
      it[Users.name] = name
      it[Users.email] = email
      it[Users.passwordHash] = passwordHash
    }
    UserRow(
      id = insertedId.value,
      email = email,
      name = name,
      passwordHash = passwordHash,
      role = "admin"
    )
  }

  private fun ResultRow.toUserRow() = UserRow(
    id = this[Users.id].value,
    email = this[Users.email],
    name = this[Users.name],
    passwordHash = this[Users.passwordHash],
    role = this[Users.role]
  )
}
