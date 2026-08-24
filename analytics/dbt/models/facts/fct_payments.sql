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
  p.payment_sk as payment_sk,
  p.event_id as event_id,
  p.payment_id as payment_id,
  p.account_id as account_id,
  p.merchant_id as merchant_id,
  p.currency as currency,
  p.amount as amount,
  p.occurred_at as occurred_at,
  p.event_date as event_date,
  a.account_sk as account_sk,
  m.merchant_sk as merchant_sk,
  c.currency_sk as currency_sk
from payments_sk as p
inner join {{ ref('dim_account') }} as a on p.account_id = a.account_id
left join {{ ref('dim_merchant') }} as m on p.merchant_id = m.merchant_id
inner join {{ ref('dim_currency') }} as c on p.currency = c.currency_code
