package com.paypulse.bff.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class SseTokenWebFilterTest {

  private val filter = SseTokenWebFilter()

  private fun capturing(): Pair<WebFilterChain, () -> ServerWebExchange?> {
    var captured: ServerWebExchange? = null
    val chain = WebFilterChain { exchange ->
      captured = exchange
      Mono.empty()
    }
    return chain to { captured }
  }

  @Test
  fun `moves token query param into Authorization header for live paths`() {
    val request = MockServerHttpRequest.get("/api/live/payments/stream?token=abc.def.ghi").build()
    val exchange = MockServerWebExchange.from(request)
    val (chain, captured) = capturing()

    filter.filter(exchange, chain).block()

    assertEquals("Bearer abc.def.ghi", captured()!!.request.headers.getFirst(HttpHeaders.AUTHORIZATION))
  }

  @Test
  fun `does not touch non-live paths`() {
    val request = MockServerHttpRequest.get("/api/payments/recent?token=abc").build()
    val exchange = MockServerWebExchange.from(request)
    val (chain, captured) = capturing()

    filter.filter(exchange, chain).block()

    assertNull(captured()!!.request.headers.getFirst(HttpHeaders.AUTHORIZATION))
  }

  @Test
  fun `keeps existing Authorization header`() {
    val request = MockServerHttpRequest.get("/api/live/payments/stream?token=ignored")
      .header(HttpHeaders.AUTHORIZATION, "Bearer real")
      .build()
    val exchange = MockServerWebExchange.from(request)
    val (chain, captured) = capturing()

    filter.filter(exchange, chain).block()

    assertEquals("Bearer real", captured()!!.request.headers.getFirst(HttpHeaders.AUTHORIZATION))
  }
}
