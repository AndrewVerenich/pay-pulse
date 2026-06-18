package com.paypulse.generator

import com.paypulse.generator.config.GeneratorProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(GeneratorProperties::class)
class PaymentGeneratorApplication

fun main(args: Array<String>) {
  runApplication<PaymentGeneratorApplication>(*args)
}
