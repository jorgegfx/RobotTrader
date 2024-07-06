package com.jworkdev.trading.robot.data.strategy

import com.jworkdev.trading.robot.data.signals.SignalFinderRequest
import com.jworkdev.trading.robot.data.strategy.macd.MACDMarketDataStrategyRequest
import com.jworkdev.trading.robot.data.strategy.macd.MACDMarketDataStrategyProvider
import com.jworkdev.trading.robot.data.strategy.opengap.{
  OpenGapMarketDataStrategyProvider,
  OpenGapMarketDataStrategyRequest
}
import com.jworkdev.trading.robot.market.data.MarketDataProvider

import scala.util.Try

trait MarketDataStrategyRequest

trait MarketDataStrategyResponse:
  def buildSignalFinderRequest(): SignalFinderRequest

trait MarketDataStrategyProvider[
    Request <: MarketDataStrategyRequest,
    Response <: MarketDataStrategyResponse
]:
  def provide(request: Request): Try[Response]

class MarketDataStrategyProviderImpl(private val marketDataProvider: MarketDataProvider)
    extends MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]:
  override def provide(request: MarketDataStrategyRequest): Try[MarketDataStrategyResponse] = request match
    case marketDataStrategyRequest: MACDMarketDataStrategyRequest =>
      MACDMarketDataStrategyProvider(marketDataProvider).provide(request = marketDataStrategyRequest)
    case openGapMarketDataStrategyRequest: OpenGapMarketDataStrategyRequest =>
      OpenGapMarketDataStrategyProvider(marketDataProvider).provide(request = openGapMarketDataStrategyRequest)

object MarketDataStrategyProvider:
  def apply(): MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] = 
    new MarketDataStrategyProviderImpl(marketDataProvider = MarketDataProvider())