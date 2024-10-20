package com.jworkdev.trading.robot.data

import com.jworkdev.trading.robot.market.data.StockPrice

import scala.util.Try

object ATRCalculator {
  private def trueRange(current: StockPrice, previousClose: Double): Double = {
    val rangeHighLow = current.high - current.low
    val rangeHighPrevClose = math.abs(current.high - previousClose)
    val rangeLowPrevClose = math.abs(current.low - previousClose)

    // Return the maximum of the three ranges
    List(rangeHighLow, rangeHighPrevClose, rangeLowPrevClose).max
  }

  def calculate(prices: List[StockPrice], period: Int): Try[Double] = {
    Try{
      // Ensure we have enough data points
      if (prices.size < period)
        throw new IllegalArgumentException("Not enough data points for the given period")

      // Calculate True Range (TR) for each day
      val trueRanges = prices.zipWithIndex.tail.map { case (current, index) =>
        val previousClose = prices(index - 1).close
        trueRange(current, previousClose)
      }

      // Calculate the average of the true ranges over the specified period
      trueRanges.take(period).sum / period
    }
  }
}
