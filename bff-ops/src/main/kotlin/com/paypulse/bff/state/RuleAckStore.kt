package com.paypulse.bff.state

import com.paypulse.bff.model.RuleAck
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap

interface RuleAckStore {
  fun onRuleApplied(ruleId: String, version: Long)
  fun stream(): Flux<RuleAck>
  fun latestVersion(ruleId: String): Long?
}

@Component
class DefaultRuleAckStore : RuleAckStore {

  private val latestVersionByRule = ConcurrentHashMap<String, Long>()
  private val sink: Sinks.Many<RuleAck> = Sinks.many().multicast().onBackpressureBuffer(256, false)

  override fun onRuleApplied(ruleId: String, version: Long) {
    latestVersionByRule.merge(ruleId, version) { old, new -> maxOf(old, new) }
    sink.tryEmitNext(RuleAck(ruleId = ruleId, version = version))
  }

  override fun stream(): Flux<RuleAck> = sink.asFlux()

  override fun latestVersion(ruleId: String): Long? = latestVersionByRule[ruleId]
}
