package com.paypulse.saga.participant

import com.paypulse.saga.model.SagaCommand
import com.paypulse.saga.model.SagaReply
import com.paypulse.saga.participant.consumer.SagaCommandConsumer
import com.paypulse.saga.participant.handler.SagaCommandHandler
import com.paypulse.saga.participant.producer.SagaReplyProducer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@AutoConfiguration
@EnableKafka
@ConditionalOnProperty(prefix = "paypulse.saga.participant", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class SagaParticipantAutoConfiguration(
  private val kafkaProperties: KafkaProperties
) {

  @Bean
  fun sagaReplyProducerFactory(): ProducerFactory<String, SagaReply> {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildProducerProperties())
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
    props["spring.json.add.type.headers"] = false
    return DefaultKafkaProducerFactory(props)
  }

  @Bean
  fun sagaReplyKafkaTemplate(): KafkaTemplate<String, SagaReply> {
    return KafkaTemplate(sagaReplyProducerFactory())
  }

  @Bean
  fun sagaReplyProducer(
    sagaReplyKafkaTemplate: KafkaTemplate<String, SagaReply>,
    @Value("\${paypulse.saga.participant.reply-topic:saga.replies}") replyTopic: String
  ): SagaReplyProducer {
    return SagaReplyProducer(sagaReplyKafkaTemplate, replyTopic)
  }

  @Bean
  fun sagaCommandConsumerFactory(): ConsumerFactory<String, SagaCommand> {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildConsumerProperties())
    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java
    props[JsonDeserializer.TRUSTED_PACKAGES] = "com.paypulse.saga.model"
    props[JsonDeserializer.VALUE_DEFAULT_TYPE] = SagaCommand::class.java.name
    props["spring.json.use.type.headers"] = false
    return DefaultKafkaConsumerFactory(props)
  }

  @Bean
  fun sagaKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, SagaCommand> {
    val factory = ConcurrentKafkaListenerContainerFactory<String, SagaCommand>()
    factory.consumerFactory = sagaCommandConsumerFactory()
    return factory
  }

  @Bean
  fun sagaCommandConsumer(
    handlers: List<SagaCommandHandler>,
    sagaReplyProducer: SagaReplyProducer
  ): SagaCommandConsumer {
    val handlerMap = handlers.associateBy { it.commandType }
    return SagaCommandConsumer(handlerMap, sagaReplyProducer)
  }
}
