package com.jworkdev.trading.robot.data.strategy.macd

import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyProvider
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotInterval}

import scala.util.Try

class MACDMarketDataStrategyProvider
    extends MarketDataStrategyProvider[MACDMarketDataStrategyRequest, MACDMarketDataStrategyResponse]:
  private val marketDataProvider = MarketDataProvider()
  override def provide(request: MACDMarketDataStrategyRequest): Try[MACDMarketDataStrategyResponse] =
    val prices = marketDataProvider.getIntradayQuotes(symbol = request.symbol, interval = request.snapshotInterval)
    prices.map(MACDMarketDataStrategyResponse.apply)
    

object MACDMarketDataStrategyProvider:
  def apply(): MACDMarketDataStrategyProvider = new MACDMarketDataStrategyProvider()