package com.paypulse.participant.fraud

import com.paypulse.saga.model.ReplyStatus
import com.paypulse.saga.model.SagaCommand
import com.paypulse.saga.model.SagaReply
import com.paypulse.saga.participant.handler.SagaCommandHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal

@Component
class FraudCheckHandler(
  private val objectMapper: ObjectMapper,
  private val processedCommands: ProcessedCommandRepository,
) : SagaCommandHandler {

  private val log = LoggerFactory.getLogger(FraudCheckHandler::class.java)
  override val commandType = "FRAUD_CHECK"

  override fun handle(command: SagaCommand): Mono<SagaReply> =
    processedCommands.findReply(command.sagaId, command.stepName)
    .map { cached ->
      objectMapper.readValue(cached, SagaReply::class.java)
    }
    .switchIfEmpty(execute(command))

  override fun compensate(command: SagaCommand): Mono<SagaReply> {
    log.info("Fraud check compensation no-op sagaId={}", command.sagaId)
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
    val payload = objectMapper.readTree(command.payload)
    val amount = BigDecimal(payload.get("amount").asText())
    val score = if (amount > BigDecimal("1000")) 0.9 else 0.05
    val reply = SagaReply(
      sagaId = command.sagaId,
      stepName = command.stepName,
      status = ReplyStatus.SUCCESS,
      payload = objectMapper.writeValueAsString(mapOf("score" to score)),
    )
    val replyJson = objectMapper.writeValueAsString(reply)
    processedCommands.save(command.sagaId, command.stepName, replyJson)
      .thenReturn(reply)
      .doOnSuccess { log.info("Fraud check sagaId={} score={}", command.sagaId, score) }
  }
}
