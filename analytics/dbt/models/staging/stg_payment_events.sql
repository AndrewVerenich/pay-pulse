select
  event_id,
  payment_id,
  account_id,
  amount,
  currency,
  coalesce(nullIf(merchant_id, ''), 'unknown') as merchant_id,
  occurred_at,
  toDate(occurred_at) as event_date
from {{ source('raw', 'payment_events_raw') }}
