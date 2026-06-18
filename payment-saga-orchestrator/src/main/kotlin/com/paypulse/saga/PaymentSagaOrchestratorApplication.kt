package com.paypulse.saga

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PaymentSagaOrchestratorApplication

fun main(args: Array<String>) {
  runApplication<PaymentSagaOrchestratorApplication>(*args)
}
