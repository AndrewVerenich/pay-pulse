package com.paypulse.outbox.publisher

import com.paypulse.outbox.publisher.repository.WriteOutboxRepository
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration
class OutboxPublisherAutoConfiguration(
  private val writeOutboxRepository: WriteOutboxRepository,
) {

  @Bean
  fun outboxPublisher(): OutboxPublisher = DefaultOutboxPublisher(writeOutboxRepository)
}
