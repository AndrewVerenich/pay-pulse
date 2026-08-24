{{
  config(
    materialized='table',
    tags=['dimensions']
  )
}}

select
  {{ dbt_utils.generate_surrogate_key(['merchant_id']) }} as merchant_sk,
  merchant_id,
  first_seen_at,
  last_seen_at,
  payment_count,
  total_amount,
  toDateTime('1970-01-01 00:00:00') as valid_from,
  toDateTime('2099-12-31 23:59:59') as valid_to,
  toUInt8(1) as is_current
from {{ ref('int_merchants_latest') }}
