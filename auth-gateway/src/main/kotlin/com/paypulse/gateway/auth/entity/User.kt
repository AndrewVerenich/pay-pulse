package com.paypulse.gateway.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table(value = "users", schema = "auth")
data class User(
  @Id
  val id: Long? = null,
  val username: String,
  val password: String,
  val email: String,
  val roles: String,
  @Column("created_at")
  val createdAt: OffsetDateTime? = null,
)
