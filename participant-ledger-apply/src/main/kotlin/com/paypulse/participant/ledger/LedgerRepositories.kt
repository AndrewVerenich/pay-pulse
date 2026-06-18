package com.paypulse.participant.ledger

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

@Table(value = "event_store", schema = "payment_command")
data class EventStoreRow(
  @Id val id: Long? = null,
  @Column("aggregate_id") val aggregateId: UUID,
  @Column("aggregate_type") val aggregateType: String,
  @Column("event_type") val eventType: String,
  val version: Int,
  @Column("account_id") val accountId: String,
  val payload: String,
  @Column("occurred_at") val occurredAt: OffsetDateTime,
)

interface EventStoreRepository : ReactiveCrudRepository<EventStoreRow, Long> {
  @Query(
    """
    SELECT COALESCE(MAX(version), 0)
      FROM payment_command.event_store
     WHERE aggregate_id = :aggregateId
    """,
  )
  fun findMaxVersionByAggregateId(aggregateId: UUID): Mono<Int>
}

interface ProcessedCommandRepository : ReactiveCrudRepository<ProcessedCommandRow, String> {

  @Query(
    """
    SELECT reply_payload FROM participant_ledger.processed_commands
    WHERE saga_id = :sagaId AND step_name = :stepName
    """,
  )
  fun findReply(sagaId: UUID, stepName: String): Mono<String>

  @Query(
    """
    INSERT INTO participant_ledger.processed_commands (saga_id, step_name, reply_payload)
    VALUES (:sagaId, :stepName, :replyPayload)
    ON CONFLICT (saga_id, step_name) DO NOTHING
    """,
  )
  fun save(sagaId: UUID, stepName: String, replyPayload: String): Mono<Void>
}

data class ProcessedCommandRow(
  val sagaId: UUID,
  val stepName: String,
  val replyPayload: String,
)
