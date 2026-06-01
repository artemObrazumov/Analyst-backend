package com.artemobraz.repository

import com.artemobraz.model.RevokedRefreshTokens
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class RevokedRefreshTokenRepository {

  suspend fun isRevoked(tokenHash: String): Boolean = newSuspendedTransaction {
    RevokedRefreshTokens.selectAll()
      .where { RevokedRefreshTokens.tokenHash eq tokenHash }
      .empty().not()
  }

  suspend fun revoke(tokenHash: String, userId: UUID, expiresAt: Date?) {
    if (expiresAt == null) return
    newSuspendedTransaction {
      val exists = RevokedRefreshTokens.selectAll()
        .where { RevokedRefreshTokens.tokenHash eq tokenHash }
        .empty()
        .not()
      if (!exists) {
        RevokedRefreshTokens.insert {
          it[RevokedRefreshTokens.tokenHash] = tokenHash
          it[RevokedRefreshTokens.userId] = userId
          it[RevokedRefreshTokens.revokedAt] = Clock.System.now()
          it[RevokedRefreshTokens.expiresAt] =
            kotlinx.datetime.Instant.fromEpochMilliseconds(expiresAt.time)
        }
      }
    }
  }
}
