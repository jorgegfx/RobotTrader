package com.jworkdev.trading.robot.data.signals
import java.time.LocalDate

object MACD {

  // Case class to hold the signal information
  case class Signal(date: LocalDate, signalType: String)

  // Helper function to calculate EMA
  def calculateEMA(period: Int, prices: Seq[Double]): Seq[Double] = {
    val alpha = 2.0 / (period + 1)
    prices.scanLeft(0.0)((prevEMA, price) => (price * alpha) + (prevEMA * (1 - alpha))).drop(1)
  }

  def calculateMACD(prices: Seq[Double]): (Seq[Double], Seq[Double], Seq[Double]) = {
    val ema12 = calculateEMA(12, prices)
    val ema26 = calculateEMA(26, prices)

    // Ensure both EMAs have the same length
    val minLength = math.min(ema12.length, ema26.length)
    val macdLine = ema12.take(minLength).zip(ema26.take(minLength)).map { case (ema12Val, ema26Val) => ema12Val - ema26Val }

    val signalLine = calculateEMA(9, macdLine)

    val histogram = macdLine.take(signalLine.length).zip(signalLine).map { case (macdVal, signalVal) => macdVal - signalVal }

    (macdLine, signalLine, histogram)
  }

  def generateSignals(dates: Seq[LocalDate], macdLine: Seq[Double], signalLine: Seq[Double]): List[Signal] = {
    val signals = macdLine.zip(signalLine).sliding(2).zip(dates.drop(1)).collect {
      case (Seq((prevMacd, prevSignal), (currMacd, currSignal)), date) =>
        if prevMacd <= prevSignal && currMacd > currSignal then Signal(date, "Buy")
        else if prevMacd >= prevSignal && currMacd < currSignal then Signal(date, "Sell")
        else Signal(date, "Hold")
    }.toList

    // Align signals with the original data length
    List.fill(dates.length - signals.length)(Signal(dates.head, "Hold")) ++ signals
  }

  def main(args: Array[String]): Unit = {
    // Example usage with some dummy data
    val data = Seq(
      (LocalDate.of(2023, 1, 1), 1.0),
      (LocalDate.of(2023, 1, 2), 1.1),
      (LocalDate.of(2023, 1, 3), 1.2),
      (LocalDate.of(2023, 1, 4), 1.3),
      (LocalDate.of(2023, 1, 5), 1.4),
      (LocalDate.of(2023, 1, 6), 1.5),
      (LocalDate.of(2023, 1, 7), 1.6),
      (LocalDate.of(2023, 1, 8), 1.7),
      (LocalDate.of(2023, 1, 9), 1.8),
      (LocalDate.of(2023, 1, 10), 1.9),
      (LocalDate.of(2023, 1, 11), 2.0),
      (LocalDate.of(2023, 1, 12), 2.1),
      (LocalDate.of(2023, 1, 13), 2.2),
      (LocalDate.of(2023, 1, 14), 2.3),
      (LocalDate.of(2023, 1, 15), 2.4),
      (LocalDate.of(2023, 1, 16), 2.5),
      (LocalDate.of(2023, 1, 17), 2.6),
      (LocalDate.of(2023, 1, 18), 2.7),
      (LocalDate.of(2023, 1, 19), 2.8),
      (LocalDate.of(2023, 1, 20), 2.9),
      (LocalDate.of(2023, 1, 21), 3.0)
    )

    val dates = data.map(_._1)
    val prices = data.map(_._2)

    val (macdLine, signalLine, histogram) = calculateMACD(prices)
    val signals = generateSignals(dates, macdLine, signalLine)

    println(s"MACD Line: $macdLine")
    println(s"Signal Line: $signalLine")
    println(s"Histogram: $histogram")
    println(s"Signals: $signals")
  }
}
