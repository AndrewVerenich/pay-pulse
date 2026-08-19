package com.paypulse.flink.sink

import com.paypulse.flink.io.PaymentJson
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.kafka.clients.producer.ProducerRecord
import java.io.Serializable
import java.nio.charset.StandardCharsets

/** JSON-сообщение с опциональным ключом (для партиционирования/компакции). */
data class KeyedJson(val key: String?, val value: String) : Serializable

object KafkaSinks {

  fun jsonSink(bootstrapServers: String, topic: String): KafkaSink<KeyedJson> =
    KafkaSink.builder<KeyedJson>()
      .setBootstrapServers(bootstrapServers)
      .setRecordSerializer(KeyedJsonSerializer(topic))
      .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .build()

  fun toJson(value: Any): String = PaymentJson.mapper.writeValueAsString(value)
}

private class KeyedJsonSerializer(private val topic: String) : KafkaRecordSerializationSchema<KeyedJson> {
  override fun serialize(
    element: KeyedJson,
    context: KafkaRecordSerializationSchema.KafkaSinkContext?,
    timestamp: Long?,
  ): ProducerRecord<ByteArray, ByteArray> {
    val key = element.key?.toByteArray(StandardCharsets.UTF_8)
    val value = element.value.toByteArray(StandardCharsets.UTF_8)
    return ProducerRecord(topic, key, value)
  }
}
