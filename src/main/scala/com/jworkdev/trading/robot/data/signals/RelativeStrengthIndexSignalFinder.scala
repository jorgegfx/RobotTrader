package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.signals.{RelativeStrengthIndexRequest, Signal, SignalFinder}
import com.jworkdev.trading.robot.market
import com.jworkdev.trading.robot.market.data.StockPrice
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}

class RelativeStrengthIndexSignalFinder(period: Int) extends SignalFinder[RelativeStrengthIndexRequest]{
  private def calculateRSI(prices: List[StockPrice]): List[Option[Double]] = {
    val priceChanges = prices.sliding(2).collect { case List(prev, curr) => curr.close - prev.close }.toList

    def avgGainLoss(values: List[Double]): (Double, Double) = {
      val gains = values.filter(_ > 0).sum / period
      val losses = -values.filter(_ < 0).sum / period
      (gains, losses)
    }

    val initial = priceChanges.take(period)
    val (initialGain, initialLoss) = avgGainLoss(initial)
    var avgGain = initialGain
    var avgLoss = initialLoss

    val rsiList = priceChanges.drop(period).scanLeft(Option.empty[Double]) { (acc, change) =>
      avgGain = (avgGain * (period - 1) + (if (change > 0) change else 0)) / period
      avgLoss = (avgLoss * (period - 1) + (if (change < 0) -change else 0)) / period
      val rs = if (avgLoss != 0) avgGain / avgLoss else avgGain
      Some(100 - (100 / (1 + rs)))
    }

    List.fill(period)(None) ++ rsiList.tail
  }

  private def detectRSISignals(prices: List[StockPrice], rsi: List[Option[Double]]): List[Signal] = {
    val priceMap = prices.groupBy(_.snapshotTime).view.mapValues(values => values.head).toMap
    (for {
      i <- rsi.indices
      signal <- rsi(i) match {
        case Some(rsiValue) if rsiValue < 30 =>
          val date = prices(i).snapshotTime
          Some(Signal(date = date,`type`=Buy,stockPrice = priceMap(date)))
        case Some(rsiValue) if rsiValue > 70 =>
          val date = prices(i).snapshotTime
          Some(Signal(date = date,`type`=Sell,stockPrice = priceMap(date)))
        case _ => None
      }
    } yield signal).toList
  }

  override def find(request: RelativeStrengthIndexRequest): List[Signal] = {
    val rsi = calculateRSI(prices = request.stockPrices)
    detectRSISignals(prices = request.stockPrices, rsi = rsi)
  }
}

object RelativeStrengthIndexSignalFinder{
  def apply(): RelativeStrengthIndexSignalFinder = new RelativeStrengthIndexSignalFinder(period = 14)
}