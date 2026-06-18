package com.paypulse.participant.notification

import com.paypulse.saga.model.ReplyStatus
import com.paypulse.saga.model.SagaCommand
import com.paypulse.saga.model.SagaReply
import com.paypulse.saga.participant.handler.SagaCommandHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class NotificationHandler(
  private val objectMapper: ObjectMapper,
  private val processedCommands: ProcessedCommandRepository,
) : SagaCommandHandler {

  private val log = LoggerFactory.getLogger(NotificationHandler::class.java)
  override val commandType = "NOTIFY"

  override fun handle(command: SagaCommand): Mono<SagaReply> =
    processedCommands.findReply(command.sagaId, command.stepName)
      .map { cached -> objectMapper.readValue(cached, SagaReply::class.java) }
      .switchIfEmpty(execute(command))

  override fun compensate(command: SagaCommand): Mono<SagaReply> =
    Mono.just(
      SagaReply(
        sagaId = command.sagaId,
        stepName = command.stepName,
        status = ReplyStatus.SUCCESS,
        isCompensation = true,
      ),
    )

  private fun execute(command: SagaCommand): Mono<SagaReply> = Mono.defer {
    val notificationId = UUID.randomUUID().toString()
    log.info("Payment notification sent sagaId={} notificationId={}", command.sagaId, notificationId)
    val reply = SagaReply(
      sagaId = command.sagaId,
      stepName = command.stepName,
      status = ReplyStatus.SUCCESS,
      payload = objectMapper.writeValueAsString(mapOf("notificationId" to notificationId)),
    )
    val replyJson = objectMapper.writeValueAsString(reply)
    processedCommands.save(command.sagaId, command.stepName, replyJson).thenReturn(reply)
  }
}
