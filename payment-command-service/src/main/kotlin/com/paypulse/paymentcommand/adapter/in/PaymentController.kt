package com.paypulse.paymentcommand.adapter.`in`

import com.paypulse.common.model.CreatePaymentRequest
import com.paypulse.common.model.CreatePaymentResponse
import com.paypulse.paymentcommand.application.IdempotencyConflictException
import com.paypulse.paymentcommand.application.OptimisticConcurrencyException
import com.paypulse.paymentcommand.application.PaymentApplicationService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
  private val paymentApplicationService: PaymentApplicationService,
  private val objectMapper: ObjectMapper,
  private val validator: Validator,
) {

  @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
  fun createPayment(
    @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
    @RequestBody body: Mono<CreatePaymentRequest>,
  ): Mono<CreatePaymentResponse> =
    body.flatMap { validate(it) }
      .flatMap { req ->
        val canonical = objectMapper.writeValueAsString(req)
        paymentApplicationService.createPayment(req, idempotencyKey, canonical)
      }

  private fun <T : Any> validate(target: T): Mono<T> {
    val violations: Set<ConstraintViolation<T>> = validator.validate(target)
    if (violations.isEmpty()) {
      return Mono.just(target)
    }
    val message = violations.joinToString("; ") { "${it.propertyPath}: ${it.message}" }
    return Mono.error(IllegalArgumentException(message))
  }
}

@RestControllerAdvice
class PaymentCommandExceptionHandler {

  @ExceptionHandler(IdempotencyConflictException::class)
  fun conflict(ex: IdempotencyConflictException): Mono<ResponseEntity<ErrorResponse>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse(code = "IDEMPOTENCY_CONFLICT", message = ex.message ?: "conflict")),
    )

  @ExceptionHandler(OptimisticConcurrencyException::class)
  fun optimisticLock(ex: OptimisticConcurrencyException): Mono<ResponseEntity<ErrorResponse>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse(code = "OPTIMISTIC_CONCURRENCY", message = ex.message)),
    )

  @ExceptionHandler(IllegalArgumentException::class)
  fun badRequest(ex: IllegalArgumentException): Mono<ResponseEntity<ErrorResponse>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(code = "VALIDATION_ERROR", message = ex.message)),
    )

  @ExceptionHandler(WebExchangeBindException::class)
  fun bind(ex: WebExchangeBindException): Mono<ResponseEntity<ErrorResponse>> =
    Mono.just(
      ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(code = "VALIDATION_ERROR", message = ex.message)),
    )
}

data class ErrorResponse(val code: String, val message: String?)

