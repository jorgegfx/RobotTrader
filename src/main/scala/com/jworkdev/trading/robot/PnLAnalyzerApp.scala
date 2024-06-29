package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.data.StockQuoteInterval.{FiveMinutes, OneMinute}
import com.jworkdev.trading.robot.data.signals.{MovingAverageRequest, RelativeStrengthIndexRequest, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.yahoo.YahooFinanceFinancialInstrumentDataProvider
import com.jworkdev.trading.robot.pnl.PnLAnalyzer

import scala.util.{Failure, Success}
object PnLAnalyzerApp extends App:

  val provider = YahooFinanceFinancialInstrumentDataProvider()
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
      println(s"PNL: ${pnlMovingAvg.pnl}")
      pnlMovingAvg.orders.map{order=>{
        s"${order.`type`},${order.symbol},${order.dateTime},${order.shares},${order.price}"
      }}.foreach(println)
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
      println(s"PNL: ${pnlRsi.pnl}")
      pnlRsi.orders.map{order=>{
        s"${order.`type`},${order.symbol},${order.dateTime},${order.shares},${order.price}"
      }}.foreach(println)
      println("MACD: ")
      val macdSignals =
        SignalFinderStrategy.findSignals(signalFinderRequest =
          MovingAverageRequest(stockPrices = stockPrices)
        )
      val pnlMacd =
        pnLAnalyzer.execute(
          initialCash = initialCash,
          prices = stockPrices,
          signals = macdSignals
        )
      println(s"PNL: ${pnlMacd.pnl}")
