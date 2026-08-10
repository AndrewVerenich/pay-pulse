package com.paypulse.saga.orchestrator.controller

import com.paypulse.saga.orchestrator.engine.SagaNotFoundException
import com.paypulse.saga.orchestrator.engine.SagaOrchestrator
import com.paypulse.saga.orchestrator.service.SagaStuckQueryService
import com.paypulse.saga.orchestrator.service.StuckSagaItem
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sagas")
class SagaAdminController(
  private val sagaOrchestrator: SagaOrchestrator,
  private val stuckQueryService: SagaStuckQueryService,
) {

  @GetMapping("/stuck")
  fun listStuck(): Flux<StuckSagaItem> = stuckQueryService.listStuck()

  @PostMapping("/{sagaId}/retry")
  fun retry(@PathVariable sagaId: UUID): Mono<ResponseEntity<Void>> =
    sagaOrchestrator.retrySaga(sagaId)
      .thenReturn(ResponseEntity.accepted().build())

  @PostMapping("/{sagaId}/force-complete")
  fun forceComplete(@PathVariable sagaId: UUID): Mono<ResponseEntity<Void>> =
    sagaOrchestrator.forceComplete(sagaId)
      .thenReturn(ResponseEntity.accepted().build())

  @PostMapping("/{sagaId}/mark-resolved")
  fun markResolved(@PathVariable sagaId: UUID): Mono<ResponseEntity<Void>> =
    sagaOrchestrator.markResolved(sagaId)
      .thenReturn(ResponseEntity.noContent().build())
}

@RestControllerAdvice
class SagaAdminExceptionHandler {

  @ExceptionHandler(SagaNotFoundException::class)
  fun notFound(ex: SagaNotFoundException): Mono<ResponseEntity<AdminErrorResponse>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(AdminErrorResponse("SAGA_NOT_FOUND", ex.message)),
    )

  @ExceptionHandler(IllegalStateException::class)
  fun badState(ex: IllegalStateException): Mono<ResponseEntity<AdminErrorResponse>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(AdminErrorResponse("INVALID_SAGA_STATE", ex.message)),
    )
}

data class AdminErrorResponse(val code: String, val message: String?)
