package com.jworkdev.trading.robot.pnl

import com.jworkdev.trading.robot.config.{OpenGapStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals
import com.jworkdev.trading.robot.data.strategy.opengap.{OpenGapMarketDataStrategyResponse, OpenGapSignalInput}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequestFactory}
import com.jworkdev.trading.robot.domain.TradingStrategyType.OpenGap
import com.jworkdev.trading.robot.market.data.StockPrice

import scala.util.Success

trait PnLMarketDataStrategyProvider:
  def provide(symbol: String, daysCount: Int): List[MarketDataEntry]

class OpenGapPnLMarketDataStrategyProvider extends PnLMarketDataStrategyProvider:
  private val marketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
  private val marketDataStrategyProvider = MarketDataStrategyProvider()

  def provide(symbol: String, daysCount: Int): List[MarketDataEntry] =
    marketDataStrategyRequestFactory
      .createMarketDataStrategyRequest(
        symbol = symbol,
        tradingStrategyType = OpenGap,
        strategyConfigurations =
          StrategyConfigurations(macd = None, openGap = Some(OpenGapStrategyConfiguration(signalCount = daysCount)))
      )
      .flatMap(marketDataStrategyProvider.provide)
      .map(_.buildSignalFinderRequest())
      .map {
        case signals.OpenGapRequest(signalInputs) => buildEntries(signalInputs = signalInputs)
        case _                                    => List.empty
      }
      .getOrElse(List.empty)

  private def buildEntries(signalInputs: List[OpenGapSignalInput]): List[MarketDataEntry] =
    signalInputs.flatMap { input =>
      input.currentPrices.zipWithIndex.map{ case (stockPrice: StockPrice, index: Int) =>
        val currentPrices = input.currentPrices.take(index+1)
        val signalInput = input.copy(currentPrices = currentPrices)
        MarketDataEntry(
          tradingPrice = stockPrice.close,
          tradingTime = input.tradingDateTime,
          marketDataStrategyResponse = Success(OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput)))
        )
      }

    }
