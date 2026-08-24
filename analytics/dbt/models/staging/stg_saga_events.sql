select
  saga_id,
  saga_type,
  event_type,
  status,
  step_name,
  payment_id,
  attempt,
  event_date,
  occurred_at
from {{ source('raw', 'saga_events_raw') }}
