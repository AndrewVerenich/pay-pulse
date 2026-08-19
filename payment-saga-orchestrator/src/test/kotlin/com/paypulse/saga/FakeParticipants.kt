package com.paypulse.saga

import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.saga.model.ReplyStatus
import com.paypulse.saga.model.SagaCommand
import com.paypulse.saga.model.SagaReply
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-process заглушки четырёх participant-сервисов. Слушают `saga.commands.*`,
 * отвечают в `saga.replies`. Поведение ledger переключается флагом [Behavior.ledgerShouldFail],
 * чтобы один и тот же набор участников обслуживал и happy-path, и компенсацию.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeParticipants(
  kafkaProperties: KafkaProperties,
  private val objectMapper: ObjectMapper,
) {
  private val log = LoggerFactory.getLogger(FakeParticipants::class.java)

  class Behavior {
    val ledgerShouldFail = AtomicBoolean(false)
  }

  private val behavior = Behavior()

  private val replyTemplate: KafkaTemplate<String, Any> = run {
    val props = HashMap<String, Any>(kafkaProperties.buildProducerProperties())
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
    props[JsonSerializer.ADD_TYPE_INFO_HEADERS] = false
    KafkaTemplate(DefaultKafkaProducerFactory<String, Any>(props))
  }

  private val consumerProps: Map<String, Any> =
    HashMap<String, Any>(kafkaProperties.buildConsumerProperties()).apply {
      put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
      put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer::class.java)
      put(JsonDeserializer.TRUSTED_PACKAGES, "com.paypulse.saga.model")
      put(JsonDeserializer.VALUE_DEFAULT_TYPE, SagaCommand::class.java.name)
      put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false)
      put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    }

  @Bean
  fun participantBehavior(): Behavior = behavior

  @Bean
  fun participantCommandFactory(): ConcurrentKafkaListenerContainerFactory<String, SagaCommand> {
    val factory = ConcurrentKafkaListenerContainerFactory<String, SagaCommand>()
    factory.consumerFactory = DefaultKafkaConsumerFactory(consumerProps)
    return factory
  }

  @KafkaListener(
    topics = ["saga.commands.fraud-check"],
    groupId = "fake-fraud-check",
    containerFactory = "participantCommandFactory",
  )
  fun onFraudCheck(command: SagaCommand) = reply(command, mapOf("score" to 0.12))

  @KafkaListener(
    topics = ["saga.commands.risk-scoring"],
    groupId = "fake-risk-scoring",
    containerFactory = "participantCommandFactory",
  )
  fun onRiskScoring(command: SagaCommand) = reply(command, mapOf("score" to 0.20))

  @KafkaListener(
    topics = ["saga.commands.ledger-apply"],
    groupId = "fake-ledger-apply",
    containerFactory = "participantCommandFactory",
  )
  fun onLedgerApply(command: SagaCommand) {
    if (behavior.ledgerShouldFail.get() && !command.isCompensation) {
      replyFailure(command, "ledger rejected (test)")
    } else {
      reply(command, mapOf("ledgerEventId" to "led-test-1"))
    }
  }

  @KafkaListener(
    topics = ["saga.commands.notification"],
    groupId = "fake-notification",
    containerFactory = "participantCommandFactory",
  )
  fun onNotification(command: SagaCommand) = reply(command, mapOf("notificationId" to "ntf-test-1"))

  private fun reply(command: SagaCommand, payload: Map<String, Any?>) {
    val reply = SagaReply(
      sagaId = command.sagaId,
      stepName = command.stepName,
      status = ReplyStatus.SUCCESS,
      payload = if (command.isCompensation) null else objectMapper.writeValueAsString(payload),
      isCompensation = command.isCompensation,
    )
    log.info("Fake participant reply step={} compensation={} status=SUCCESS", command.stepName, command.isCompensation)
    replyTemplate.send("saga.replies", command.sagaId.toString(), reply)
  }

  private fun replyFailure(command: SagaCommand, error: String) {
    val reply = SagaReply(
      sagaId = command.sagaId,
      stepName = command.stepName,
      status = ReplyStatus.FAILURE,
      errorMessage = error,
      isCompensation = command.isCompensation,
    )
    log.info("Fake participant reply step={} status=FAILURE reason={}", command.stepName, error)
    replyTemplate.send("saga.replies", command.sagaId.toString(), reply)
  }
}
