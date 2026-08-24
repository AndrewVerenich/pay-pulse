-- AML structuring: несколько платежей ниже порога в один день с суммой выше порога.
select
  account_id,
  event_date,
  count() as payment_count,
  sum(amount) as daily_total,
  max(amount) as max_single_amount
from {{ ref('fct_payments') }}
where amount < {{ var('structuring_threshold') }}
group by account_id, event_date
having payment_count >= 3 and daily_total >= {{ var('structuring_threshold') }}
