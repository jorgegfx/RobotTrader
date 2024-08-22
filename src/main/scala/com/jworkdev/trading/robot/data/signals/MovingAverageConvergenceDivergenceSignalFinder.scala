package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}
import com.jworkdev.trading.robot.market.data.StockPrice

import java.time.{Instant, ZonedDateTime}

class MovingAverageConvergenceDivergenceSignalFinder extends SignalFinder[MACDRequest]:
  def find(request: MACDRequest): List[Signal] =
    val dates = request.stockPrices.map(_.snapshotTime)
    val prices = request.stockPrices.map(_.close)
    val priceMap = request.stockPrices.groupBy(_.snapshotTime).view.mapValues(values => values.head).toMap
    val (macdLine, signalLine, histogram) = calculateMACD(prices)
    generateSignals(dates, macdLine, signalLine, priceMap)

  def calculateMACD(prices: Seq[Double]): (Seq[Double], Seq[Double], Seq[Double]) =
    val ema12 = calculateEMA(12, prices)
    val ema26 = calculateEMA(26, prices)

    // Ensure both EMAs have the same length
    val minLength = math.min(ema12.length, ema26.length)
    val macdLine =
      ema12.take(minLength).zip(ema26.take(minLength)).map { case (ema12Val, ema26Val) => ema12Val - ema26Val }

    val signalLine = calculateEMA(9, macdLine)

    val histogram =
      macdLine.take(signalLine.length).zip(signalLine).map { case (macdVal, signalVal) => macdVal - signalVal }

    (macdLine, signalLine, histogram)

  // Helper function to calculate EMA
  def calculateEMA(period: Int, prices: Seq[Double]): Seq[Double] =
    val alpha = 2.0 / (period + 1)
    prices.scanLeft(0.0)((prevEMA, price) => (price * alpha) + (prevEMA * (1 - alpha))).drop(1)

  def generateSignals(
      dates: Seq[ZonedDateTime],
      macdLine: Seq[Double],
      signalLine: Seq[Double],
      priceMap: Map[ZonedDateTime, StockPrice]
  ): List[Signal] =
    macdLine
      .zip(signalLine)
      .sliding(2)
      .zip(dates.drop(1))
      .collect { case (Seq((prevMacd, prevSignal), (currMacd, currSignal)), date) =>
        if prevMacd <= prevSignal && currMacd > currSignal then
          Some(Signal(date = date, `type` = Buy, stockPrice = priceMap(date)))
        else if prevMacd >= prevSignal && currMacd < currSignal then
          Some(Signal(date = date, `type` = Sell, stockPrice = priceMap(date)))
        else None
      }
      .toList
      .flatten
