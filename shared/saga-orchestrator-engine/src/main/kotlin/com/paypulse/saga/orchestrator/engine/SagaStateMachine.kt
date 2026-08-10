package com.paypulse.saga.orchestrator.engine

import com.paypulse.saga.model.SagaStatus
import com.paypulse.saga.model.StepStatus
import com.paypulse.saga.model.StepType
import com.paypulse.saga.orchestrator.dsl.SagaDefinition
import com.paypulse.saga.orchestrator.dsl.StepDefinition
import com.paypulse.saga.orchestrator.entity.SagaStepEntity
import org.springframework.stereotype.Component

sealed class SagaAction {
  data class ExecuteStep(val stepName: String) : SagaAction()
  data class CompensateStep(val stepName: String) : SagaAction()
  data class RetryStep(val stepName: String, val retryCount: Int) : SagaAction()
  data class Complete(val status: SagaStatus) : SagaAction()
}

@Component
class SagaStateMachine {

  fun determineNextAction(
    definition: SagaDefinition<out Any>,
    steps: List<SagaStepEntity>,
    currentStepName: String,
    replySuccess: Boolean,
    isCompensation: Boolean
  ): SagaAction {
    val stepDefs = definition.steps
    val currentStepDef = stepDefs.first { it.stepName == currentStepName }
    val currentIdx = stepDefs.indexOfFirst { it.stepName == currentStepName }

    if (isCompensation) {
      if (!replySuccess) {
        val prevCompensable = findPreviousCompensableStep(stepDefs, steps, currentIdx)
        return prevCompensable ?: SagaAction.Complete(SagaStatus.FAILED)
      }
      return handleCompensationReply(stepDefs, steps, currentIdx)
    }

    if (replySuccess) {
      return handleSuccessReply(stepDefs, currentIdx)
    }

    return handleFailureReply(stepDefs, steps, currentStepDef, currentIdx)
  }

  private fun handleSuccessReply(
    stepDefs: List<StepDefinition<out Any>>,
    currentIdx: Int
  ): SagaAction {
    val nextIdx = currentIdx + 1
    if (nextIdx >= stepDefs.size) {
      return SagaAction.Complete(SagaStatus.COMPLETED)
    }
    return SagaAction.ExecuteStep(stepDefs[nextIdx].stepName)
  }

  private fun handleFailureReply(
    stepDefs: List<StepDefinition<out Any>>,
    steps: List<SagaStepEntity>,
    failedStepDef: StepDefinition<out Any>,
    failedIdx: Int
  ): SagaAction {
    if (failedStepDef.stepType == StepType.RETRYABLE) {
      val stepEntity = steps.first { it.stepName == failedStepDef.stepName }
      if (stepEntity.retryCount < failedStepDef.maxRetries) {
        return SagaAction.RetryStep(failedStepDef.stepName, stepEntity.retryCount + 1)
      }
      return SagaAction.Complete(SagaStatus.FAILED)
    }

    val lastCompensable = findLastCompensableStep(stepDefs, steps, failedIdx)
    return lastCompensable
      ?: SagaAction.Complete(SagaStatus.COMPENSATED)
  }

  private fun handleCompensationReply(
    stepDefs: List<StepDefinition<out Any>>,
    steps: List<SagaStepEntity>,
    compensatedIdx: Int
  ): SagaAction {
    val prevCompensable = findPreviousCompensableStep(stepDefs, steps, compensatedIdx)
    return prevCompensable
      ?: SagaAction.Complete(SagaStatus.COMPENSATED)
  }

  private fun findLastCompensableStep(
    stepDefs: List<StepDefinition<out Any>>,
    steps: List<SagaStepEntity>,
    beforeIdx: Int
  ): SagaAction.CompensateStep? {
    for (i in (beforeIdx - 1) downTo 0) {
      val def = stepDefs[i]
      val entity = steps.first { it.stepName == def.stepName }
      if (def.stepType == StepType.COMPENSABLE
        && entity.status == StepStatus.COMPLETED.name
        && def.compensationBuilder != null
      ) {
        return SagaAction.CompensateStep(def.stepName)
      }
    }
    return null
  }

  private fun findPreviousCompensableStep(
    stepDefs: List<StepDefinition<out Any>>,
    steps: List<SagaStepEntity>,
    currentIdx: Int
  ): SagaAction.CompensateStep? {
    for (i in (currentIdx - 1) downTo 0) {
      val def = stepDefs[i]
      val entity = steps.first { it.stepName == def.stepName }
      if (def.stepType == StepType.COMPENSABLE
        && entity.status == StepStatus.COMPLETED.name
        && def.compensationBuilder != null
      ) {
        return SagaAction.CompensateStep(def.stepName)
      }
    }
    return null
  }
}
