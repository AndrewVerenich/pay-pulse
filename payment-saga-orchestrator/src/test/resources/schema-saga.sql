CREATE SCHEMA IF NOT EXISTS saga;

CREATE TABLE saga.saga_instance (
  id           BIGSERIAL PRIMARY KEY,
  saga_id      UUID         NOT NULL UNIQUE,
  saga_type    VARCHAR(100) NOT NULL,
  status       VARCHAR(30)  NOT NULL DEFAULT 'STARTED',
  current_step VARCHAR(100),
  payload      JSONB        NOT NULL,
  created_at   TIMESTAMPTZ(3) NOT NULL DEFAULT (timezone('utc', now())),
  updated_at   TIMESTAMPTZ(3) NOT NULL DEFAULT (timezone('utc', now())),
  completed_at TIMESTAMPTZ(3)
);

CREATE INDEX idx_saga_instance_status ON saga.saga_instance (status);
CREATE INDEX idx_saga_instance_type ON saga.saga_instance (saga_type);
CREATE INDEX idx_saga_instance_created ON saga.saga_instance (created_at DESC);

CREATE TABLE saga.saga_step (
  id               BIGSERIAL PRIMARY KEY,
  saga_instance_id BIGINT       NOT NULL REFERENCES saga.saga_instance (id),
  step_name        VARCHAR(100) NOT NULL,
  step_type        VARCHAR(30)  NOT NULL,
  step_order       INT          NOT NULL,
  status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
  command_payload  JSONB,
  reply_payload    JSONB,
  error_message    TEXT,
  retry_count      INT          NOT NULL DEFAULT 0,
  started_at       TIMESTAMPTZ(3),
  completed_at     TIMESTAMPTZ(3),
  CONSTRAINT uk_saga_step_instance_name UNIQUE (saga_instance_id, step_name)
);

CREATE INDEX idx_saga_step_instance ON saga.saga_step (saga_instance_id);
CREATE INDEX idx_saga_step_status ON saga.saga_step (status);

CREATE TABLE saga.compensation_failure (
  saga_id    UUID PRIMARY KEY,
  reason     TEXT NOT NULL,
  payload    TEXT,
  created_at TIMESTAMPTZ(3) NOT NULL DEFAULT (timezone('utc', now())),
  resolved   BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_compensation_failure_unresolved ON saga.compensation_failure (resolved, created_at DESC);
