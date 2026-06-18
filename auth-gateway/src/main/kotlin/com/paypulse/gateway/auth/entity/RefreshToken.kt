package com.paypulse.gateway.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table(value = "refresh_tokens", schema = "auth")
data class RefreshToken(
  @Id
  val id: Long? = null,
  val token: String,
  @Column("user_id")
  val userId: Long,
  val fingerprint: String,
  val family: String = UUID.randomUUID().toString(),
  val status: String = RefreshTokenStatus.ACTIVE.name,
  @Column("expires_at")
  val expiresAt: OffsetDateTime,
  @Column("created_at")
  val createdAt: OffsetDateTime? = null,
)

enum class RefreshTokenStatus {
  ACTIVE,
  USED,
  REVOKED,
}
