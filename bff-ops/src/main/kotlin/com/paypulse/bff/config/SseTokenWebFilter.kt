package com.paypulse.bff.config

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class SseTokenWebFilter : WebFilter {

  override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
    val request = exchange.request
    val path = request.path.value()
    if (!path.startsWith("/api/live/")) {
      return chain.filter(exchange)
    }

    val token = request.queryParams.getFirst("token")
    if (token.isNullOrBlank() || request.headers.containsKey(HttpHeaders.AUTHORIZATION)) {
      return chain.filter(exchange)
    }

    val mutated = request.mutate()
      .headers { headers -> headers.set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
      .build()
    return chain.filter(exchange.mutate().request(mutated).build())
  }
}
