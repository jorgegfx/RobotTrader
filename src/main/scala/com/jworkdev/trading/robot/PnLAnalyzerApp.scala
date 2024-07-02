package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.market.data.SnapshotInterval.{FiveMinutes, OneMinute}
import com.jworkdev.trading.robot.data.signals.{MACDRequest, MovingAverageRequest, RelativeStrengthIndexRequest, SignalFinderStrategy}
import com.jworkdev.trading.robot.market.data.yahoo.YahooFinanceMarketDataProvider
import com.jworkdev.trading.robot.pnl.{PnLAnalysis, PnLAnalyzer}

import scala.util.{Failure, Success}
object PnLAnalyzerApp extends App:
  val provider = YahooFinanceMarketDataProvider()
  private val pnLAnalyzer = PnLAnalyzer()
  val initialCash = 100000.0
  provider.getIntradayQuotes("NVDA", OneMinute) match
    case Failure(exception) => exception.printStackTrace()
    case Success(stockPrices) =>
      println("stockQuotes")
      stockPrices.foreach(println)

      println("MovingAverage: ")
      val movingAvgSignals =
        SignalFinderStrategy.findSignals(signalFinderRequest =
          MovingAverageRequest(stockPrices = stockPrices)
        )
      val pnlMovingAvg =
        pnLAnalyzer.execute(
          initialCash = initialCash,
          prices = stockPrices,
          signals = movingAvgSignals
        )
      printPnlAnalysis(pnLAnalysis = pnlMovingAvg)
      val rsiSignals = SignalFinderStrategy.findSignals(signalFinderRequest =
        RelativeStrengthIndexRequest(stockPrices = stockPrices)
      )
      println("RSI: ")
      val pnlRsi =
        pnLAnalyzer.execute(
          initialCash = initialCash,
          prices = stockPrices,
          signals = rsiSignals
        )
      printPnlAnalysis(pnLAnalysis = pnlRsi)
      println("MACD: ")
      val macdSignals =
        SignalFinderStrategy.findSignals(signalFinderRequest =
          MACDRequest(stockPrices = stockPrices, validate = true)
        )
      val pnlMacd =
        pnLAnalyzer.execute(
          initialCash = initialCash,
          prices = stockPrices,
          signals = macdSignals
        )
      printPnlAnalysis(pnLAnalysis = pnlMacd)

def printPnlAnalysis(pnLAnalysis: PnLAnalysis): Unit =
  println(s"PNL: ${pnLAnalysis.pnl}")
  pnLAnalysis.orders
    .map { order =>
      s"${order.`type`},${order.symbol},${order.dateTime},${order.shares},${order.price}"
    }
    .foreach(println)
