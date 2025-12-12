package com.orderbook.orderbook
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant


@Serializable
data class Order(
  val id: String,
  val type: OrderType,
  val price: Double,
  var quantity: Double,
  val createdAt: Instant,
  val currencyPair: String,
  val customerOrderId: String,
)
