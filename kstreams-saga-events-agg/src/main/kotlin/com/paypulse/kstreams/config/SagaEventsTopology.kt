package com.paypulse.kstreams.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams

@Configuration
@EnableKafkaStreams
class SagaEventsTopology(
  private val objectMapper: ObjectMapper,
  private val meterRegistry: MeterRegistry,
  @Value("\${paypulse.saga.events-topic:saga.events}") private val eventsTopic: String,
) {

  @Bean
  fun sagaOutcomeMetrics(streamsBuilder: StreamsBuilder): KStream<String, String> {
    val stream = streamsBuilder.stream(eventsTopic, Consumed.with(Serdes.String(), Serdes.String()))
    stream.foreach { _, value ->
      runCatching {
        val eventType = objectMapper.readTree(value).path("eventType").asText("")
        outcomeOf(eventType)?.let { outcome ->
          meterRegistry.counter("paypulse_saga_outcome_total", "outcome", outcome).increment()
        }
      }
    }
    return stream
  }

  private fun outcomeOf(eventType: String): String? = when (eventType) {
    "SAGA_COMPLETED" -> "COMPLETED"
    "SAGA_FAILED" -> "FAILED"
    "SAGA_COMPENSATED" -> "COMPENSATED"
    else -> null
  }
}
