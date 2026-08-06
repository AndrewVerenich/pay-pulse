package com.paypulse.bff.model

import java.time.Instant

data class RuleAck(
  val ruleId: String,
  val version: Long,
  val appliedAt: Instant = Instant.now(),
)
