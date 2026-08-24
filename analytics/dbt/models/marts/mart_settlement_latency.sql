-- Settlement latency: время от SAGA_STARTED до SAGA_COMPLETED по saga_id.
-- Источник: saga.events в ClickHouse (002-analytics-pipeline.sql).
with started as (
  select saga_id, min(occurred_at) as started_at
  from {{ ref('stg_saga_events') }}
  where event_type = 'SAGA_STARTED'
  group by saga_id
),
completed as (
  select saga_id, min(occurred_at) as completed_at
  from {{ ref('stg_saga_events') }}
  where event_type = 'SAGA_COMPLETED'
  group by saga_id
)
select
  s.saga_id,
  s.started_at,
  c.completed_at,
  dateDiff('second', s.started_at, c.completed_at) as latency_seconds
from started as s
inner join completed as c on s.saga_id = c.saga_id
