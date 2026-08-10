package com.paypulse.saga.orchestrator.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.paypulse.saga.model.SagaCommand
import com.paypulse.saga.model.SagaReply
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaConfig(
  private val kafkaProperties: KafkaProperties
) {

  @Bean
  fun sagaCommandProducerFactory(): ProducerFactory<String, SagaCommand> {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildProducerProperties())
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JsonSerializer::class.java
    props["spring.json.add.type.headers"] = false
    return DefaultKafkaProducerFactory(props)
  }

  @Bean
  fun sagaCommandKafkaTemplate(): KafkaTemplate<String, SagaCommand> {
    return KafkaTemplate(sagaCommandProducerFactory())
  }

  @Bean
  fun sagaEventProducerFactory(): ProducerFactory<String, String> {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildProducerProperties())
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    return DefaultKafkaProducerFactory(props)
  }

  @Bean
  fun sagaEventKafkaTemplate(): KafkaTemplate<String, String> {
    return KafkaTemplate(sagaEventProducerFactory())
  }

  @Bean
  fun sagaReplyConsumerFactory(): ConsumerFactory<String, SagaReply> {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildConsumerProperties())
    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java
    props[JsonDeserializer.TRUSTED_PACKAGES] = "com.paypulse.saga.model"
    props[JsonDeserializer.VALUE_DEFAULT_TYPE] = SagaReply::class.java.name
    props["spring.json.use.type.headers"] = false
    return DefaultKafkaConsumerFactory(props)
  }

  @Bean
  fun kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, SagaReply> {
    val factory = ConcurrentKafkaListenerContainerFactory<String, SagaReply>()
    factory.consumerFactory = sagaReplyConsumerFactory()
    return factory
  }

  @Bean
  fun sagaStartConsumerFactory(): ConsumerFactory<String, JsonNode> {
    val props = HashMap<String, Any>()
    props.putAll(kafkaProperties.buildConsumerProperties())
    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JsonDeserializer::class.java
    props[JsonDeserializer.TRUSTED_PACKAGES] = "*"
    props[JsonDeserializer.VALUE_DEFAULT_TYPE] = JsonNode::class.java.name
    props["spring.json.use.type.headers"] = false
    return DefaultKafkaConsumerFactory(props)
  }

  @Bean
  fun sagaStartKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, JsonNode> {
    val factory = ConcurrentKafkaListenerContainerFactory<String, JsonNode>()
    factory.consumerFactory = sagaStartConsumerFactory()
    return factory
  }
}
