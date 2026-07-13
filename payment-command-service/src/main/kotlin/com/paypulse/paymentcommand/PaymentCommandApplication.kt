package com.paypulse.paymentcommand

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableR2dbcRepositories(basePackages = ["com.paypulse"])
@EnableTransactionManagement
@EnableScheduling
class PaymentCommandApplication

fun main(args: Array<String>) {
  runApplication<PaymentCommandApplication>(*args)
}
