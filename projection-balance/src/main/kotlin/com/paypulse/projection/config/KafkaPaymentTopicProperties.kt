package com.paypulse.projection.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "paypulse.kafka")
data class KafkaPaymentTopicProperties(
  val paymentEventsTopic: String = "payment.events",
)
