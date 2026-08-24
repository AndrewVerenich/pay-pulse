{{
  config(
    materialized='table',
    tags=['dimensions']
  )
}}

select
  {{ dbt_utils.generate_surrogate_key(['account_id']) }} as account_sk,
  account_id,
  first_seen_at,
  last_seen_at,
  payment_count
from {{ ref('int_accounts_latest') }}
