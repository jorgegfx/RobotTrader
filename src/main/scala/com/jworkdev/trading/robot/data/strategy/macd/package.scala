package com.jworkdev.trading.robot.data.strategy

import com.jworkdev.trading.robot.data.signals
import com.jworkdev.trading.robot.data.signals.MACDRequest
import com.jworkdev.trading.robot.market.data.{SnapshotInterval, StockPrice}

package object macd {
  case class MACDMarketDataStrategyRequest(symbol: String, snapshotInterval: SnapshotInterval) extends MarketDataStrategyRequest
  case class MACDMarketDataStrategyResponse(prices: List[StockPrice]) extends MarketDataStrategyResponse{
    override def buildSignalFinderRequest(): signals.SignalFinderRequest = MACDRequest(stockPrices = prices, validate = true)
  }

}
