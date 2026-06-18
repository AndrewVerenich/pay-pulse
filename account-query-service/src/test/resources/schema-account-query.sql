CREATE SCHEMA IF NOT EXISTS account_query;

CREATE TABLE account_query.balance_events (
  id BIGSERIAL PRIMARY KEY,
  source_event_id VARCHAR(64) NOT NULL UNIQUE,
  account_id VARCHAR(255) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  delta NUMERIC(19, 4) NOT NULL,
  balance_after NUMERIC(19, 4) NOT NULL,
  occurred_at TIMESTAMPTZ(3) NOT NULL,
  aggregate_id UUID NOT NULL
);

CREATE INDEX idx_balance_events_account_time ON account_query.balance_events (account_id, occurred_at);

CREATE TABLE account_query.account_balance (
  account_id VARCHAR(255) NOT NULL,
  currency VARCHAR(16) NOT NULL,
  balance NUMERIC(19, 4) NOT NULL,
  last_occurred_at TIMESTAMPTZ(3) NOT NULL,
  last_source_event_id VARCHAR(64) NOT NULL,
  PRIMARY KEY (account_id, currency)
);

INSERT INTO account_query.balance_events
  (source_event_id, account_id, currency, delta, balance_after, occurred_at, aggregate_id)
VALUES
  ('00000000-0000-0000-0000-0000000000a1', 'acc-test', 'USD', 10.00, 10.00, '2026-01-10T10:00:00Z', '00000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-0000000000a2', 'acc-test', 'USD', 5.00, 15.00, '2026-01-11T10:00:00Z', '00000000-0000-0000-0000-000000000002');

INSERT INTO account_query.account_balance
  (account_id, currency, balance, last_occurred_at, last_source_event_id)
VALUES
  ('acc-test', 'USD', 15.00, '2026-01-11T10:00:00Z', '00000000-0000-0000-0000-0000000000a2');
