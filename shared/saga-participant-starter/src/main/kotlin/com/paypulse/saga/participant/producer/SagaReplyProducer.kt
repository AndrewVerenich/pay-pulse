package com.paypulse.saga.participant.producer

import com.paypulse.saga.model.SagaReply
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate

class SagaReplyProducer(
  private val kafkaTemplate: KafkaTemplate<String, SagaReply>,
  private val replyTopic: String
) {
  private val log = LoggerFactory.getLogger(SagaReplyProducer::class.java)

  fun sendReply(reply: SagaReply) {
    log.info("Sending reply to topic={} sagaId={} step={} status={}",
      replyTopic, reply.sagaId, reply.stepName, reply.status)
    kafkaTemplate.send(replyTopic, reply.sagaId.toString(), reply)
  }
}
