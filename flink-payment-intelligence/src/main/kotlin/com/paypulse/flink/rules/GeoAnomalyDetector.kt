package com.paypulse.flink.rules

/**
 * Stub гео-аномалии (S4): признак "иностранного" мерчанта по соглашению об именовании
 * `merchantId` содержит суффикс `:foreign`. В реальной системе — geo-IP / BIN lookup.
 */
object GeoAnomalyDetector {
  fun isForeign(merchantId: String?): Boolean =
    merchantId?.contains(":foreign", ignoreCase = true) == true
}
