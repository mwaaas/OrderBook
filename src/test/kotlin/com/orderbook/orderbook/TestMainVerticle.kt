package com.orderbook.orderbook

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlinx.datetime.Instant

@ExtendWith(VertxExtension::class)
class TestMainVerticle {

  @BeforeEach
  fun deploy_verticle(vertx: Vertx, testContext: VertxTestContext) {
    val config = JsonObject().put("http.port", 7005)
    vertx.deployVerticle(OrderBookVerticle(), DeploymentOptions().setConfig(config))
      .onComplete(
        testContext.succeeding<String>
        { _ -> testContext.completeNow() })
  }

  @Test
  fun test_executing_trade(vertx: Vertx, testContext: VertxTestContext) {
    val mainVerticle = OrderBookVerticle();
    // add two sell orders of 100 and 200
    mainVerticle.sellOrders[100.0] = mutableListOf(
      Order(
        id = "order-1",
        type = OrderType.SELL,
        price = 100.0,
        quantity = 5.0,
        currencyPair = "BTC-USD",
        customerOrderId = "test-buy-1",
        createdAt = Clock.System.now(),
      )
    )
    mainVerticle.sellOrders[200.0] = mutableListOf(
      Order(
        id = "order-2",
        type = OrderType.SELL,
        price = 200.0,
        quantity = 3.0,
        currencyPair = "BTC-USD",
        customerOrderId = "test-buy-2",
        createdAt = Clock.System.now(),
      )
    )
    // no buy orders yet, so no trades should be executed
    var trades = mainVerticle.executeTrade()
    testContext.verify {
      assert(trades.isEmpty())
    }

    // add a buy order of 99, should not match any sell orders
    val buyOrder = Order(
      id = "order-buy-1",
      type = OrderType.BUY,
      price = 99.0,
      quantity = 4.0,
      currencyPair = "BTC-USD",
      customerOrderId = "test-buy-1",
      createdAt = Clock.System.now(),
    )
    mainVerticle.buyOrders[buyOrder.price] = mutableListOf(buyOrder)
    trades = mainVerticle.executeTrade()
    testContext.verify {
      assert(trades.isEmpty())
    }

    // add a buy order of 150, should match with the sell order of 100
    val buyOrder2 = Order(
      id = "order-buy-2",
      type = OrderType.BUY,
      price = 150.0,
      quantity = 6.0,
      currencyPair = "BTC-USD",
      customerOrderId = "test-buy-2",
      createdAt = Clock.System.now(),
    )
    mainVerticle.buyOrders[buyOrder2.price] = mutableListOf(buyOrder2)
    trades = mainVerticle.executeTrade()
    testContext.verify {
      assert(trades.size == 1)
      assert(trades[0].price == 100.0)
      assert(trades[0].quantity == 5.0)
      assert(trades[0].buyOrderId == buyOrder2.id)
      assert(trades[0].sellOrderId == "order-1")
    }

    // check remaining orders in the order book
    testContext.verify {
      // sell order of 100 should be removed
      assert(!mainVerticle.sellOrders.containsKey(100.0))
      // sell order of 200 should still be there
      assert(mainVerticle.sellOrders.containsKey(200.0))
      // buy order of 150 should have 1.0 quantity left
      val remainingBuyOrders = mainVerticle.buyOrders[150.0]
      assert(remainingBuyOrders != null)
      assert(remainingBuyOrders!!.size == 1)
      assert(remainingBuyOrders[0].quantity == 1.0)

      // now check the buy order of 99 is still there
      val remainingLowBuyOrders = mainVerticle.buyOrders[99.0]
      assert(remainingLowBuyOrders != null)
      assert(remainingLowBuyOrders!!.size == 1)
      assert(remainingLowBuyOrders[0].quantity == 4.0)
    }

    // now suppose we add another buy order of 80,
    val buyOrder3 = Order(
      id = "order-buy-3",
      type = OrderType.BUY,
      price = 80.0,
      quantity = 10.0,
      currencyPair = "BTC-USD",
      customerOrderId = "test-buy-3",
      createdAt = Clock.System.now(),
    )
    mainVerticle.buyOrders[buyOrder3.price] = mutableListOf(
      Order(
        id = "order-buy-3",
        type = OrderType.BUY,
        price = 80.0,
        quantity = 10.0,
        currencyPair = "BTC-USD",
        customerOrderId = "test-buy-3",
        createdAt = Clock.System.now(),
      ),
      Order(
        id = "order-buy-4",
        type = OrderType.BUY,
        price = 80.0,
        quantity = 10.0,
        currencyPair = "BTC-USD",
        customerOrderId = "test-buy-4",
        createdAt = Clock.System.now(),
      )
    )
    trades = mainVerticle.executeTrade()
    testContext.verify {
      // no new trades should be executed
      assert(trades.isEmpty())
    }

    // now we add a sell order of 100,
    val sellOrder3 = Order(
      id = "order-sell-3",
      type = OrderType.SELL,
      price = 80.0,
      quantity = 6.0,
      currencyPair = "BTC-USD",
      customerOrderId = "test-sell-3",
      createdAt = Clock.System.now(),
    )
    mainVerticle.sellOrders[sellOrder3.price] = mutableListOf(sellOrder3)
    trades = mainVerticle.executeTrade()
    testContext.verify {
      // should match with the buy order of 150 first (1.0 quantity),
      // then with buy order of 99 (4.0 quantity)
      // then with buy order of 80 (1.0 quantity)
      // total 6.0 quantity traded at price 80
      // and the buy order of 80 should remain
      // buy order of 150 is fully filled and removed
      // buy order of 99 has 1.0 quantity left
      // buy order of 80 second order is not touched
      // sell order of 80 is fully filled and removed
      assert(trades.size == 3)
      assert(trades[0].price == 80.0)
      assert(trades[0].quantity == 1.0)
      assert(trades[0].buyOrderId == "order-buy-2")
      assert(trades[0].sellOrderId == "order-sell-3")
      assert(trades[1].price == 80.0)
      assert(trades[1].quantity == 4.0)
      assert(trades[1].buyOrderId == "order-buy-1")
      assert(trades[1].sellOrderId == "order-sell-3")
      assert(trades[2].price == 80.0)
      assert(trades[2].buyOrderId == "order-buy-3")
      assert(trades[2].sellOrderId == "order-sell-3")
      assert(trades[2].quantity == 1.0)


      // check remaining orders in the order book
      assert(!mainVerticle.buyOrders.containsKey(150.0))
      assert(!mainVerticle.buyOrders.containsKey(99.0))
      assert(!mainVerticle.sellOrders.containsKey(100.0))
      assert(mainVerticle.sellOrders.containsKey(200.0))
      val remainingBuyOrders80Price = mainVerticle.buyOrders[80.0]
      assert(remainingBuyOrders80Price != null)
      assert(remainingBuyOrders80Price!!.size == 2)
      assert(remainingBuyOrders80Price[0].price == 80.0)
      // quantity should be 9.0 now
      assert(remainingBuyOrders80Price[0].quantity == 9.0)
      // second buy order of 80 should remain with 10.0 quantity
      assert(remainingBuyOrders80Price[1].price == 80.0)
      assert(remainingBuyOrders80Price[1].quantity == 10.0)
      testContext.completeNow()
    }
  }

