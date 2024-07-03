package com.jworkdev.trading.robot.data.strategy

import com.jworkdev.trading.robot.config.StrategyConfigurations
import com.jworkdev.trading.robot.data.strategy.macd.MACDMarketDataStrategyRequest
import com.jworkdev.trading.robot.domain.TradingStrategyType

import scala.util.{Failure, Success, Try}

object MarketDataStrategyRequestFactory {
  def createMarketDataStrategyRequest(
                                       symbol: String,
                                       tradingStrategyType: TradingStrategyType,
                                       strategyConfigurations: StrategyConfigurations
                                     ): Try[MarketDataStrategyRequest] =
    tradingStrategyType match
      case TradingStrategyType.OpenGap =>
        Failure(new IllegalStateException("No OpenGap configuration found!"))
      case TradingStrategyType.MACD =>
        strategyConfigurations.macd match
          case Some(macdCfg) =>
            Success(MACDMarketDataStrategyRequest(symbol = symbol, snapshotInterval = macdCfg.snapshotInterval))
          case None => Failure(new IllegalStateException("No MACD configuration found!"))
}
