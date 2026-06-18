package com.paypulse.participant.fraud

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import java.util.UUID

interface ProcessedCommandRepository : ReactiveCrudRepository<ProcessedCommandRow, String> {

  @Query(
    """
    SELECT reply_payload FROM participant_fraud.processed_commands
    WHERE saga_id = :sagaId AND step_name = :stepName
    """,
  )
  fun findReply(sagaId: UUID, stepName: String): Mono<String>

  @Query(
    """
    INSERT INTO participant_fraud.processed_commands (saga_id, step_name, reply_payload)
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
