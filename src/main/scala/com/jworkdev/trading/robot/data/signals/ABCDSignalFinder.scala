package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}
import com.jworkdev.trading.robot.market.data.StockPrice

class ABCDSignalFinder(windowSize: Int = 10) extends SignalFinder[ABCDRequest]:
  private def findInSegment(priceSegment: List[StockPrice]): Option[Signal] =
    val A = priceSegment.head
    val B = priceSegment.tail.maxBy(_.high) // Find the highest point (B)
    val C = priceSegment.slice(priceSegment.indexOf(B), priceSegment.size).minBy(_.low) // Find the lowest point after B (C)
    val D = priceSegment.slice(priceSegment.indexOf(C), priceSegment.size).maxBy(_.high) // Find the highest point after C (D)

    // Calculate price changes between points
    val abMove = (B.high - A.low) / A.low
    val cdMove = (D.high - C.low) / C.low

    // Define thresholds for a valid ABCD pattern (customizable)
    val minMove = 0.05 // Minimum 5% move between points
    val maxPullback = 0.5 // Maximum 50% pullback for C

    // Determine if the price movement follows an ABCD pattern
    if abMove >= minMove && cdMove >= minMove && C.low >= (A.low + (B.high - A.low) * maxPullback) then
      Some(Signal(date = D.snapshotTime, `type` = Buy, stockPrice = D))
    else
      Some(Signal(date = D.snapshotTime, `type` = Sell, stockPrice = D))

  def find(request: ABCDRequest): List[Signal] =
    if(request.stockPrices.isEmpty || request.stockPrices.size < 4)
      return List.empty
    (0 until request.stockPrices.size - windowSize).flatMap { i =>
      val segment = request.stockPrices.slice(i, i + windowSize)
      findInSegment(segment)
    }.toList