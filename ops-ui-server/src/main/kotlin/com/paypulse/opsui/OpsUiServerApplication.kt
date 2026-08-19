package com.paypulse.opsui

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OpsUiServerApplication

fun main(args: Array<String>) {
  runApplication<OpsUiServerApplication>(*args)
}
