package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.data.StockQuoteInterval.OneMinute
import com.jworkdev.trading.robot.data.signals.{MovingAverageRequest, RelativeStrengthIndexRequest, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.yahoo.YahooFinanceFinancialInstrumentDataProvider

import scala.util.{Failure, Success}
object SignalFinderApp extends App:
  val provider = YahooFinanceFinancialInstrumentDataProvider()
  provider.getIntradayQuotes("NVDA", OneMinute) match
    case Failure(exception) => exception.printStackTrace()
    case Success(stockQuotes) =>
      println("stockQuotes")
      stockQuotes.foreach(println)

      println("MovingAverage: signals")
      val movingAvgSignals = SignalFinderStrategy.findSignals(signalFinderRequest =
        MovingAverageRequest(stockPrices = stockQuotes)
      )
      movingAvgSignals.foreach(println)
      val rsiSignals = SignalFinderStrategy.findSignals(signalFinderRequest =
        RelativeStrengthIndexRequest(stockPrices = stockQuotes)
      )
      println("RSI: signals")
      rsiSignals.foreach(println)

