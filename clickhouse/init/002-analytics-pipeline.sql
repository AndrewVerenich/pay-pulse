-- S7 analytics pipeline: fraud_alerts + payment_metrics_hourly (+ saga.events for settlement latency).
-- payment.events уже ingested в 001-kafka-and-raw.sql — не дублируем.

-- ── fraud_alerts ──────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS paypulse_analytics.fraud_alerts_kafka (
  alertId String,
  userId String,
  paymentId String,
  score Float64,
  reasons Array(String),
  ruleId String,
  occurredAtEpochMs Int64
) ENGINE = Kafka
SETTINGS
  kafka_broker_list = 'kafka:9092',
  kafka_topic_list = 'fraud_alerts',
  kafka_group_name = 'clickhouse_fraud_alerts',
  kafka_format = 'JSONEachRow',
  kafka_num_consumers = 1,
  kafka_handle_error_mode = 'stream';

CREATE TABLE IF NOT EXISTS paypulse_analytics.fraud_alerts_raw (
  alert_id String,
  user_id String,
  payment_id String,
  score Float64,
  reasons Array(String),
  rule_id String,
  alert_date Date,
  occurred_at DateTime64(3, 'UTC')
) ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(alert_date)
ORDER BY (alert_date, user_id, alert_id)
TTL alert_date + INTERVAL 90 DAY;

CREATE MATERIALIZED VIEW IF NOT EXISTS paypulse_analytics.fraud_alerts_mv
TO paypulse_analytics.fraud_alerts_raw AS
SELECT
  alertId AS alert_id,
  userId AS user_id,
  paymentId AS payment_id,
  score,
  reasons,
  ruleId AS rule_id,
  toDate(fromUnixTimestamp64Milli(occurredAtEpochMs, 'UTC')) AS alert_date,
  fromUnixTimestamp64Milli(occurredAtEpochMs, 'UTC') AS occurred_at
FROM paypulse_analytics.fraud_alerts_kafka
WHERE length(_error) = 0;

-- ── payment_metrics_hourly (Flink → Kafka) ──────────────────────────────────
-- Отдельный ingest: Flink пишет агрегаты в payment_metrics_hourly, не в payment.events.

CREATE TABLE IF NOT EXISTS paypulse_analytics.payment_metrics_hourly_kafka (
  windowStartEpochMs Int64,
  windowEndEpochMs Int64,
  currency String,
  count Int64,
  totalAmount Float64
) ENGINE = Kafka
SETTINGS
  kafka_broker_list = 'kafka:9092',
  kafka_topic_list = 'payment_metrics_hourly',
  kafka_group_name = 'clickhouse_payment_metrics_hourly',
  kafka_format = 'JSONEachRow',
  kafka_num_consumers = 1,
  kafka_handle_error_mode = 'stream';

CREATE TABLE IF NOT EXISTS paypulse_analytics.payment_metrics_hourly_raw (
  window_start DateTime64(3, 'UTC'),
  window_end DateTime64(3, 'UTC'),
  metric_date Date,
  currency LowCardinality(String),
  payment_count Int64,
  total_amount Float64
) ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(metric_date)
ORDER BY (metric_date, currency, window_start)
TTL metric_date + INTERVAL 180 DAY;

CREATE MATERIALIZED VIEW IF NOT EXISTS paypulse_analytics.payment_metrics_hourly_mv
TO paypulse_analytics.payment_metrics_hourly_raw AS
SELECT
  fromUnixTimestamp64Milli(windowStartEpochMs, 'UTC') AS window_start,
  fromUnixTimestamp64Milli(windowEndEpochMs, 'UTC') AS window_end,
  toDate(fromUnixTimestamp64Milli(windowStartEpochMs, 'UTC')) AS metric_date,
  currency,
  count AS payment_count,
  totalAmount AS total_amount
FROM paypulse_analytics.payment_metrics_hourly_kafka
WHERE length(_error) = 0;

-- ── saga.events (для mart_settlement_latency) ─────────────────────────────────

CREATE TABLE IF NOT EXISTS paypulse_analytics.saga_events_kafka (
  sagaId String,
  sagaType String,
  eventType String,
  status String,
  stepName Nullable(String),
  paymentId Nullable(String),
  attempt Int32,
  occurredAt String
) ENGINE = Kafka
SETTINGS
  kafka_broker_list = 'kafka:9092',
  kafka_topic_list = 'saga.events',
  kafka_group_name = 'clickhouse_saga_events',
  kafka_format = 'JSONEachRow',
  kafka_num_consumers = 1,
  kafka_handle_error_mode = 'stream';

CREATE TABLE IF NOT EXISTS paypulse_analytics.saga_events_raw (
  saga_id String,
  saga_type String,
  event_type String,
  status String,
  step_name Nullable(String),
  payment_id Nullable(String),
  attempt Int32,
  event_date Date,
  occurred_at DateTime64(3, 'UTC')
) ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(event_date)
ORDER BY (saga_id, occurred_at, event_type)
TTL event_date + INTERVAL 90 DAY;

CREATE MATERIALIZED VIEW IF NOT EXISTS paypulse_analytics.saga_events_mv
TO paypulse_analytics.saga_events_raw AS
SELECT
  sagaId AS saga_id,
  sagaType AS saga_type,
  eventType AS event_type,
  status,
  stepName AS step_name,
  paymentId AS payment_id,
  attempt,
  toDate(parseDateTime64BestEffortOrZero(occurredAt, 3)) AS event_date,
  parseDateTime64BestEffortOrZero(occurredAt, 3) AS occurred_at
FROM paypulse_analytics.saga_events_kafka
WHERE length(_error) = 0;
