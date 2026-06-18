package com.paypulse.saga.definition

import com.paypulse.saga.model.StepType
import com.paypulse.saga.orchestrator.dsl.SagaDefinition
import com.paypulse.saga.orchestrator.dsl.saga
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

data class PaymentSagaData(
  val sagaId: UUID? = null,
  val sagaType: String? = null,
  val paymentId: UUID,
  val accountId: String,
  val amount: BigDecimal,
  val currency: String,
  val merchantId: String? = null,
  val fraudScore: Double? = null,
  val riskScore: Double? = null,
  val ledgerEventId: String? = null,
  val notificationId: String? = null,
)

@Configuration
class PaymentSagaConfiguration {

  @Bean
  fun paymentSaga(): SagaDefinition<PaymentSagaData> =
    saga<PaymentSagaData>("PaymentSaga") {
      step("FRAUD_CHECK") {
        type = StepType.COMPENSABLE
        participant = "fraud-check"
        command { data ->
          mapOf(
            "paymentId" to data.paymentId,
            "accountId" to data.accountId,
            "amount" to data.amount,
            "currency" to data.currency,
            "merchantId" to data.merchantId,
          )
        }
        onReply { data, reply -> data.copy(fraudScore = reply.get("score")?.asDouble()) }
        compensation { data -> mapOf("paymentId" to data.paymentId) }
        timeout = Duration.ofSeconds(10)
      }

      step("RISK_SCORING") {
        type = StepType.COMPENSABLE
        participant = "risk-scoring"
        command { data ->
          mapOf(
            "paymentId" to data.paymentId,
            "accountId" to data.accountId,
            "amount" to data.amount,
            "fraudScore" to data.fraudScore,
          )
        }
        onReply { data, reply -> data.copy(riskScore = reply.get("score")?.asDouble()) }
        compensation { data -> mapOf("paymentId" to data.paymentId, "riskScore" to data.riskScore) }
        timeout = Duration.ofSeconds(10)
      }

      step("LEDGER_APPLY") {
        type = StepType.PIVOT
        participant = "ledger-apply"
        command { data ->
          mapOf(
            "paymentId" to data.paymentId,
            "accountId" to data.accountId,
            "amount" to data.amount,
            "currency" to data.currency,
            "riskScore" to data.riskScore,
          )
        }
        onReply { data, reply -> data.copy(ledgerEventId = reply.get("ledgerEventId")?.asText()) }
        maxRetries = 5
        retryBackoff = Duration.ofSeconds(1)
        timeout = Duration.ofSeconds(30)
      }

      step("NOTIFY") {
        type = StepType.RETRYABLE
        participant = "notification"
        command { data ->
          mapOf(
            "paymentId" to data.paymentId,
            "accountId" to data.accountId,
            "ledgerEventId" to data.ledgerEventId,
          )
        }
        onReply { data, reply -> data.copy(notificationId = reply.get("notificationId")?.asText()) }
        maxRetries = 3
        retryBackoff = Duration.ofSeconds(2)
        timeout = Duration.ofSeconds(30)
      }
    }
}
