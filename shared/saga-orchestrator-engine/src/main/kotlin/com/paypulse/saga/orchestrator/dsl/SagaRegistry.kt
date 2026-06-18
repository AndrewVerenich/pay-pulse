package com.paypulse.saga.orchestrator.dsl

import org.springframework.stereotype.Component

@Component
class SagaRegistry(
  definitions: List<SagaDefinition<out Any>>
) {
  private val registry: Map<String, SagaDefinition<out Any>> =
    definitions.associateBy { it.sagaType }

  fun get(sagaType: String): SagaDefinition<out Any> =
    registry[sagaType] ?: throw IllegalArgumentException("Unknown saga type: $sagaType. Available: ${registry.keys}")

  fun allTypes(): Set<String> = registry.keys
}
