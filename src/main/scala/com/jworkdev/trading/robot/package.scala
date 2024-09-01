package com.jworkdev.trading

import com.jworkdev.trading.robot.data.signals.SignalType
import com.jworkdev.trading.robot.domain.TradingStrategyType

import java.time.{Instant, ZonedDateTime}

package object robot:
  enum OrderType:
    case Buy, Sell
  def fromSignalType(signal: SignalType): OrderType = signal match
    case SignalType.Buy  => OrderType.Buy
    case SignalType.Sell => OrderType.Sell

  case class Order(
      `type`: OrderType,
      symbol: String,
      dateTime: ZonedDateTime,
      shares: Long,
      price: Double,
      tradingStrategyType: TradingStrategyType,
      positionId: Option[Long] = None,
      trigger: OrderTrigger
  ){
    def totalPrice: Double = shares * price 
  }

  enum OrderTrigger:
    case Signal, MaxProfitExit, StopLoss, CloseDay
