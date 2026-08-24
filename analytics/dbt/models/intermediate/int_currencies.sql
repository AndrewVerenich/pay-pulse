select
  currency,
  count() as payment_count
from {{ ref('stg_payment_events') }}
group by currency
