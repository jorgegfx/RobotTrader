package com.jworkdev.trading.robot.data.signals.validator
import com.jworkdev.trading.robot.data

import java.time.Instant

class RSIIndicatorValidator(period: Int) extends IndicatorValidator{
  private def calculate(prices: Seq[Double], period: Int): Seq[Double] = {
    require(prices.length >= period, "Not enough data points to calculate RSI")

    val changes = prices.sliding(2).map { case Seq(prev, curr) => curr - prev }.toSeq
    val gains = changes.map(change => if (change > 0) change else 0.0)
    val losses = changes.map(change => if (change < 0) -change else 0.0)

    val avgGains = calculateSMA(gains, period)
    val avgLosses = calculateSMA(losses, period)

    val rs = avgGains.zip(avgLosses).map { case (avgGain, avgLoss) =>
      if (avgLoss == 0) Double.PositiveInfinity else avgGain / avgLoss
    }

    rs.map(r => 100 - (100 / (1 + r)))
  }

  private def calculateSMA(data: Seq[Double], period: Int): Seq[Double] = {
    val initialSMA = data.take(period).sum / period
    val sma = data.drop(period).scanLeft(initialSMA) { (prev, current) =>
      (prev * (period - 1) + current) / period
    }
    sma.drop(1)
  }

  override def validate(stockPrices: List[data.StockPrice]): Map[Instant, Boolean] = {
    val rsiValues = calculate(prices = stockPrices.map(_.close), period = period)
    stockPrices.map(_.snapshotTime).zipWithIndex.map{case (date: Instant, index: Int)=>
      (date,rsiValues(index) > 70)
    }.toMap
  }
}
