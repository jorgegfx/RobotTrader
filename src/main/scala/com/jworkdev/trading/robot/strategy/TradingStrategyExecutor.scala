package com.jworkdev.trading.robot.strategy

import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy as SignalBuy, Sell as SignalSell}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderRequest, SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyResponse
import com.jworkdev.trading.robot.domain.{FinInstrument, Position, TradingExchange, TradingStrategy}
import com.jworkdev.trading.robot.service.{ForcePositionExitService, TradingWindowValidator}
import com.jworkdev.trading.robot.time.ZonedDateTimeExtensions.isSameDay
import com.jworkdev.trading.robot.{Order, OrderTrigger, OrderType}
import com.typesafe.scalalogging.Logger

import java.time.ZonedDateTime
import scala.util.{Failure, Success, Try}

case class TradingStrategyEntryRequest(
    balancePerFinInst: Double,
    finInstrument: FinInstrument,
    exchange: TradingExchange,
    tradingStrategy: TradingStrategy,
    tradingMode: TradingMode,
    tradingPrice: Double,
    tradeDateTime: ZonedDateTime,
    marketDataStrategyResponse: Try[MarketDataStrategyResponse]
)

case class TradingStrategyExitRequest(
    position: Position,
    finInstrument: FinInstrument,
    exchange: TradingExchange,
    tradingStrategy: TradingStrategy,
    tradingMode: TradingMode,
    stopLossPercentage: Int,
    tradingPrice: Double,
    tradeDateTime: ZonedDateTime,
    marketDataStrategyResponse: Try[MarketDataStrategyResponse]
)

trait TradingStrategyExecutor:
  
  def executeEntry(tradingStrategyEntryRequest: TradingStrategyEntryRequest): Option[Order]

  def executeExit(tradingStrategyExitRequest: TradingStrategyExitRequest): Option[Order]
