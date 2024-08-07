package com.jworkdev.trading.robot.pnl

import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyResponse
import com.jworkdev.trading.robot.domain.TradingStrategyType

import java.time.LocalDateTime
import scala.util.Try

case class MarketDataEntry(tradingPrice: Double,
                           tradingTime: LocalDateTime,
                           marketDataStrategyResponse: Try[MarketDataStrategyResponse])
trait PnLMarketDataProvider {
  def provide(symbol:String, daysCount: Int, tradingStrategyType: TradingStrategyType): List[MarketDataEntry]
}
