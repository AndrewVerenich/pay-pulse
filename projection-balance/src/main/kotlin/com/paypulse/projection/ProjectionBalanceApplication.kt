package com.paypulse.projection

import com.paypulse.projection.config.KafkaPaymentTopicProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@EnableKafka
@EnableConfigurationProperties(KafkaPaymentTopicProperties::class)
class ProjectionBalanceApplication

fun main(args: Array<String>) {
  runApplication<ProjectionBalanceApplication>(*args)
}
