package com.jworkdev.trading.robot.data.strategy.macd

import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyProvider
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotInterval}

import scala.util.Try

class MACDMarketDataStrategyProvider(private val marketDataProvider: MarketDataProvider)
    extends MarketDataStrategyProvider[MACDMarketDataStrategyRequest, MACDMarketDataStrategyResponse]:
  override def provide(request: MACDMarketDataStrategyRequest): Try[MACDMarketDataStrategyResponse] =
    val prices = marketDataProvider.getIntradayQuotes(symbol = request.symbol, interval = request.snapshotInterval)
    prices.map(MACDMarketDataStrategyResponse.apply)
    

object MACDMarketDataStrategyProvider:
  def apply(marketDataProvider: MarketDataProvider): MACDMarketDataStrategyProvider =
    new MACDMarketDataStrategyProvider(marketDataProvider)