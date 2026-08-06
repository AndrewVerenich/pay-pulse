package com.paypulse.rules.adapter.`in`

import com.paypulse.rules.application.FraudRuleNotFoundException
import com.paypulse.rules.application.FraudRuleService
import com.paypulse.rules.application.RuleSpecValidationException
import jakarta.validation.Valid
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.util.UUID

interface FraudRuleController {
  fun list(): Flux<FraudRuleDto>
  fun create(request: CreateFraudRuleRequest): Mono<ResponseEntity<FraudRuleDto>>
  fun update(id: UUID, request: UpdateFraudRuleRequest): Mono<FraudRuleDto>
  fun delete(id: UUID): Mono<ResponseEntity<Void>>
}

@RestController
@RequestMapping("/api/v1/fraud-rules")
class DefaultFraudRuleController(
  private val service: FraudRuleService,
) : FraudRuleController {

  @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
  override fun list(): Flux<FraudRuleDto> = service.list()

  @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
  override fun create(@Valid @RequestBody request: CreateFraudRuleRequest): Mono<ResponseEntity<FraudRuleDto>> =
    service.create(request).map { dto ->
      ResponseEntity.created(URI.create("/api/v1/fraud-rules/${dto.id}")).body(dto)
    }

  @PutMapping(
    "/{id}",
    consumes = [MediaType.APPLICATION_JSON_VALUE],
    produces = [MediaType.APPLICATION_JSON_VALUE],
  )
  override fun update(
    @PathVariable id: UUID,
    @Valid @RequestBody request: UpdateFraudRuleRequest,
  ): Mono<FraudRuleDto> = service.update(id, request)

  @DeleteMapping("/{id}")
  override fun delete(@PathVariable id: UUID): Mono<ResponseEntity<Void>> =
    service.delete(id).thenReturn(ResponseEntity.noContent().build())
}

@RestControllerAdvice
class RuleManagementExceptionHandler {

  @ExceptionHandler(RuleSpecValidationException::class)
  fun invalidSpec(ex: RuleSpecValidationException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(ErrorResponse("INVALID_RULE_SPEC", ex.message))

  @ExceptionHandler(WebExchangeBindException::class)
  fun bind(ex: WebExchangeBindException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .body(ErrorResponse("VALIDATION_ERROR", ex.reason ?: ex.message))

  @ExceptionHandler(FraudRuleNotFoundException::class)
  fun notFound(ex: FraudRuleNotFoundException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.NOT_FOUND)
      .body(ErrorResponse("RULE_NOT_FOUND", ex.message))

  @ExceptionHandler(DuplicateKeyException::class)
  fun duplicate(ex: DuplicateKeyException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.CONFLICT)
      .body(ErrorResponse("RULE_NAME_CONFLICT", "Rule name already exists"))
}

data class ErrorResponse(val code: String, val message: String?)
