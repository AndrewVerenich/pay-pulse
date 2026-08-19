package com.paypulse.opsui

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono

@Configuration
class UiRoutesConfig(
  @Value("\${paypulse.auth-gateway-uri:http://localhost:8090}") private val gatewayUri: String,
) {

  @Bean
  fun webClient(): WebClient = WebClient.builder().build()

  @Bean
  fun uiRoutes(webClient: WebClient): RouterFunction<ServerResponse> = router {
    path("/api/**") { req -> proxy(webClient, req) }
    path("/auth/**") { req -> proxy(webClient, req) }
    resources("/assets/**", ClassPathResource("static/assets/"))
    GET("/") { index() }
    GET("/**") { req ->
      val path = req.path()
      if (path.startsWith("/api") || path.startsWith("/auth") || path.startsWith("/actuator")) {
        ServerResponse.notFound().build()
      } else {
        index()
      }
    }
  }

  private fun index(): Mono<ServerResponse> =
    ServerResponse.ok()
      .contentType(MediaType.TEXT_HTML)
      .body(BodyInserters.fromResource(ClassPathResource("static/index.html")))

  private fun proxy(webClient: WebClient, req: ServerRequest): Mono<ServerResponse> {
    val query = req.uri().rawQuery?.let { "?$it" } ?: ""
    val target = "$gatewayUri${req.path()}$query"
    val wantsSse = req.headers().header(HttpHeaders.ACCEPT)
      .any { it.contains(MediaType.TEXT_EVENT_STREAM_VALUE, ignoreCase = true) }

    return DataBufferUtils.join(req.bodyToFlux(DataBuffer::class.java))
      .defaultIfEmpty(req.exchange().response.bufferFactory().wrap(ByteArray(0)))
      .flatMap { requestBody ->
        val bodyBytes = ByteArray(requestBody.readableByteCount()).also { requestBody.read(it) }
        DataBufferUtils.release(requestBody)

        webClient
          .method(req.method())
          .uri(target)
          .headers { headers ->
            req.headers().asHttpHeaders().forEach { name, values ->
              if (!HttpHeaders.HOST.equals(name, ignoreCase = true) &&
                !HttpHeaders.CONTENT_LENGTH.equals(name, ignoreCase = true)
              ) {
                headers[name] = values
              }
            }
          }
          .bodyValue(bodyBytes)
          .exchangeToMono { upstream ->
            val responseHeaders = HttpHeaders()
            upstream.headers().asHttpHeaders().forEach { name, values ->
              if (!HttpHeaders.TRANSFER_ENCODING.equals(name, ignoreCase = true) &&
                !HttpHeaders.CONTENT_LENGTH.equals(name, ignoreCase = true)
              ) {
                responseHeaders[name] = values
              }
            }
            val builder = ServerResponse.status(upstream.statusCode()).headers { it.addAll(responseHeaders) }

            if (wantsSse || req.path().startsWith("/api/live/")) {
              builder.body(
                upstream.bodyToFlux(DataBuffer::class.java),
                DataBuffer::class.java,
              )
            } else {
              DataBufferUtils.join(upstream.bodyToFlux(DataBuffer::class.java))
                .defaultIfEmpty(req.exchange().response.bufferFactory().wrap(ByteArray(0)))
                .flatMap { buf ->
                  val bytes = ByteArray(buf.readableByteCount()).also { buf.read(it) }
                  DataBufferUtils.release(buf)
                  builder.bodyValue(bytes)
                }
            }
          }
      }
  }
}
