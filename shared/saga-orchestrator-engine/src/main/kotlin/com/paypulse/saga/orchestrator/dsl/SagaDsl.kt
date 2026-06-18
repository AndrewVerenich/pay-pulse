package com.paypulse.saga.orchestrator.dsl

import com.paypulse.saga.model.StepType
import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration

@DslMarker
annotation class SagaDslMarker

data class SagaDefinition<T : Any>(
  val sagaType: String,
  val dataClass: Class<T>,
  val steps: List<StepDefinition<T>>
)

data class StepDefinition<T : Any>(
  val stepName: String,
  val stepType: StepType,
  val participant: String,
  val commandBuilder: (T) -> Any,
  val onReplyHandler: ((T, JsonNode) -> T)?,
  val compensationBuilder: ((T) -> Any)?,
  val timeout: Duration,
  val maxRetries: Int,
  val retryBackoff: Duration
)

inline fun <reified T : Any> saga(
  sagaType: String,
  block: SagaBuilder<T>.() -> Unit
): SagaDefinition<T> {
  val builder = SagaBuilder<T>(sagaType, T::class.java)
  builder.block()
  return builder.build()
}

@SagaDslMarker
class SagaBuilder<T : Any>(
  private val sagaType: String,
  private val dataClass: Class<T>
) {
  private val steps = mutableListOf<StepDefinition<T>>()

  fun step(name: String, block: StepBuilder<T>.() -> Unit) {
    val builder = StepBuilder<T>(name)
    builder.block()
    steps.add(builder.build())
  }

  fun build(): SagaDefinition<T> = SagaDefinition(
    sagaType = sagaType,
    dataClass = dataClass,
    steps = steps.toList()
  )
}

@SagaDslMarker
class StepBuilder<T : Any>(private val stepName: String) {
  var type: StepType = StepType.COMPENSABLE
  var participant: String = ""
  var timeout: Duration = Duration.ofSeconds(30)
  var maxRetries: Int = 3
  var retryBackoff: Duration = Duration.ofSeconds(5)

  private var commandBuilder: ((T) -> Any)? = null
  private var onReplyHandler: ((T, JsonNode) -> T)? = null
  private var compensationBuilder: ((T) -> Any)? = null

  fun command(builder: (T) -> Any) {
    commandBuilder = builder
  }

  fun onReply(handler: (T, JsonNode) -> T) {
    onReplyHandler = handler
  }

  fun compensation(builder: (T) -> Any) {
    compensationBuilder = builder
  }

  fun build(): StepDefinition<T> {
    require(participant.isNotBlank()) { "Participant must be set for step '$stepName'" }
    requireNotNull(commandBuilder) { "Command builder must be set for step '$stepName'" }

    return StepDefinition(
      stepName = stepName,
      stepType = type,
      participant = participant,
      commandBuilder = commandBuilder!!,
      onReplyHandler = onReplyHandler,
      compensationBuilder = compensationBuilder,
      timeout = timeout,
      maxRetries = maxRetries,
      retryBackoff = retryBackoff
    )
  }
}
