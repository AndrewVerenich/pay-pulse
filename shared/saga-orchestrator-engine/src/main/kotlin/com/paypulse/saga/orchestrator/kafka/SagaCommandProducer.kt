package com.paypulse.saga.orchestrator.kafka

import com.paypulse.saga.model.SagaCommand
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SagaCommandProducer(
  private val kafkaTemplate: KafkaTemplate<String, SagaCommand>
) {
  private val log = LoggerFactory.getLogger(SagaCommandProducer::class.java)

  fun sendCommand(participant: String, command: SagaCommand): Mono<Void> {
    val topic = "saga.commands.$participant"
    log.info("Sending command to topic={} sagaId={} step={} compensation={}",
      topic, command.sagaId, command.stepName, command.isCompensation)

    return Mono.fromFuture(kafkaTemplate.send(topic, command.sagaId.toString(), command))
      .doOnError { e -> log.error("Failed to send command to {}: {}", topic, e.message) }
      .then()
  }
}
