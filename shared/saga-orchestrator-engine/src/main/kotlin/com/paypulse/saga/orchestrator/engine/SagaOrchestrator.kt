package com.paypulse.saga.orchestrator.engine

import com.paypulse.saga.model.*
import com.paypulse.saga.orchestrator.dsl.SagaDefinition
import com.paypulse.saga.orchestrator.dsl.SagaRegistry
import com.paypulse.saga.orchestrator.dsl.StepDefinition
import com.paypulse.saga.orchestrator.entity.SagaInstanceEntity
import com.paypulse.saga.orchestrator.entity.SagaStepEntity
import com.paypulse.saga.orchestrator.kafka.SagaCommandProducer
import com.paypulse.saga.orchestrator.kafka.SagaLifecycleKafkaPublisher
import com.paypulse.saga.orchestrator.metrics.SagaMetrics
import com.paypulse.saga.orchestrator.repository.SagaInstanceRepository
import com.paypulse.saga.orchestrator.repository.SagaStepRepository
import com.paypulse.saga.orchestrator.service.CompensationFailureService
import com.paypulse.saga.orchestrator.sse.SagaEventPublisher
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

@Service
class SagaOrchestrator(
  private val sagaRegistry: SagaRegistry,
  private val sagaStateMachine: SagaStateMachine,
  private val instanceRepository: SagaInstanceRepository,
  private val stepRepository: SagaStepRepository,
  private val commandProducer: SagaCommandProducer,
  private val objectMapper: ObjectMapper,
  private val sagaMetrics: SagaMetrics,
  private val eventPublisher: SagaEventPublisher,
  private val lifecyclePublisher: SagaLifecycleKafkaPublisher,
  private val compensationFailureService: CompensationFailureService,
) {
  private val log = LoggerFactory.getLogger(SagaOrchestrator::class.java)

  fun startSaga(sagaType: String, payload: String, sagaId: UUID = UUID.randomUUID()): Mono<SagaInstanceEntity> {
    val definition = sagaRegistry.get(sagaType)

    log.info("Starting saga [{}] id={}", sagaType, sagaId)

    val now = LocalDateTime.now()
    return instanceRepository.insertWithJsonb(
      sagaId = sagaId,
      sagaType = sagaType,
      status = SagaStatus.STARTED.name,
      currentStep = definition.steps.first().stepName,
      payload = payload,
      createdAt = now,
      updatedAt = now
    )
      .flatMap { saved ->
        createStepRecords(saved, definition)
          .then(Mono.just(saved))
      }
      .flatMap { saved ->
        sagaMetrics.recordSagaStarted(sagaType)
        eventPublisher.publishSagaEvent(saved)
        lifecyclePublisher.publishStarted(saved)
        executeStep(saved, definition, definition.steps.first())
          .thenReturn(saved)
      }
  }

  fun handleReply(reply: SagaReply): Mono<Void> {
    log.info("Received reply for saga={} step={} status={} compensation={}",
      reply.sagaId, reply.stepName, reply.status, reply.isCompensation)

    return instanceRepository.findBySagaId(reply.sagaId)
      .flatMap { instance ->
        val definition = sagaRegistry.get(instance.sagaType)
        stepRepository.findBySagaInstanceIdAndStepName(instance.id!!, reply.stepName)
          .flatMap { step ->
            updateStepFromReply(step, reply, definition)
              .flatMap { updatedStep ->
                eventPublisher.publishStepEvent(instance, updatedStep)
                lifecyclePublisher.publishStep(instance, updatedStep)
                processNextAction(instance, definition, reply)
              }
          }
      }
  }

  fun retrySaga(sagaId: UUID): Mono<Void> =
    instanceRepository.findBySagaId(sagaId)
      .switchIfEmpty(Mono.error(SagaNotFoundException(sagaId)))
      .flatMap { instance ->
        val stepName = instance.currentStep
          ?: return@flatMap Mono.error(IllegalStateException("Saga has no current step to retry"))
        retryStep(instance.id!!, stepName)
      }

  fun forceComplete(sagaId: UUID): Mono<Void> =
    instanceRepository.findBySagaId(sagaId)
      .switchIfEmpty(Mono.error(SagaNotFoundException(sagaId)))
      .flatMap { completeSaga(it, SagaStatus.COMPLETED) }

  fun markResolved(sagaId: UUID): Mono<Void> =
    compensationFailureService.markResolved(sagaId)

  fun retryStep(instanceId: Long, stepName: String): Mono<Void> {
    return instanceRepository.findById(instanceId)
      .flatMap { instance ->
        val definition = sagaRegistry.get(instance.sagaType)
        val stepDef = definition.steps.first { it.stepName == stepName }
        stepRepository.findBySagaInstanceIdAndStepName(instance.id!!, stepName)
          .flatMap { step ->
            val updated = step.copy(
              status = StepStatus.EXECUTING.name,
              retryCount = step.retryCount + 1,
              startedAt = LocalDateTime.now(),
              errorMessage = null
            )
            stepRepository.markRetrying(
              id = step.id!!,
              status = updated.status,
              retryCount = updated.retryCount,
              startedAt = updated.startedAt!!,
              errorMessage = updated.errorMessage
            )
              .then(executeStep(instance, definition, stepDef))
          }
      }
  }

  private fun createStepRecords(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>
  ): Mono<Void> {
    val steps = definition.steps.mapIndexed { idx, stepDef ->
      SagaStepEntity(
        sagaInstanceId = instance.id!!,
        stepName = stepDef.stepName,
        stepType = stepDef.stepType.name,
        stepOrder = idx,
        status = StepStatus.PENDING.name
      )
    }
    return stepRepository.saveAll(steps).then()
  }

  @Suppress("UNCHECKED_CAST")
  private fun executeStep(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>,
    stepDef: StepDefinition<out Any>
  ): Mono<Void> {
    val data = objectMapper.readValue(instance.payload, definition.dataClass)
    val rawDef = stepDef as StepDefinition<Any>
    val command = rawDef.commandBuilder(data)
    val commandJson = objectMapper.writeValueAsString(command)

    val sagaCommand = SagaCommand(
      sagaId = instance.sagaId,
      sagaType = instance.sagaType,
      stepName = stepDef.stepName,
      payload = commandJson,
      isCompensation = false
    )

    return stepRepository.findBySagaInstanceIdAndStepName(instance.id!!, stepDef.stepName)
      .flatMap { step ->
        val updated = step.copy(
          status = StepStatus.EXECUTING.name,
          commandPayload = commandJson,
          startedAt = LocalDateTime.now()
        )
        stepRepository.markExecutingWithCommandPayload(
          id = step.id!!,
          status = updated.status,
          commandPayload = updated.commandPayload!!,
          startedAt = updated.startedAt!!
        )
          .then(
            Mono.defer {
              val updatedAt = LocalDateTime.now()
              val updatedInstance = instance.copy(
                currentStep = stepDef.stepName,
                status = SagaStatus.EXECUTING.name,
                updatedAt = updatedAt
              )
              instanceRepository.updateState(
                id = updatedInstance.id!!,
                status = updatedInstance.status,
                currentStep = updatedInstance.currentStep,
                updatedAt = updatedAt
              ).thenReturn(updatedInstance)
            }
          )
      }
      .flatMap {
        commandProducer.sendCommand(stepDef.participant, sagaCommand)
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun executeCompensation(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>,
    stepDef: StepDefinition<out Any>
  ): Mono<Void> {
    val data = objectMapper.readValue(instance.payload, definition.dataClass)
    val rawDef = stepDef as StepDefinition<Any>
    val compensationCommand = rawDef.compensationBuilder?.invoke(data)
      ?: return Mono.empty()
    val commandJson = objectMapper.writeValueAsString(compensationCommand)

    val sagaCommand = SagaCommand(
      sagaId = instance.sagaId,
      sagaType = instance.sagaType,
      stepName = stepDef.stepName,
      payload = commandJson,
      isCompensation = true
    )

    return stepRepository.findBySagaInstanceIdAndStepName(instance.id!!, stepDef.stepName)
      .flatMap { step ->
        val updated = step.copy(
          status = StepStatus.COMPENSATING.name,
          startedAt = LocalDateTime.now()
        )
        stepRepository.markCompensating(
          id = step.id!!,
          status = updated.status,
          startedAt = updated.startedAt!!
        )
          .then(
            Mono.defer {
              val updatedAt = LocalDateTime.now()
              val updatedInstance = instance.copy(
                currentStep = stepDef.stepName,
                status = SagaStatus.COMPENSATING.name,
                updatedAt = updatedAt
              )
              instanceRepository.updateState(
                id = updatedInstance.id!!,
                status = updatedInstance.status,
                currentStep = updatedInstance.currentStep,
                updatedAt = updatedAt
              ).thenReturn(updatedInstance)
            }
          )
      }
      .flatMap {
        sagaMetrics.recordCompensationTriggered(instance.sagaType)
        commandProducer.sendCommand(stepDef.participant, sagaCommand)
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun updateStepFromReply(
    step: SagaStepEntity,
    reply: SagaReply,
    definition: SagaDefinition<out Any>
  ): Mono<SagaStepEntity> {
    val newStatus = if (reply.isCompensation) {
      if (reply.status == ReplyStatus.SUCCESS) StepStatus.COMPENSATED else StepStatus.FAILED
    } else {
      if (reply.status == ReplyStatus.SUCCESS) StepStatus.COMPLETED else StepStatus.FAILED
    }

    val updated = step.copy(
      status = newStatus.name,
      replyPayload = reply.payload,
      errorMessage = reply.errorMessage,
      completedAt = LocalDateTime.now()
    )

    sagaMetrics.recordStepDuration(definition.sagaType, updated)
    if (newStatus == StepStatus.FAILED) {
      sagaMetrics.recordStepFailure(definition.sagaType, step.stepName)
    }

    return stepRepository.markCompletedFromReply(
      id = updated.id!!,
      status = updated.status,
      replyPayload = updated.replyPayload,
      errorMessage = updated.errorMessage,
      completedAt = updated.completedAt!!
    ).thenReturn(updated)
  }

  @Suppress("UNCHECKED_CAST")
  private fun processNextAction(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>,
    reply: SagaReply
  ): Mono<Void> {
    return stepRepository.findBySagaInstanceIdOrderByStepOrder(instance.id!!)
      .collectList()
      .flatMap { steps ->
        // If reply was successful and had onReply handler, update saga payload
        val updatedPayloadMono = if (reply.status == ReplyStatus.SUCCESS && !reply.isCompensation) {
          updateSagaPayload(instance, definition, reply)
        } else {
          Mono.just(instance)
        }

        updatedPayloadMono.flatMap { updatedInstance ->
          val action = sagaStateMachine.determineNextAction(
            definition, steps, reply.stepName,
            reply.status == ReplyStatus.SUCCESS,
            reply.isCompensation
          )

          when (action) {
            is SagaAction.ExecuteStep -> {
              val nextDef = definition.steps.first { it.stepName == action.stepName }
              executeStep(updatedInstance, definition, nextDef)
            }

            is SagaAction.CompensateStep -> {
              val compDef = definition.steps.first { it.stepName == action.stepName }
              executeCompensation(updatedInstance, definition, compDef)
            }

            is SagaAction.RetryStep -> {
              sagaMetrics.recordStepRetry(updatedInstance.sagaType, action.stepName)
              retryStep(updatedInstance.id!!, action.stepName)
            }

            is SagaAction.Complete -> {
              completeSaga(updatedInstance, action.status)
            }
          }
        }
      }
  }

  @Suppress("UNCHECKED_CAST")
  private fun updateSagaPayload(
    instance: SagaInstanceEntity,
    definition: SagaDefinition<out Any>,
    reply: SagaReply
  ): Mono<SagaInstanceEntity> {
    val stepDef = definition.steps.firstOrNull { it.stepName == reply.stepName }
      ?: return Mono.just(instance)

    val rawDef = stepDef as StepDefinition<Any>
    val handler = rawDef.onReplyHandler ?: return Mono.just(instance)

    val data = objectMapper.readValue(instance.payload, definition.dataClass)
    val replyNode: JsonNode = reply.payload?.let { objectMapper.readTree(it) }
      ?: objectMapper.createObjectNode()
    val updatedData = handler(data, replyNode)
    val updatedPayload = objectMapper.writeValueAsString(updatedData)

    return instanceRepository.updatePayloadWithJsonb(
      id = instance.id!!,
      payload = updatedPayload,
      updatedAt = LocalDateTime.now()
    )
  }

  private fun completeSaga(instance: SagaInstanceEntity, status: SagaStatus): Mono<Void> {
    log.info("Saga [{}] id={} completed with status={}", instance.sagaType, instance.sagaId, status)

    val updatedAt = LocalDateTime.now()
    val completedAt = LocalDateTime.now()
    val updated = instance.copy(
      status = status.name,
      updatedAt = updatedAt,
      completedAt = completedAt
    )

    return instanceRepository.updateState(
      id = instance.id!!,
      status = status.name,
      currentStep = instance.currentStep,
      updatedAt = updatedAt,
      completedAt = completedAt
    )
      .flatMap {
        if (status == SagaStatus.FAILED) {
          compensationFailureService.record(
            updated,
            "Saga failed at step ${instance.currentStep ?: "unknown"}",
          )
        } else {
          Mono.empty()
        }
      }
      .doOnSuccess {
        val durationSeconds = java.time.Duration.between(instance.createdAt, updatedAt).toMillis() / 1000.0
        sagaMetrics.recordSagaCompleted(instance.sagaType, status, durationSeconds)
        eventPublisher.publishSagaEvent(updated)
        lifecyclePublisher.publishTerminal(updated)
      }
      .then()
  }
}

class SagaNotFoundException(sagaId: UUID) : RuntimeException("Saga not found: $sagaId")
