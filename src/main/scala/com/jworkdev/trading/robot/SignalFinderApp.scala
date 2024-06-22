package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.data.StockQuoteInterval.FiveMinutes
import com.jworkdev.trading.robot.data.alphavantage.AlphaVantageQuotesDataProvider
import com.jworkdev.trading.robot.data.signals.{MovingAverageRequest, RelativeStrengthIndexRequest, SignalFinderStrategy}

import scala.util.{Failure, Success}
object SignalFinderApp extends App:
  val provider = AlphaVantageQuotesDataProvider()
  provider.getIntradayQuotes("NVDA", FiveMinutes) match
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

