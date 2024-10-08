package com.jworkdev.trading.robot.data.strategy

import com.jworkdev.trading.robot.config.StrategyConfigurations
import com.jworkdev.trading.robot.data.strategy.macd.MACDMarketDataStrategyRequest
import com.jworkdev.trading.robot.data.strategy.opengap.OpenGapMarketDataStrategyRequest
import com.jworkdev.trading.robot.domain.TradingStrategyType

import scala.util.{Failure, Success, Try}

trait MarketDataStrategyRequestFactory{
  def createMarketDataStrategyRequest(
                                       symbol: String,
                                       tradingStrategyType: TradingStrategyType,
                                       strategyConfigurations: StrategyConfigurations
                                     ): Try[MarketDataStrategyRequest]
}

class MarketDataStrategyRequestFactoryImpl extends MarketDataStrategyRequestFactory{
  override def createMarketDataStrategyRequest(
                                                symbol: String,
                                                tradingStrategyType: TradingStrategyType,
                                                strategyConfigurations: StrategyConfigurations
                                              ): Try[MarketDataStrategyRequest] =
    tradingStrategyType match
      case TradingStrategyType.OpenGap =>
        strategyConfigurations.openGap match
          case Some(openGapCfg) => Success(OpenGapMarketDataStrategyRequest(symbol=symbol,signalCount = openGapCfg.signalCount))
          case None => Failure(new IllegalStateException("No OpenGap configuration found!"))
      case TradingStrategyType.MACD =>
        strategyConfigurations.macd match
          case Some(macdCfg) =>
            Success(MACDMarketDataStrategyRequest(symbol = symbol, snapshotInterval = macdCfg.snapshotInterval))
          case None => Failure(new IllegalStateException("No MACD configuration found!"))
}

object MarketDataStrategyRequestFactory {
  def apply(): MarketDataStrategyRequestFactory = new MarketDataStrategyRequestFactoryImpl()
}
