package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequestFactory}
import com.jworkdev.trading.robot.domain.TradingStrategyType
import com.jworkdev.trading.robot.domain.TradingStrategyType.MACD
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.pnl.{PnLAnalysis, PnLAnalyzer}

import scala.util.{Failure, Success, Try}
object PnLAnalyzerApp extends App:
  private val pnLAnalyzer = PnLAnalyzer()
  val initialCash = 100000.0
  val cfg = StrategyConfigurations(macd = Some(MACDStrategyConfiguration(snapshotInterval = OneMinute)))
  executeStrategy(symbol = "NVDA", tradingStrategyType = MACD, strategyConfigurations = cfg) match
    case Failure(exception) => exception.printStackTrace()
    case Success(signals) =>
      val res = pnLAnalyzer.execute(
        initialCash = initialCash,
        signals = signals
      )
      printPnlAnalysis(pnLAnalysis = res)

  private def executeStrategy(
      symbol: String,
      tradingStrategyType: TradingStrategyType,
      strategyConfigurations: StrategyConfigurations
  ): Try[List[Signal]] =
    MarketDataStrategyRequestFactory.createMarketDataStrategyRequest(
      symbol = symbol,
      tradingStrategyType = tradingStrategyType,
      strategyConfigurations = strategyConfigurations
    ).map(MarketDataStrategyProvider.provide).flatMap(_.map(_.buildSignalFinderRequest()))
      .map(SignalFinderStrategy.findSignals)

  private def printPnlAnalysis(pnLAnalysis: PnLAnalysis): Unit =
    println(s"PNL: ${pnLAnalysis.pnl}")
    pnLAnalysis.orders
      .map { order =>
        s"${order.`type`},${order.symbol},${order.dateTime},${order.shares},${order.price}"
      }
      .foreach(println)
