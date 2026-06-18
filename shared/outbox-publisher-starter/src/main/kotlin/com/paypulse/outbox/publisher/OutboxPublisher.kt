package com.paypulse.outbox.publisher

import com.paypulse.outbox.publisher.model.OutboxEvent
import com.paypulse.outbox.publisher.repository.WriteOutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

interface OutboxPublisher {
  fun publish(
    partitioningKey: String,
    eventType: String,
    payloadJson: String,
  ): Mono<OutboxEvent>
}

open class DefaultOutboxPublisher(
  private val repository: WriteOutboxRepository,
) : OutboxPublisher {

  private val log = LoggerFactory.getLogger(DefaultOutboxPublisher::class.java)

  @Transactional(propagation = Propagation.MANDATORY)
  override fun publish(
    partitioningKey: String,
    eventType: String,
    payloadJson: String,
  ): Mono<OutboxEvent> {
    log.debug("Outbox publish type={} key={}", eventType, partitioningKey)
    return repository.saveWithJsonb(partitioningKey, eventType, payloadJson)
      .flatMap { saved ->
        val id = saved.id
        if (id == null) {
          Mono.just(saved)
        } else {
          repository.deleteById(id).thenReturn(saved)
        }
      }
  }
}
