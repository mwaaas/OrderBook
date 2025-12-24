package com.orderbook.orderbook

import kotlinx.serialization.Serializable

/**
 * Defines how long an order remains active.
 * - GTC: Good Till Cancelled
 * - FOK: Fill or Kill
 * - IOC: Immediate or Cancel
 */
@Serializable
enum class TimeInForce {
  GTC,
//  FOK,
//  IOC
}
