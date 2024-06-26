package com.jworkdev.trading

import com.jworkdev.trading.robot.data.signals.SignalType

import java.time.Instant

package object robot:
  enum OrderType:
    case Buy, Sell
  def fromSignalType(signal: SignalType): OrderType = signal match
    case SignalType.Buy  => OrderType.Buy
    case SignalType.Sell => OrderType.Sell

  case class Order(
      `type`: OrderType,
      symbol: String,
      dateTime: Instant,
      shares: Long,
      price: Double,
      positionId: Option[Long] = None 
  ){
    def totalPrice: Double = shares * price 
  }
