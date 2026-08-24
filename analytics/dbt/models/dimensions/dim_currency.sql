{{
  config(
    materialized='table',
    tags=['dimensions']
  )
}}

select
  {{ dbt_utils.generate_surrogate_key(['currency']) }} as currency_sk,
  currency as currency_code
from {{ ref('int_currencies') }}
