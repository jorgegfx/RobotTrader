package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.market.data

case class StockQuote(
    date: String,
    open: Double,
    close: Double,
    high: Double,
    low: Double
):
  def average: Double = (high + low) / 2

object MovingAverage:
  def calculateSMA(
                    prices: List[StockQuote],
                    period: Int,
                    priceType: String
  ): List[Option[Double]] =
    def getPriceByType(stockPrice: StockQuote, priceType: String): Double =
      priceType.toLowerCase match
        case "open"    => stockPrice.open
        case "close"   => stockPrice.close
        case "high"    => stockPrice.high
        case "low"     => stockPrice.low
        case "average" => stockPrice.average
        case _ =>
          throw IllegalArgumentException(s"Unknown price type: $priceType")

    prices.indices.toList.map { i =>
      if i >= period - 1 then
        val sum = (i - period + 1 to i)
          .map(j => getPriceByType(prices(j), priceType))
          .sum
        Some(sum / period)
      else None
    }

object MovingAverageCrossover extends App:
  val prices = List(
    StockQuote("2023-06-01", 148, 150, 151, 147),
    StockQuote("2023-06-02", 151, 152, 153, 150),
    StockQuote("2023-06-03", 152, 153, 154, 151),
    StockQuote("2023-06-04", 150, 151, 152, 149),
    StockQuote("2023-06-05", 154, 155, 156, 153),
    StockQuote("2023-06-06", 156, 157, 158, 155),
    StockQuote("2023-06-07", 158, 159, 160, 157),
    StockQuote("2023-06-08", 157, 158, 159, 156),
    StockQuote("2023-06-09", 159, 160, 161, 158),
    StockQuote("2023-06-10", 161, 162, 163, 160)
  )

  val shortTermPeriod = 3 // Short-term moving average period
  val longTermPeriod = 5 // Long-term moving average period
  val priceType = "close" // Can be "open", "close", "high", "low", "average"

  val shortTermMA =
    MovingAverage.calculateSMA(prices, shortTermPeriod, priceType)
  println("short")
  shortTermMA.map(_.getOrElse(0))foreach(println(_))
  val longTermMA = MovingAverage.calculateSMA(prices, longTermPeriod, priceType)
  println("long")
  longTermMA.map(_.getOrElse(0)).foreach(println(_))
  val signals = detectSignals(prices, shortTermMA, longTermMA)
  signals.foreach(println)

  def detectSignals(
                     prices: List[StockQuote],
                     shortTermMA: List[Option[Double]],
                     longTermMA: List[Option[Double]]
  ): List[(String, String)] =
    (for
      i <- shortTermMA.indices
      signal <- (
        shortTermMA.lift(i),
        longTermMA.lift(i),
        shortTermMA.lift(i - 1),
        longTermMA.lift(i - 1)
      ) match
        case (
              Some(Some(shortCurrent)),
              Some(Some(longCurrent)),
              Some(Some(shortPrevious)),
              Some(Some(longPrevious))
            ) =>
          if shortPrevious <= longPrevious && shortCurrent > longCurrent then
            Some(prices(i).date -> "Buy")
          else if shortPrevious >= longPrevious && shortCurrent < longCurrent
          then Some(prices(i).date -> "Sell")
          else None
        case _ => None
    yield signal).toList
