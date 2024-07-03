package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.market
import com.jworkdev.trading.robot.market.data.StockPrice
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}

class MovingAverageSignalFinder(
    private val shortTermPeriod: Int,
    private val longTermPeriod: Int
) extends SignalFinder[MovingAverageRequest]:
  override def find(request: MovingAverageRequest): List[Signal] =
    if request.stockPrices.size > 1 then
      val prices = request.stockPrices.map(_.close)
      val shortTermMA = calculateSMA(prices = prices, period = shortTermPeriod)
      val longTermMA = calculateSMA(prices = prices, period = longTermPeriod)
      detectSignals(
        stockQuotes = request.stockPrices,
        shortTermMA = shortTermMA,
        longTermMA = longTermMA
      )
    else List.empty[Signal]

  private def calculateSMA(
      prices: List[Double],
      period: Int
  ): List[Option[Double]] =
    prices.indices.toList.map { i =>
      if i >= period - 1 then
        val sum = (i - period + 1 to i).map(j => prices(j)).sum
        Some(sum / period)
      else None
    }

  private def detectSignals(
                             stockQuotes: List[StockPrice],
                             shortTermMA: List[Option[Double]],
                             longTermMA: List[Option[Double]]
  ): List[Signal] =
    val priceMap = stockQuotes.groupBy(_.snapshotTime).flatMap {
      case (key,value) => value.headOption.map(value=>(key,value))
    }
    (for i <- 1 until stockQuotes.length
      yield (
        shortTermMA(i),
        longTermMA(i),
        shortTermMA(i - 1),
        longTermMA(i - 1)
      ) match
        case (
          Some(shortCurrent),
          Some(longCurrent),
          Some(shortPrevious),
          Some(longPrevious)
          ) =>
          val date = stockQuotes(i).snapshotTime
          if shortPrevious <= longPrevious && shortCurrent > longCurrent then
            Some(Signal(date = date,`type`=Buy,stockPrice = priceMap(date)))
          else if shortPrevious >= longPrevious && shortCurrent < longCurrent then
            Some(Signal(date = date,`type`=Sell,stockPrice = priceMap(date)))
          else None
        case _ => None
      ).flatten.toList

object MovingAverageSignalFinder:
  def apply(): MovingAverageSignalFinder = new MovingAverageSignalFinder(50, 200)
