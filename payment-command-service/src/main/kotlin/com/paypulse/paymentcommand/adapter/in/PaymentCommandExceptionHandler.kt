package com.paypulse.paymentcommand.adapter.`in`

import com.paypulse.paymentcommand.application.IdempotencyConflictException
import com.paypulse.paymentcommand.application.OptimisticConcurrencyException
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import reactor.core.publisher.Mono

@RestControllerAdvice
class PaymentCommandExceptionHandler(
  private val meterRegistry: MeterRegistry,
) {

  @ExceptionHandler(IdempotencyConflictException::class)
  fun conflict(ex: IdempotencyConflictException): Mono<ResponseEntity<ErrorResponse>> {
    meterRegistry.counter("paypulse_payments_total", "result", "failed").increment()
    return Mono.just(
      ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse(code = "IDEMPOTENCY_CONFLICT", message = ex.message ?: "conflict")),
    )
  }

  @ExceptionHandler(OptimisticConcurrencyException::class)
  fun optimisticLock(ex: OptimisticConcurrencyException): Mono<ResponseEntity<ErrorResponse>> {
    meterRegistry.counter("paypulse_optimistic_lock_conflicts_total").increment()
    meterRegistry.counter("paypulse_payments_total", "result", "failed").increment()
    return Mono.just(
      ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse(code = "OPTIMISTIC_CONCURRENCY", message = ex.message)),
    )
  }

  @ExceptionHandler(IllegalArgumentException::class)
  fun badRequest(ex: IllegalArgumentException): Mono<ResponseEntity<ErrorResponse>> {
    meterRegistry.counter("paypulse_payments_total", "result", "failed").increment()
    return Mono.just(
      ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(code = "VALIDATION_ERROR", message = ex.message)),
    )
  }

  @ExceptionHandler(WebExchangeBindException::class)
  fun bind(ex: WebExchangeBindException): Mono<ResponseEntity<ErrorResponse>> {
    meterRegistry.counter("paypulse_payments_total", "result", "failed").increment()
    return Mono.just(
      ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(code = "VALIDATION_ERROR", message = ex.message)),
    )
  }
}

data class ErrorResponse(val code: String, val message: String?)
