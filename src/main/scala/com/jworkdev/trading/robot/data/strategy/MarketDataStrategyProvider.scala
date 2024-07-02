package com.jworkdev.trading.robot.data.strategy

import scala.util.Try

trait MarketDataStrategyRequest

trait MarketDataStrategyResponse

trait MarketDataStrategyProvider[
    Request <: MarketDataStrategyRequest,
    Response <: MarketDataStrategyResponse
]:
  def provide(request: Request): Try[Response]
