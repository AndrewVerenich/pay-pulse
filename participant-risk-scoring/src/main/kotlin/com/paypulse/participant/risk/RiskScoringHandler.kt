package com.paypulse.participant.risk

import com.paypulse.saga.model.ReplyStatus
import com.paypulse.saga.model.SagaCommand
import com.paypulse.saga.model.SagaReply
import com.paypulse.saga.participant.handler.SagaCommandHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import kotlin.random.Random

@Component
class RiskScoringHandler(
  private val objectMapper: ObjectMapper,
  private val processedCommands: ProcessedCommandRepository,
) : SagaCommandHandler {

  private val log = LoggerFactory.getLogger(RiskScoringHandler::class.java)
  override val commandType = "RISK_SCORING"

  override fun handle(command: SagaCommand): Mono<SagaReply> =
    processedCommands.findReply(command.sagaId, command.stepName)
      .map { cached -> objectMapper.readValue(cached, SagaReply::class.java) }
      .switchIfEmpty(execute(command))

  override fun compensate(command: SagaCommand): Mono<SagaReply> {
    log.info("Risk scoring compensation sagaId={}", command.sagaId)
    return Mono.just(
      SagaReply(
        sagaId = command.sagaId,
        stepName = command.stepName,
        status = ReplyStatus.SUCCESS,
        isCompensation = true,
        payload = objectMapper.writeValueAsString(mapOf("compensated" to true)),
      ),
    )
  }

  private fun execute(command: SagaCommand): Mono<SagaReply> = Mono.defer {
    val score = Random.nextDouble(0.0, 0.3)
    val reply = SagaReply(
      sagaId = command.sagaId,
      stepName = command.stepName,
      status = ReplyStatus.SUCCESS,
      payload = objectMapper.writeValueAsString(mapOf("score" to score)),
    )
    val replyJson = objectMapper.writeValueAsString(reply)
    processedCommands.save(command.sagaId, command.stepName, replyJson)
      .thenReturn(reply)
      .doOnSuccess { log.info("Risk score sagaId={} score={}", command.sagaId, score) }
  }
}
