package com.orderbook.orderbook

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant


@Serializable
data class Trade(
  val price: Double,
  val quantity: Double,
  val currencyPair: String,
  val tradedAt: Instant,
  val takerSide: OrderType,
  val id: String,
  val quoteVolume: Double,
  val buyOrderId: String,
  val sellOrderId: String
)
