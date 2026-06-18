package com.paypulse.projection.config

import org.apache.kafka.common.TopicPartition
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties.AckMode
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.util.backoff.FixedBackOff
import javax.sql.DataSource

@Configuration
class ProjectionConfiguration {

  @Bean
  fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
    DataSourceTransactionManager(dataSource)

  @Bean
  fun deadLetterPublishingRecoverer(template: KafkaTemplate<String, String>): DeadLetterPublishingRecoverer =
    DeadLetterPublishingRecoverer(template) { record, _ ->
      TopicPartition(record.topic() + ".DLT", record.partition())
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
