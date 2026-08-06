package com.paypulse.rules.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.paypulse.rules.adapter.`in`.CreateFraudRuleRequest
import com.paypulse.rules.adapter.`in`.FraudRuleDto
import com.paypulse.rules.adapter.`in`.UpdateFraudRuleRequest
import com.paypulse.rules.adapter.persistence.FraudRuleRepository
import com.paypulse.rules.adapter.persistence.FraudRuleRow
import com.paypulse.rules.adapter.persistence.RuleOutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

class FraudRuleNotFoundException(id: String) : RuntimeException("Fraud rule not found: $id")

interface FraudRuleService {
  fun list(): Flux<FraudRuleDto>
  fun create(request: CreateFraudRuleRequest): Mono<FraudRuleDto>
  fun update(id: UUID, request: UpdateFraudRuleRequest): Mono<FraudRuleDto>
  fun delete(id: UUID): Mono<Void>
}

@Service
class DefaultFraudRuleService(
  private val ruleRepository: FraudRuleRepository,
  private val outboxRepository: RuleOutboxRepository,
  private val validator: RuleSpecValidator,
  private val transactionalOperator: TransactionalOperator,
  private val objectMapper: ObjectMapper,
) : FraudRuleService {

  override fun list(): Flux<FraudRuleDto> = ruleRepository.findAllRules().map { it.toDto() }

  override fun create(request: CreateFraudRuleRequest): Mono<FraudRuleDto> {
    validator.validate(request.jsonSpec)
    val flow = ruleRepository.insertRule(request.name, request.enabled, request.jsonSpec)
      .flatMap { row -> publishRule(row).thenReturn(row.toDto()) }
    return transactionalOperator.transactional(flow)
  }

  override fun update(id: UUID, request: UpdateFraudRuleRequest): Mono<FraudRuleDto> {
    validator.validate(request.jsonSpec)
    val flow = ruleRepository.updateRule(id, request.name, request.enabled, request.jsonSpec)
      .switchIfEmpty(Mono.error(FraudRuleNotFoundException(id.toString())))
      .flatMap { row -> publishRule(row).thenReturn(row.toDto()) }
    return transactionalOperator.transactional(flow)
  }

  override fun delete(id: UUID): Mono<Void> {
    val flow = ruleRepository.findRuleById(id)
      .switchIfEmpty(Mono.error(FraudRuleNotFoundException(id.toString())))
      .flatMap { row ->
        ruleRepository.deleteRuleById(id)
          .then(publishRule(row.copy(enabled = false)))
      }
      .then()
    return transactionalOperator.transactional(flow)
  }

  private fun publishRule(row: FraudRuleRow): Mono<Void> {
    val payload = buildFlinkRulePayload(row)
    return outboxRepository.insertReturningId(
      partitioningKey = row.id.toString(),
      type = "FraudRuleUpdated",
      payload = payload,
    ).flatMap { outboxId -> outboxRepository.deleteOutboxById(outboxId) }.then()
  }

  private fun buildFlinkRulePayload(row: FraudRuleRow): String {
    val spec = objectMapper.readTree(row.jsonSpec) as ObjectNode
    val payload = spec.deepCopy()
    payload.put("ruleId", row.id.toString())
    payload.put("version", row.version)
    payload.put("enabled", row.enabled)
    return objectMapper.writeValueAsString(payload)
  }

  private fun FraudRuleRow.toDto() = FraudRuleDto(
    id = id.toString(),
    name = name,
    enabled = enabled,
    jsonSpec = jsonSpec,
    version = version,
    updatedAt = updatedAt,
  )
}
