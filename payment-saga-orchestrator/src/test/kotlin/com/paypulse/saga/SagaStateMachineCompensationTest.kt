package com.paypulse.saga.orchestrator.engine

import com.paypulse.saga.model.SagaStatus
import com.paypulse.saga.model.StepStatus
import com.paypulse.saga.model.StepType
import com.paypulse.saga.orchestrator.dsl.SagaDefinition
import com.paypulse.saga.orchestrator.dsl.StepDefinition
import com.paypulse.saga.orchestrator.entity.SagaStepEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class SagaStateMachineCompensationTest {

  private val machine = SagaStateMachine()

  private val steps = listOf(
    stepDef("fraud", StepType.COMPENSABLE),
    stepDef("ledger", StepType.PIVOT),
  )

  private val entities = listOf(
    entity("fraud", StepStatus.COMPLETED),
    entity("ledger", StepStatus.FAILED),
  )

  @Test
  fun `failed compensation with no previous step completes as FAILED`() {
    val action = machine.determineNextAction(
      definition = SagaDefinition("Test", String::class.java, steps),
      steps = entities,
      currentStepName = "fraud",
      replySuccess = false,
      isCompensation = true,
    )
    assertTrue(action is SagaAction.Complete)
    assertEquals(SagaStatus.FAILED, (action as SagaAction.Complete).status)
  }

  private fun stepDef(name: String, type: StepType): StepDefinition<String> = StepDefinition(
    stepName = name,
    stepType = type,
    participant = name,
    commandBuilder = { value -> value },
    onReplyHandler = null,
    compensationBuilder = if (type == StepType.COMPENSABLE) ({ value: String -> value }) else null,
    timeout = Duration.ofMinutes(5),
    maxRetries = 3,
    retryBackoff = Duration.ofSeconds(1),
  )

  private fun entity(name: String, status: StepStatus) = SagaStepEntity(
    sagaInstanceId = 1L,
    stepName = name,
    stepType = StepType.COMPENSABLE.name,
    stepOrder = 0,
    status = status.name,
  )
}
