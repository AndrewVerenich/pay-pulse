package com.paypulse.saga.orchestrator.scheduler

import com.paypulse.saga.model.ReplyStatus
import com.paypulse.saga.model.SagaReply
import com.paypulse.saga.orchestrator.engine.SagaOrchestrator
import com.paypulse.saga.orchestrator.repository.SagaInstanceRepository
import com.paypulse.saga.orchestrator.repository.SagaStepRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SagaTimeoutScheduler(
  private val stepRepository: SagaStepRepository,
  private val instanceRepository: SagaInstanceRepository,
  private val sagaOrchestrator: SagaOrchestrator,
  @Value("\${saga.timeout.check-seconds:30}") private val timeoutSeconds: Int
) {
  private val log = LoggerFactory.getLogger(SagaTimeoutScheduler::class.java)

  @Scheduled(fixedDelayString = "\${saga.timeout.check-interval-ms:10000}")
  fun checkTimeouts() {
    stepRepository.findTimedOutSteps(timeoutSeconds)
      .flatMap { step ->
        log.warn("Step timed out: sagaInstanceId={} step={}", step.sagaInstanceId, step.stepName)

        instanceRepository.findById(step.sagaInstanceId)
          .flatMap { instance ->
            val reply = SagaReply(
              sagaId = instance.sagaId,
              stepName = step.stepName,
              status = ReplyStatus.FAILURE,
              errorMessage = "Step timed out after ${timeoutSeconds}s"
            )
            sagaOrchestrator.handleReply(reply)
          }
      }
      .subscribe()
  }
}
