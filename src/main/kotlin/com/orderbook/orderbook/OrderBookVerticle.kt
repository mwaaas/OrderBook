package com.orderbook.orderbook

import io.vertx.core.Future
import io.vertx.core.VerticleBase
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import kotlinx.datetime.Clock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentSkipListMap

class OrderBookVerticle : VerticleBase() {

  private val logger = LoggerFactory.getLogger(OrderBookVerticle::class.java)

  // using map where key is price optimizing for execution of trades but expensive for getting particular order by id
  // to optimize for both we can maintain another map of orderId to Order that would double the memory usage though.
  // if it was on real database would have relied on indexes.
  internal val buyOrders = ConcurrentSkipListMap<Double, MutableList<Order>>(compareByDescending { it })
  internal val sellOrders = ConcurrentSkipListMap<Double, MutableList<Order>>(compareBy {it })
  internal val tradeHistory = mutableListOf<Trade>()

  fun executeTrade() : List<Trade> {
    val trades = mutableListOf<Trade>()

    // we match buy orders with sell orders.
    val buyIterator = buyOrders.entries.iterator()
    while (buyIterator.hasNext()) {
      val (buyPrice, buyOrderList) = buyIterator.next()
      val sellIterator = sellOrders.entries.iterator()
      while (sellIterator.hasNext() && buyOrderList.isNotEmpty()) {
        val (sellPrice, sellOrderList) = sellIterator.next()
        if (buyPrice >= sellPrice) {
          // We have a match
          val buyOrder = buyOrderList.first()
          val sellOrder = sellOrderList.first()

          val tradeQuantity = minOf(buyOrder.quantity, sellOrder.quantity)
          val takerSide = if (buyOrder.createdAt < sellOrder.createdAt) OrderType.SELL else OrderType.BUY
          val trade = Trade(
            id = java.util.UUID.randomUUID().toString(),
            buyOrderId = buyOrder.id,
            sellOrderId = sellOrder.id,
            takerSide = takerSide,
            price = sellPrice,
            quantity = tradeQuantity,
            currencyPair = sellOrder.currencyPair,
            quoteVolume = tradeQuantity * sellPrice,
            tradedAt = Clock.System.now()
          )
          trades.add(trade)

          // Update quantities
          buyOrder.quantity -= tradeQuantity
          sellOrder.quantity -= tradeQuantity

          // Remove orders if fully filled
          if (buyOrder.quantity <= 0) {
            buyOrderList.removeAt(0)
          }
          if (sellOrder.quantity <= 0) {
            sellOrderList.removeAt(0)
          }

          // Clean up empty price levels
          if (sellOrderList.isEmpty()) {
            sellIterator.remove()
          }

          if (buyOrderList.isEmpty()) {
            buyIterator.remove()
          }
        } else {
          // No more matches possible at this buy price
          break
        }
      }
    }

    // add trades to trade history
    tradeHistory.addAll(trades)
    return trades
  }

  fun addOrder(limitRequest: OrderLimitRequest): Order {
    val order = Order(
      id = java.util.UUID.randomUUID().toString(),
      type = limitRequest.side,
      price = limitRequest.price,
      quantity = limitRequest.quantity,
      currencyPair = limitRequest.currencyPair,
      customerOrderId = limitRequest.customerOrderId ?: "",
      createdAt = Clock.System.now()
    )

    when (limitRequest.side) {
      OrderType.BUY -> {
        val ordersAtPrice = buyOrders.getOrPut(limitRequest.price) { mutableListOf() }
        ordersAtPrice.add(order)
      }
      OrderType.SELL -> {
        val ordersAtPrice = sellOrders.getOrPut(limitRequest.price) { mutableListOf() }
        ordersAtPrice.add(order)
      }
    }

    vertx.eventBus().publish("order.added", order.id)
    return order;
  }

  /* tag::get-orderbook[]
   GET /orderbook

   Returns the current order book snapshot.

   See <<Order>> for details on the order structure.

   Sample response:
   Response 200 (application/json):
   {
       "bids": [],
       "asks": []
   }
  end::get-orderbook[] */
  fun orderBookApiHandler(context: RoutingContext)
  {
    val orderBook = mapOf(
      "Asks" to sellOrders.toMap(),
      "Bids" to buyOrders.toMap()
    )
    val responseBody = Json.encodeToString(orderBook)
    context.response()
      .putHeader("content-type", "application/json")
      .end(responseBody)
  }

  fun limitOrderApiHandler(context: RoutingContext)
  {
    try{
      var limitRequest = Json.decodeFromString<OrderLimitRequest>(
        context.body().asString())

      val order = addOrder(limitRequest)
      context.response()
        .putHeader("content-type", "application/json")
        .setStatusCode(201)
        .end(io.vertx.core.json.Json.encodePrettily(mapOf("id" to order.id)))

    }
    catch (e: SerializationException){
      context.response()
        .setStatusCode(400)
        .putHeader("content-type", "application/json")
        .end("Bad Request: ${e.message}");
    }
    catch (e: Exception){
      context.response()
        .setStatusCode(500)
        .putHeader("content-type", "application/json")
        .end("Internal Server Error: ${e.message}");
    }
  }

  fun tradeHistoryApiHandler(context: RoutingContext)
  {
    val responseBody = Json.encodeToString(tradeHistory)
    context.response()
      .putHeader("content-type", "application/json")
      .end(responseBody)
  }


  override fun start() : Future<*> {
    logger.info("Starting verticle")

    vertx.eventBus().consumer<String>("order.added") { message ->
      logger.info("Order added with id: ${message.body()}. Executing trades...")
      val trades = executeTrade()
      logger.info("Executed ${trades.size} trades.")
    }
    val router = Router.router(vertx)
    router.route().handler(BodyHandler.create());

    router.get("/orderbook").handler(::orderBookApiHandler)

    router.post("/order/limit").handler(::limitOrderApiHandler)

    router.get("/tradehistory").handler(::tradeHistoryApiHandler)

    val port = config().getInteger("http.port", 8000)
    return vertx
      .createHttpServer()
      .requestHandler(router)
      .listen(port).onSuccess { http ->
        logger.info("HTTP server started on port ${http.actualPort()}")
    }
  }
}
