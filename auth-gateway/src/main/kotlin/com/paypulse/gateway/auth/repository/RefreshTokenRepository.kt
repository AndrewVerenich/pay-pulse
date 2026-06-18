package com.paypulse.gateway.auth.repository

import com.paypulse.gateway.auth.entity.RefreshToken
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface RefreshTokenRepository : ReactiveCrudRepository<RefreshToken, Long> {

  fun findByToken(token: String): Mono<RefreshToken>

  @Modifying
  @Query("UPDATE auth.refresh_tokens SET status = :status WHERE id = :id")
  fun updateStatus(id: Long, status: String): Mono<Int>

  @Modifying
  @Query(
    """
    UPDATE auth.refresh_tokens
       SET status = 'REVOKED'
     WHERE user_id = :userId
       AND status = 'ACTIVE'
    """,
  )
  fun revokeAllForUser(userId: Long): Mono<Int>

  @Modifying
  @Query(
    """
    UPDATE auth.refresh_tokens
       SET status = 'REVOKED'
     WHERE user_id = :userId
       AND family  = :family
       AND status IN ('ACTIVE','USED')
    """,
  )
  fun revokeFamily(userId: Long, family: String): Mono<Int>
}