  @Test
  fun test_order_api(vertx: Vertx, testContext: VertxTestContext) {
    var client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 7005, "localhost", "/orderbook")
      .compose { req -> req.send() }
      .compose { resp ->
        testContext.verify {
          assert(resp.statusCode() == 200)
        }
        resp.body()
      }
      .compose { bodyBuffer ->
        testContext.verify {
          val bodyString = bodyBuffer.toString()
          val result: Map<String, Map<Double, List<Order>>> = Json.decodeFromString(bodyString)
          // should contain only Asks and Bids keys
          assert(result.contains("Asks"))
          assert(result.contains("Bids"))

          assert(result.getValue("Asks").isEmpty())
          assert(result.getValue("Bids").isEmpty())
        }
        val orderLimitRequest1 = OrderLimitRequest(
          side = OrderType.SELL,
          price = 100.0,
          quantity = 2.0,
          customerOrderId = "test-1"
        )

        val orderLimitRequest2 = OrderLimitRequest(
          side = OrderType.SELL,
          price = 200.0,
          quantity = 1.0,
          customerOrderId = "test-2"
        )
        val orderJson = Json.encodeToString(OrderLimitRequest.serializer(), orderLimitRequest1)
        client.request(HttpMethod.POST, 7005, "localhost", "/order/limit")
          .compose { req -> req.putHeader("content-type", "application/json").send(orderJson) }
          .compose { resp1 ->
            testContext.verify {
              assert(resp1.statusCode() == 201)
            }
            resp1.body()
          }
          .compose { resp1Body ->
            testContext.verify {
              val respBodyString = resp1Body.toString()
              val respBodyJson = Json.parseToJsonElement(respBodyString).jsonObject
              assert(respBodyJson.containsKey("id"))
            }
            val orderJson2 = Json.encodeToString(OrderLimitRequest.serializer(), orderLimitRequest2)
            client.request(HttpMethod.POST, 7005, "localhost", "/order/limit")
              .compose { req2 -> req2.putHeader("content-type", "application/json").send(orderJson2) }
              .compose { resp1 ->
                testContext.verify {
                  assert(resp1.statusCode() == 201)
                }
                client.request(HttpMethod.GET, 7005, "localhost", "/orderbook")
                  .compose { req3 -> req3.send() }
              }
          }
      }
      .compose { req2 ->
        testContext.verify {
          assert(req2.statusCode() == 200)
        }
        req2.body()
      }
      .compose { bodyBuffer ->
        testContext.verify {
          val bodyString = bodyBuffer.toString()
          val result: Map<String, Map<Double, List<Order>>> = Json.decodeFromString(bodyString)
          // should contain only Asks and Bids keys
          assert(result.contains("Asks"))
          assert(result.contains("Bids"))
          val asks = result.getValue("Asks")
          val bids = result.getValue("Bids")
          assert(asks.size == 2)
          assert(bids.size == 0)
        }
        // add a buy order that does not match any sell orders
        val orderLimitRequest3 = OrderLimitRequest(
          side = OrderType.BUY,
          price = 80.0,
          quantity = 1.0,
          customerOrderId = "test-3"
        )
        val orderJson3 = Json.encodeToString(OrderLimitRequest.serializer(), orderLimitRequest3)
        client.request(HttpMethod.POST, 7005, "localhost", "/order/limit")
          .compose { req -> req.putHeader("content-type", "application/json").send(orderJson3) }
          .compose { resp1 ->
            testContext.verify {
              assert(resp1.statusCode() == 201)
            }
            client.request(HttpMethod.GET, 7005, "localhost", "/orderbook")
              .compose { req3 -> req3.send() }
              .compose { resp3 ->
                testContext.verify {
                  assert(resp3.statusCode() == 200)
                }
                resp3.body()
              }
          }
      }
      .compose { resp3BodyBuffer ->
        testContext.verify {
          val bodyString = resp3BodyBuffer.toString()
          val result: Map<String, Map<Double, List<Order>>> = Json.decodeFromString(bodyString)
          // should contain only Asks and Bids keys
          assert(result.contains("Asks"))
          assert(result.contains("Bids"))
          val asks = result.getValue("Asks")
          val bids = result.getValue("Bids")
          assert(asks.size == 2)
          assert(bids.size == 1)
        }
        // now add a sell order that matched the buy order price 80.0
        val orderLimitRequest4 = OrderLimitRequest(
          side = OrderType.SELL,
          price = 80.0,
          quantity = 1.0,
          customerOrderId = "test-4"
        )
        val orderJson4 = Json.encodeToString(OrderLimitRequest.serializer(), orderLimitRequest4)
        client.request(HttpMethod.POST, 7005, "localhost", "/order/limit")
          .compose { req -> req.putHeader("content-type", "application/json").send(orderJson4) }
          .compose { resp1 ->
            testContext.verify {
              assert(resp1.statusCode() == 201)
            }
            client.request(HttpMethod.GET, 7005, "localhost", "/orderbook")
              .compose { req3 -> req3.send() }
              .compose { resp3 ->
                testContext.verify {
                  assert(resp3.statusCode() == 200)
                }
                resp3.body()
              }
          }
      }
      .compose { bodyBuffer ->
        testContext.verify {
          val bodyString = bodyBuffer.toString()
          val result: Map<String, Map<Double, List<Order>>> = Json.decodeFromString(bodyString)
          // should contain only Asks and Bids keys
          assert(result.contains("Asks"))
          assert(result.contains("Bids"))
          val asks = result.getValue("Asks")
          val bids = result.getValue("Bids")
          // both should be empty now as the orders matched
          assert(asks.size == 2)
          assert(bids.size == 0)
        }
        // finally, check the trade history
        client.request(HttpMethod.GET, 7005, "localhost", "/tradehistory")
          .compose { req3 -> req3.send() }
          .compose { resp3 ->
            testContext.verify {
              assert(resp3.statusCode() == 200)
            }
            resp3.body()
          }
      }
      .onComplete(
        testContext.succeeding { bodyBuffer ->
          testContext.verify {
            val bodyString = bodyBuffer.toString()
            val result: List<Trade> = Json.decodeFromString(bodyString)
            assert(result.size == 1)
            val trade = result[0]
            assert(trade.price == 80.0)
            assert(trade.quantity == 1.0)
            assert(trade.buyOrderId.isNotEmpty())
            // should contain only Asks and Bids keys
          }
          testContext.completeNow()
        }
      )
  }

}
