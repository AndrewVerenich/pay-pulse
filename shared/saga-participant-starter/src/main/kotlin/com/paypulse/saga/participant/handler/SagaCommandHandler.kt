package com.paypulse.saga.participant.handler

import com.paypulse.saga.model.SagaCommand
import com.paypulse.saga.model.SagaReply
import reactor.core.publisher.Mono

interface SagaCommandHandler {
  val commandType: String

  fun handle(command: SagaCommand): Mono<SagaReply>

  fun compensate(command: SagaCommand): Mono<SagaReply> =
    Mono.error(UnsupportedOperationException("Compensation not supported for commandType=$commandType"))
}
