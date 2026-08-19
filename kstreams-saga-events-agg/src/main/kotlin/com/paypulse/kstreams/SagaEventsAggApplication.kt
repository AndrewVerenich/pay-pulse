package com.paypulse.kstreams

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SagaEventsAggApplication

fun main(args: Array<String>) {
  runApplication<SagaEventsAggApplication>(*args)
}
