package com.artemobraz.repository

import com.artemobraz.model.ApiKeyRow
import com.artemobraz.model.ApiKeys
import com.artemobraz.model.ProjectRow
import com.artemobraz.model.Projects
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class ProjectRepository {

  suspend fun findAllByOwner(ownerId: UUID): List<ProjectRow> = newSuspendedTransaction {
    Projects.selectAll()
      .where { Projects.ownerId eq ownerId }
      .map { it.toProjectRow() }
  }

  suspend fun findById(id: UUID): ProjectRow? = newSuspendedTransaction {
    Projects.selectAll()
      .where { Projects.id eq id }
      .firstOrNull()
      ?.toProjectRow()
  }

  suspend fun create(ownerId: UUID, name: String, description: String?): ProjectRow = newSuspendedTransaction {
    val insertedId = Projects.insertAndGetId {
      it[Projects.ownerId] = ownerId
      it[Projects.name] = name
      it[Projects.description] = description
    }
    Projects.selectAll().where { Projects.id eq insertedId }.first().toProjectRow()
  }

  suspend fun update(id: UUID, name: String?, description: String?): ProjectRow = newSuspendedTransaction {
    Projects.update({ Projects.id eq id }) {
      if (name != null) it[Projects.name] = name
      if (description != null) it[Projects.description] = description
      it[Projects.updatedAt] = Clock.System.now()
    }
    Projects.selectAll().where { Projects.id eq id }.first().toProjectRow()
  }

  suspend fun delete(id: UUID) = newSuspendedTransaction {
    Projects.deleteWhere { Projects.id eq id }
  }

  suspend fun getActiveKey(projectId: UUID): ApiKeyRow? = newSuspendedTransaction {
    ApiKeys.selectAll()
      .where { (ApiKeys.projectId eq projectId) and (ApiKeys.isActive eq true) }
      .firstOrNull()
      ?.toApiKeyRow()
  }

  suspend fun createKey(projectId: UUID, keyHash: String, label: String?): ApiKeyRow = newSuspendedTransaction {
    val insertedId = ApiKeys.insertAndGetId {
      it[ApiKeys.projectId] = projectId
      it[ApiKeys.keyHash] = keyHash
      it[ApiKeys.label] = label
    }
    ApiKeys.selectAll().where { ApiKeys.id eq insertedId }.first().toApiKeyRow()
  }

  suspend fun revokeActiveKey(projectId: UUID) = newSuspendedTransaction {
    ApiKeys.update({ (ApiKeys.projectId eq projectId) and (ApiKeys.isActive eq true) }) {
      it[isActive] = false
      it[revokedAt] = Clock.System.now()
    }
  }

  private fun ResultRow.toProjectRow() = ProjectRow(
    id = this[Projects.id].value,
    ownerId = this[Projects.ownerId].value,
    name = this[Projects.name],
    description = this[Projects.description],
    createdAt = this[Projects.createdAt],
    updatedAt = this[Projects.updatedAt]
  )

  private fun ResultRow.toApiKeyRow() = ApiKeyRow(
    id = this[ApiKeys.id].value,
    projectId = this[ApiKeys.projectId].value,
    keyHash = this[ApiKeys.keyHash],
    label = this[ApiKeys.label],
    isActive = this[ApiKeys.isActive],
    createdAt = this[ApiKeys.createdAt],
    revokedAt = this[ApiKeys.revokedAt]
  )
}
