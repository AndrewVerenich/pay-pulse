{{
  config(
    materialized='table',
    tags=['facts']
  )
}}

with payments_sk as (
  select
    *,
    {{ dbt_utils.generate_surrogate_key(['event_id']) }} as payment_sk
  from {{ ref('stg_payment_events') }}
)
select
  p.payment_sk,
  p.event_id,
  p.payment_id,
  p.account_id,
  p.merchant_id,
  p.currency,
  p.amount,
  p.occurred_at,
  p.event_date,
  a.account_sk,
  m.merchant_sk,
  c.currency_sk
from payments_sk as p
inner join {{ ref('dim_account') }} as a on p.account_id = a.account_id
left join {{ ref('dim_merchant') }} as m on p.merchant_id = m.merchant_id
inner join {{ ref('dim_currency') }} as c on p.currency = c.currency_code
