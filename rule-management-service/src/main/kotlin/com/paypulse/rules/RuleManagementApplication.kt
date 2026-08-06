package com.paypulse.rules

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RuleManagementApplication

fun main(args: Array<String>) {
  runApplication<RuleManagementApplication>(*args)
}
