package com.paypulse.paymentcommand.adapter.`in`

import com.fasterxml.jackson.databind.ObjectMapper
import com.paypulse.common.model.CreatePaymentRequest
import com.paypulse.common.model.CreatePaymentResponse
import com.paypulse.paymentcommand.application.PaymentApplicationService
import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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


