package com.orderbook.orderbook

import kotlinx.serialization.Serializable

@Serializable
data class OrderLimitRequest(
  val side: OrderType,
  val price: Double,
  val quantity: Double,
  val postOnly: Boolean? = false,
  val currencyPair: String = "BTC-USD",
  val timeInForce: TimeInForce? = TimeInForce.GTC,
  val customerOrderId: String? = null,
  val allowMargin: Boolean? = false,
  val reduceOnly: Boolean? = false,
)
