package com.paypulse.accountquery.adapter.`in`

import com.paypulse.accountquery.application.BalanceQueryService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/v1/accounts")
class BalanceQueryController(
  private val balanceQueryService: BalanceQueryService,
) {

  @GetMapping("/{accountId}/balance", produces = [MediaType.APPLICATION_JSON_VALUE])
  fun getBalance(
    @PathVariable accountId: String,
    @RequestParam(defaultValue = "USD") currency: String,
    @RequestParam(required = false) at: String?,
  ): Mono<BalanceResponse> {
    val parsedAt = try {
      if (at.isNullOrBlank()) {
        null
      } else {
        OffsetDateTime.parse(at)
      }
    } catch (_: DateTimeParseException) {
      return Mono.error(InvalidAtException())
    }
    return balanceQueryService.getBalance(accountId, currency, parsedAt)
  }
}

class InvalidAtException : RuntimeException()

@RestControllerAdvice
class BalanceQueryExceptionHandler {

  @ExceptionHandler(InvalidAtException::class)
  fun invalidAt(): ResponseEntity<Map<String, String>> =
    ResponseEntity.status(HttpStatus.BAD_REQUEST)
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        mapOf(
          "code" to "INVALID_AT",
          "message" to "Use ISO-8601 offset datetime, e.g. 2026-01-15T12:00:00Z",
        ),
      )
}
