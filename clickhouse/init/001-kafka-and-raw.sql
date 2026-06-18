CREATE DATABASE IF NOT EXISTS paypulse_analytics;

CREATE TABLE IF NOT EXISTS paypulse_analytics.payment_events_kafka (
  eventId String,
  paymentId String,
  accountId String,
  amount Decimal(19, 4),
  currency LowCardinality(String),
  merchantId Nullable(String),
  occurredAt String
) ENGINE = Kafka
SETTINGS
  kafka_broker_list = 'kafka:9092',
  kafka_topic_list = 'payment.events',
  kafka_group_name = 'clickhouse_payment_events',
  kafka_format = 'JSONEachRow',
  kafka_num_consumers = 1,
  kafka_handle_error_mode = 'stream';

CREATE TABLE IF NOT EXISTS paypulse_analytics.payment_events_raw (
  event_id String,
  payment_id String,
  account_id String,
  amount Decimal(19, 4),
  currency LowCardinality(String),
  merchant_id Nullable(String),
  occurred_at DateTime64(3, 'UTC')
) ENGINE = ReplacingMergeTree
ORDER BY (account_id, occurred_at, event_id);

CREATE MATERIALIZED VIEW IF NOT EXISTS paypulse_analytics.payment_events_mv
TO paypulse_analytics.payment_events_raw AS
SELECT
  eventId                                          AS event_id,
  paymentId                                        AS payment_id,
  accountId                                        AS account_id,
  amount                                           AS amount,
  currency                                         AS currency,
  merchantId                                       AS merchant_id,
  parseDateTime64BestEffortOrZero(occurredAt, 3)   AS occurred_at
FROM paypulse_analytics.payment_events_kafka
WHERE length(_error) = 0;

CREATE TABLE IF NOT EXISTS paypulse_analytics.payment_events_dlq (
  topic String,
  partition UInt64,
  offset UInt64,
  raw_message String,
  error String,
  ingested_at DateTime DEFAULT now()
) ENGINE = MergeTree
ORDER BY (topic, partition, offset);

CREATE MATERIALIZED VIEW IF NOT EXISTS paypulse_analytics.payment_events_dlq_mv
TO paypulse_analytics.payment_events_dlq AS
SELECT
  _topic                                           AS topic,
  _partition                                       AS partition,
  _offset                                          AS offset,
  _raw_message                                     AS raw_message,
  _error                                           AS error,
  now()                                            AS ingested_at
FROM paypulse_analytics.payment_events_kafka
WHERE length(_error) > 0;
