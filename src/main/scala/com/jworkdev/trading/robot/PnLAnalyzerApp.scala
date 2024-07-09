package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, OpenGapStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequestFactory}
import com.jworkdev.trading.robot.domain.TradingStrategyType
import com.jworkdev.trading.robot.domain.TradingStrategyType.{MACD, OpenGap}
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.pnl.{PnLAnalysis, PnLAnalyzer}

import scala.util.{Failure, Success, Try}
object PnLAnalyzerApp extends App:
  private val pnLAnalyzer = PnLAnalyzer()
  private val marketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
  private val marketDataStrategyProvider = MarketDataStrategyProvider()
  private val signalFinderStrategy = SignalFinderStrategy()
  val initialCash = 100000.0
  val cfg = StrategyConfigurations(
    macd = Some(MACDStrategyConfiguration(snapshotInterval = OneMinute)),
    openGap = Some(OpenGapStrategyConfiguration(signalCount = 15))
  )
  val tests  = List(("NVDA",MACD),("NVDA",OpenGap))
  tests.foreach{ case (symbol: String, tradingStrategyType: TradingStrategyType) =>
    executeStrategy(symbol = symbol, tradingStrategyType = tradingStrategyType, strategyConfigurations = cfg) match
      case Failure(exception) => exception.printStackTrace()
      case Success(signals) =>
        val res = pnLAnalyzer.execute(
          initialCash = initialCash,
          signals = signals,
          tradingStrategyType = tradingStrategyType
        )
        printPnlAnalysis(pnLAnalysis = res)
  }


  private def executeStrategy(
      symbol: String,
      tradingStrategyType: TradingStrategyType,
      strategyConfigurations: StrategyConfigurations
  ): Try[List[Signal]] =
    println(s"Executing $symbol using $tradingStrategyType with cfg ${strategyConfigurations}")
    marketDataStrategyRequestFactory
      .createMarketDataStrategyRequest(
        symbol = symbol,
        tradingStrategyType = tradingStrategyType,
        strategyConfigurations = strategyConfigurations
      )
      .map(marketDataStrategyProvider.provide)
      .flatMap(_.map(_.buildSignalFinderRequest()))
      .map(signalFinderStrategy.findSignals)

  private def printPnlAnalysis(pnLAnalysis: PnLAnalysis): Unit =
    println(s"PNL: ${pnLAnalysis.pnl}")
    pnLAnalysis.orders
      .map { order =>
        s"${order.`type`},${order.symbol},${order.dateTime},${order.shares},${order.price}"
      }
      .foreach(println)
