package com.paypulse.bff.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.bff.state.RuleAckStore
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

interface RuleAckListener {
  fun onRule(record: ConsumerRecord<String, String?>)
}

@Component
class DefaultRuleAckListener(
  private val objectMapper: ObjectMapper,
  private val store: RuleAckStore,
) : RuleAckListener {
  private val log = LoggerFactory.getLogger(DefaultRuleAckListener::class.java)

  @KafkaListener(
    topics = ["\${paypulse.bff.fraud-rules-topic}"],
    groupId = "bff-ops-rule-ack",
  )
  override fun onRule(record: ConsumerRecord<String, String?>) {
    val value = record.value() ?: return // tombstone
    try {
      val node = objectMapper.readTree(value)
      val ruleId = node.path("ruleId").takeIf { it.isTextual }?.asText()
        ?: record.key()
        ?: return
      val version = node.path("version").let { if (it.isNumber) it.asLong() else 1L }
      log.debug("rule applied ruleId={} version={}", ruleId, version)
      store.onRuleApplied(ruleId, version)
    } catch (e: Exception) {
      log.warn("Skipping malformed fraud_rules record offset={}: {}", record.offset(), e.message)
    }
  }
}
