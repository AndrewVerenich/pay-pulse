package com.paypulse.bff.aggregator

import com.fasterxml.jackson.databind.JsonNode
import com.paypulse.bff.model.SagaStepSummary
import com.paypulse.bff.model.SagaSummary
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

interface SagaQueryService {
  fun fetchSummary(sagaId: String): Mono<SagaSummary>
  fun fetchRaw(sagaId: String): Mono<JsonNode>
}

@Service
class DefaultSagaQueryService(
  private val sagaOrchestratorClient: WebClient,
) : SagaQueryService {

  override fun fetchSummary(sagaId: String): Mono<SagaSummary> =
    fetchRaw(sagaId).map { it.toSummary() }

  override fun fetchRaw(sagaId: String): Mono<JsonNode> =
    sagaOrchestratorClient.get()
      .uri("/api/v1/sagas/{id}", sagaId)
      .retrieve()
      .onStatus({ status: HttpStatusCode -> status.value() == 404 }) { Mono.empty() }
      .bodyToMono(JsonNode::class.java)

  private fun JsonNode.toSummary(): SagaSummary {
    val steps = path("steps").takeIf { it.isArray }?.map { step ->
      SagaStepSummary(
        stepName = step.path("stepName").asText(""),
        status = step.path("status").asText(""),
        attempts = step.path("retryCount").asInt(0),
        startedAt = step.path("startedAt").asTextOrNull(),
        finishedAt = step.path("completedAt").asTextOrNull(),
      )
    } ?: emptyList()
    return SagaSummary(
      sagaId = path("sagaId").asText(""),
      status = path("status").asText(""),
      currentStep = path("currentStep").asTextOrNull(),
      steps = steps,
    )
  }

  private fun JsonNode.asTextOrNull(): String? =
    if (isNull || isMissingNode || asText().isBlank()) null else asText()
}
