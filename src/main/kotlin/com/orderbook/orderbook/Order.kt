package com.orderbook.orderbook
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/* tag::order-class[]
[[Order]]
- id: Unique identifier for the order.
- type: Type of the order (BUY or SELL).
- price: Price at which the order is placed.
- quantity: Quantity of the asset to be traded.
- createdAt: Timestamp when the order was created.
- currencyPair: The trading pair for the order (e.g., BTC-USD).
- customerOrderId: Custom identifier provided by the customer for tracking.
end::order-class[] */
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
