package com.paypulse.projection.consumer

import com.paypulse.projection.service.BalanceProjectionService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

interface PaymentEventsListener {
  fun onPaymentEvent(record: ConsumerRecord<String, String>, ack: Acknowledgment)
}

@Component
class DefaultPaymentEventsListener(
  private val balanceProjectionService: BalanceProjectionService,
) : PaymentEventsListener {
  private val log = LoggerFactory.getLogger(DefaultPaymentEventsListener::class.java)

  @KafkaListener(
    topics = ["\${paypulse.kafka.payment-events-topic}"],
    groupId = "\${paypulse.kafka.consumer-group:projection-balance}",
    containerFactory = "paymentEventsListenerContainerFactory",
  )
  override fun onPaymentEvent(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
    log.debug("Received payment event partition={} offset={} key={}", record.partition(), record.offset(), record.key())
    balanceProjectionService.handlePaymentEvent(record.value()).block()
    ack.acknowledge()
  }
}
