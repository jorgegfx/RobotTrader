package com.jworkdev.trading.robot.data.strategy

import com.jworkdev.trading.robot.data.signals.SignalFinderRequest
import com.jworkdev.trading.robot.data.strategy.macd.MACDMarketDataStrategyRequest
import com.jworkdev.trading.robot.data.strategy.macd.MACDMarketDataStrategyProvider
import com.jworkdev.trading.robot.data.strategy.opengap.{OpenGapMarketDataStrategyProvider, OpenGapMarketDataStrategyRequest}

import scala.util.Try

trait MarketDataStrategyRequest

trait MarketDataStrategyResponse:
  def buildSignalFinderRequest(): SignalFinderRequest

trait MarketDataStrategyProvider[
    Request <: MarketDataStrategyRequest,
    Response <: MarketDataStrategyResponse
]:
  def provide(request: Request): Try[Response]


object MarketDataStrategyProvider{
   def provide(request: MarketDataStrategyRequest): Try[MarketDataStrategyResponse] =
     request match
       case marketDataStrategyRequest: MACDMarketDataStrategyRequest => 
         MACDMarketDataStrategyProvider().provide(request = marketDataStrategyRequest)
       case openGapMarketDataStrategyRequest: OpenGapMarketDataStrategyRequest =>
         OpenGapMarketDataStrategyProvider().provide(request = openGapMarketDataStrategyRequest)
}