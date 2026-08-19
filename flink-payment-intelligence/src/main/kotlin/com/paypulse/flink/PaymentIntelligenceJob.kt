package com.paypulse.flink

import com.paypulse.flink.config.FlinkJobProperties
import com.paypulse.flink.enrich.UserRiskEnricher
import com.paypulse.flink.io.DEAD_LETTER_TAG
import com.paypulse.flink.io.EventParser
import com.paypulse.flink.io.PaymentJson
import com.paypulse.flink.metrics.HourlyMetricsFunction
import com.paypulse.flink.model.DeadLetter
import com.paypulse.flink.model.EnrichedPayment
import com.paypulse.flink.model.FraudAlert
import com.paypulse.flink.model.FraudRule
import com.paypulse.flink.model.PaymentEvent
import com.paypulse.flink.model.PaymentMetrics
import com.paypulse.flink.model.UserFraudScore
import com.paypulse.flink.model.UserRiskProfile
import com.paypulse.flink.scoring.FraudDetectionFunction
import com.paypulse.flink.sink.KafkaSinks
import com.paypulse.flink.sink.KeyedJson
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend
import org.apache.flink.runtime.state.storage.FileSystemCheckpointStorage
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.CheckpointConfig
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import java.time.Duration

object PaymentIntelligenceJob {

  data class PipelineOutputs(
    val alerts: DataStream<FraudAlert>,
    val scores: DataStream<UserFraudScore>,
    val metrics: DataStream<PaymentMetrics>,
  )

  /**
   * Чистая сборка графа детекции из уже распарсенного потока платежей и broadcast-потоков.
   * Вынесена отдельно, чтобы тест мог подать `env.fromElements(...)` вместо Kafka.
   */
  fun buildDetection(
    payments: DataStream<PaymentEvent>,
    rules: DataStream<FraudRule>,
    profiles: DataStream<UserRiskProfile>,
  ): PipelineOutputs {
    val enriched = payments
      .connect(profiles.broadcast(UserRiskEnricher.PROFILE_STATE))
      .process(UserRiskEnricher())
      .uid("user-risk-enricher")
      .name("user-risk-enricher")

    val detection = enriched
      .keyBy(KeySelector { it.event.accountId }, TypeInformation.of(String::class.java))
      .connect(rules.broadcast(FraudDetectionFunction.RULES_STATE))
      .process(FraudDetectionFunction())
      .uid("fraud-scorer")
      .name("fraud-scorer")

    val scores = detection.getSideOutput(FraudDetectionFunction.USER_SCORE_TAG)

    val metrics = payments
      .keyBy(KeySelector { it.currency }, TypeInformation.of(String::class.java))
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      .process(HourlyMetricsFunction())
      .uid("hourly-metrics")
      .name("hourly-metrics")

    return PipelineOutputs(detection, scores, metrics)
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val props = FlinkJobProperties.fromEnv()
    val env = StreamExecutionEnvironment.getExecutionEnvironment()
    configureCheckpointing(env, props)

    val rawPayments = env.fromSource(
      stringSource(props.bootstrapServers, props.paymentEventsTopic, props.consumerGroup),
      WatermarkStrategy.noWatermarks(),
      "payment-events-source",
    ).uid("payment-events-src")

    val parser = rawPayments.process(EventParser()).uid("event-parser").name("event-parser")

    val payments = parser
      .assignTimestampsAndWatermarks(
        WatermarkStrategy
          .forBoundedOutOfOrderness<PaymentEvent>(Duration.ofSeconds(5))
          .withIdleness(Duration.ofSeconds(30))
          .withTimestampAssigner(SerializableTimestampAssigner { e, _ -> e.occurredAtEpochMs }),
      )

    val rules = env.fromSource(
      stringSource(props.bootstrapServers, props.fraudRulesTopic, "${props.consumerGroup}-rules", OffsetsInitializer.earliest()),
      WatermarkStrategy.noWatermarks(),
      "fraud-rules-source",
    ).uid("rules-broadcast-src")
      .map(MapFunction<String, FraudRule> { PaymentJson.parseRule(it) })
      .returns(FraudRule::class.java)

    val profiles = env.fromSource(
      stringSource(props.bootstrapServers, props.userRiskProfilesTopic, "${props.consumerGroup}-profiles", OffsetsInitializer.earliest()),
      WatermarkStrategy.noWatermarks(),
      "user-risk-profiles-source",
    ).uid("profiles-broadcast-src")
      .map(MapFunction<String, UserRiskProfile> { PaymentJson.parseProfile(it) })
      .returns(UserRiskProfile::class.java)

    val outputs = buildDetection(payments, rules, profiles)

    outputs.alerts
      .map(MapFunction<FraudAlert, KeyedJson> { KeyedJson(it.userId, KafkaSinks.toJson(it)) })
      .returns(KeyedJson::class.java)
      .sinkTo(KafkaSinks.jsonSink(props.bootstrapServers, props.fraudAlertsTopic))
      .uid("fraud-alerts-sink")

    outputs.scores
      .map(MapFunction<UserFraudScore, KeyedJson> { KeyedJson(it.userId, KafkaSinks.toJson(it)) })
      .returns(KeyedJson::class.java)
      .sinkTo(KafkaSinks.jsonSink(props.bootstrapServers, props.userFraudScoresTopic))
      .uid("user-fraud-scores-sink")

    outputs.metrics
      .map(MapFunction<PaymentMetrics, KeyedJson> { KeyedJson("${it.windowStartEpochMs}:${it.currency}", KafkaSinks.toJson(it)) })
      .returns(KeyedJson::class.java)
      .sinkTo(KafkaSinks.jsonSink(props.bootstrapServers, props.paymentMetricsTopic))
      .uid("payment-metrics-sink")

    parser.getSideOutput(DEAD_LETTER_TAG)
      .map(MapFunction<DeadLetter, KeyedJson> { KeyedJson(null, KafkaSinks.toJson(it)) })
      .returns(KeyedJson::class.java)
      .sinkTo(KafkaSinks.jsonSink(props.bootstrapServers, props.deadLetterTopic))
      .uid("dead-letter-sink")

    env.execute("PaymentIntelligenceJob")
  }

  private fun stringSource(
    bootstrap: String,
    topic: String,
    group: String,
    offsets: OffsetsInitializer = OffsetsInitializer.committedOffsets(org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST),
  ): KafkaSource<String> =
    KafkaSource.builder<String>()
      .setBootstrapServers(bootstrap)
      .setTopics(topic)
      .setGroupId(group)
      .setStartingOffsets(offsets)
      .setValueOnlyDeserializer(SimpleStringSchema())
      .build()

  private fun configureCheckpointing(env: StreamExecutionEnvironment, props: FlinkJobProperties) {
    env.enableCheckpointing(60_000L)
    env.checkpointConfig.minPauseBetweenCheckpoints = 30_000L
    env.checkpointConfig.maxConcurrentCheckpoints = 1
    env.checkpointConfig.externalizedCheckpointCleanup =
      CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
    env.setStateBackend(HashMapStateBackend())
    env.checkpointConfig.checkpointStorage = FileSystemCheckpointStorage(props.checkpointDir)
  }
}
