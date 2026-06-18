package com.paypulse.accountquery.adapter.`in`

import com.paypulse.accountquery.application.BalanceQueryService
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Component
class BalanceQueryHandler(
  private val balanceQueryService: BalanceQueryService,
) {

  fun getBalance(req: ServerRequest): Mono<ServerResponse> {
    val accountId = req.pathVariable("accountId")
    val currency = req.queryParam("currency").orElse("USD")
    val at: OffsetDateTime? = try {
      val raw = req.queryParam("at").orElse(null)
      if (raw.isNullOrBlank()) {
        null
      } else {
        OffsetDateTime.parse(raw)
      }
    } catch (_: DateTimeParseException) {
      return ServerResponse.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          mapOf(
            "code" to "INVALID_AT",
            "message" to "Use ISO-8601 offset datetime, e.g. 2026-01-15T12:00:00Z",
          ),
        )
    }
    return balanceQueryService.getBalance(accountId, currency, at)
      .flatMap { body ->
        ServerResponse.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(body)
      }
  }
}
