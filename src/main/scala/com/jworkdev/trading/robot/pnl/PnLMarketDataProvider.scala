package com.jworkdev.trading.robot.pnl

import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyResponse
import com.jworkdev.trading.robot.domain.TradingStrategyType

import java.time.LocalDateTime
import scala.util.Try

case class MarketDataEntry(
    tradingPrice: Double,
    tradingTime: LocalDateTime,
    marketDataStrategyResponse: Try[MarketDataStrategyResponse]
)
trait PnLMarketDataProvider:
  def provide(symbol: String, daysCount: Int, tradingStrategyType: TradingStrategyType): List[MarketDataEntry]

class PnLMarketDataProviderImpl extends PnLMarketDataProvider:
  private val pnLMarketDataStrategyProviderMap = Map[TradingStrategyType, PnLMarketDataStrategyProvider](
    TradingStrategyType.OpenGap -> OpenGapPnLMarketDataStrategyProvider()
  )
  def provide(symbol: String, daysCount: Int, tradingStrategyType: TradingStrategyType): List[MarketDataEntry] =
    pnLMarketDataStrategyProviderMap
      .get(tradingStrategyType)
      .map(_.provide(symbol = symbol, daysCount = daysCount))
      .getOrElse(List.empty)

object PnLMarketDataProvider:
  def apply(): PnLMarketDataProvider = new PnLMarketDataProviderImpl()
