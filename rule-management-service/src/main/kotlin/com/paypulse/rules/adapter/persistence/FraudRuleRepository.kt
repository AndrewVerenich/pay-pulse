package com.paypulse.rules.adapter.persistence

import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

interface FraudRuleRepository : ReactiveCrudRepository<FraudRuleRow, UUID> {

  @Query(
    """
    SELECT id, name, enabled, json_spec::text AS json_spec, version, updated_at
    FROM rule_management.fraud_rule
    ORDER BY name
    """,
  )
  fun findAllRules(): Flux<FraudRuleRow>

  @Query(
    """
    SELECT id, name, enabled, json_spec::text AS json_spec, version, updated_at
    FROM rule_management.fraud_rule
    WHERE id = :id
    """,
  )
  fun findRuleById(id: UUID): Mono<FraudRuleRow>

  @Query(
    """
    INSERT INTO rule_management.fraud_rule (name, enabled, json_spec)
    VALUES (:name, :enabled, CAST(:jsonSpec AS jsonb))
    RETURNING id, name, enabled, json_spec::text AS json_spec, version, updated_at
    """,
  )
  fun insertRule(name: String, enabled: Boolean, jsonSpec: String): Mono<FraudRuleRow>

  @Query(
    """
    UPDATE rule_management.fraud_rule
    SET name = :name,
        enabled = :enabled,
        json_spec = CAST(:jsonSpec AS jsonb),
        version = version + 1,
        updated_at = timezone('utc', now())
    WHERE id = :id
    RETURNING id, name, enabled, json_spec::text AS json_spec, version, updated_at
    """,
  )
  fun updateRule(id: UUID, name: String, enabled: Boolean, jsonSpec: String): Mono<FraudRuleRow>

  @Modifying
  @Query("DELETE FROM rule_management.fraud_rule WHERE id = :id")
  fun deleteRuleById(id: UUID): Mono<Long>
}
