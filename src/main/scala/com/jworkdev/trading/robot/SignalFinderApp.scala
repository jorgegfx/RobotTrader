package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.data.alphavantage.AlphaVantageQuotesDataProvider
import com.jworkdev.trading.robot.data.StockQuoteInterval.ThirtyMinutes
import com.jworkdev.trading.robot.data.signals.{MovingAverageRequest, MovingAverageSignalFinder}

import scala.util.{Failure, Success}
object SignalFinderApp extends App{
  val provider = AlphaVantageQuotesDataProvider()
  provider.getIntradayQuotes("AAPL", ThirtyMinutes) match
    case Failure(exception) => exception.printStackTrace()
    case Success(stockQuotes) =>
      val movingAverageSignalFinder = MovingAverageSignalFinder()
      val signals = movingAverageSignalFinder.find(request = MovingAverageRequest(stockQuotes = stockQuotes))
      signals.foreach(println)
}
