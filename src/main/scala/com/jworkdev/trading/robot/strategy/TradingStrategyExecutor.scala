package com.jworkdev.trading.robot.strategy

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.config.TradingMode
import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyResponse
import com.jworkdev.trading.robot.domain.{FinInstrument, Position, TradingExchange, TradingStrategy}

import java.time.ZonedDateTime
import scala.util.Try

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
