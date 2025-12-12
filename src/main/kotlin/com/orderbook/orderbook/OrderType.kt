package com.orderbook.orderbook

import kotlinx.serialization.Serializable

@Serializable
enum class OrderType{
  BUY,
  SELL
}

