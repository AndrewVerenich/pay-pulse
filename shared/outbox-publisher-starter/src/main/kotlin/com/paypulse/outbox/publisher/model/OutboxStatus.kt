package com.paypulse.outbox.publisher.model

enum class OutboxStatus {
  PENDING,
  PROCESSED,
  FAILED,
}
