package com.paypulse.bff

import com.paypulse.bff.properties.BffProperties
import com.paypulse.bff.properties.HealthProbeProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(BffProperties::class, HealthProbeProperties::class)
class BffOpsApplication

fun main(args: Array<String>) {
  runApplication<BffOpsApplication>(*args)
}
