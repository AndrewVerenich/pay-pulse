select
  account_id,
  min(occurred_at) as first_seen_at,
  max(occurred_at) as last_seen_at,
  count() as payment_count
from {{ ref('stg_payment_events') }}
group by account_id
