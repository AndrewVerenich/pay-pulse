package com.paypulse.projection.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties.AckMode
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
class ProjectionConfiguration {

  @Bean
  fun paypulseDltMessagesCounter(meterRegistry: MeterRegistry): Counter =
    Counter.builder("paypulse_dlt_messages_total")
      .tag("topic", "_none")
      .description("Dead-letter publishes from projection-balance")
      .register(meterRegistry)

  @Bean
  fun deadLetterPublishingRecoverer(
    template: KafkaTemplate<String, String>,
    meterRegistry: MeterRegistry,
  ): DeadLetterPublishingRecoverer =
    object : DeadLetterPublishingRecoverer(
      template,
      { record, _ -> TopicPartition(record.topic() + ".DLT", record.partition()) },
    ) {
      override fun accept(record: ConsumerRecord<*, *>, exception: Exception) {
        meterRegistry.counter("paypulse_dlt_messages_total", "topic", record.topic()).increment()
        super.accept(record, exception)
      }
    }

  @Bean
  fun paymentEventsErrorHandler(recoverer: DeadLetterPublishingRecoverer): DefaultErrorHandler {
    val backOff = FixedBackOff(RETRY_INTERVAL_MS, RETRY_ATTEMPTS)
    return DefaultErrorHandler(recoverer, backOff)
  }

  @Bean(name = ["paymentEventsListenerContainerFactory"])
  fun paymentEventsListenerContainerFactory(
    consumerFactory: ConsumerFactory<String, String>,
    errorHandler: DefaultErrorHandler,
  ): ConcurrentKafkaListenerContainerFactory<String, String> {
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.consumerFactory = consumerFactory
    factory.containerProperties.ackMode = AckMode.MANUAL_IMMEDIATE
    factory.setCommonErrorHandler(errorHandler)
    return factory
  }

  private companion object {
    const val RETRY_INTERVAL_MS = 1_000L
    const val RETRY_ATTEMPTS = 3L
  }
}
