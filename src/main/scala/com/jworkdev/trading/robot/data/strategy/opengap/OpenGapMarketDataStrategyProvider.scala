package com.jworkdev.trading.robot.data.strategy.opengap

import com.jworkdev.trading.robot.data.strategy.MarketDataStrategyProvider

import scala.util.Try

class OpenGapMarketDataStrategyProvider
    extends MarketDataStrategyProvider[
      OpenGapMarketDataStrategyRequest,
      OpenGapMarketDataStrategyResponse
    ]:
  override def provide(
      request: OpenGapMarketDataStrategyRequest
  ): Try[OpenGapMarketDataStrategyResponse] = ???


object OpenGapMarketDataStrategyProvider:
  def apply(): OpenGapMarketDataStrategyProvider = new OpenGapMarketDataStrategyProvider()