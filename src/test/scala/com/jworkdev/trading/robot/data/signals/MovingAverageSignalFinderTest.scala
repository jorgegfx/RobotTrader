package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.market
import com.jworkdev.trading.robot.market.data.StockPrice
import org.scalatest.flatspec.AnyFlatSpec

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.util.Random

class MovingAverageSignalFinderTest extends AnyFlatSpec:
  it should "Not have signals when is empty" in {
    val movingAverageSignalFinder = MovingAverageSignalFinder()
    val stockQuotes: List[StockPrice] = List()
    val signals = movingAverageSignalFinder.find(request =
      MovingAverageRequest(stockPrices = stockQuotes)
    )
    assert(signals.isEmpty === true)
  }
  it should "Not have signals when has only one entry" in {
    val movingAverageSignalFinder = MovingAverageSignalFinder()
    val now = Instant.now
    val stockQuotes: List[StockPrice] = List(
      StockPrice(
        symbol = "NDVA",
        open = 1,
        close = 2,
        high = 3,
        low = 1,
        volume = 4,
        snapshotTime = now.minus(1, ChronoUnit.DAYS)
      )
    )
    val signals = movingAverageSignalFinder.find(request =
      MovingAverageRequest(stockPrices = stockQuotes)
    )
    assert(signals.isEmpty === true)
  }

  def generateSinusoidalSeries(
      numberOfDays: Int,
      amplitude: Double,
      frequency: Double,
      phase: Double
  ): List[StockPrice] =
    val start = Instant.now().minus(numberOfDays, ChronoUnit.DAYS)
    val random = new Random()
    (0 until numberOfDays).toList.map { day =>
      val date = start.plus(day, ChronoUnit.DAYS)
      val angle = 2 * Math.PI * frequency * day + phase
      val price =
        amplitude * Math.sin(angle) + amplitude // To make all values positive
      StockPrice(
        symbol = "AAPL",
        open = price,
        close = price,
        high = price + random.nextDouble() * 5,
        low = price - random.nextDouble() * 5,
        volume = 1000,
        snapshotTime = date
      )
    }

  it should "Have signals" in {
    val movingAverageSignalFinder = MovingAverageSignalFinder()
    val start = Instant.now.minus(14, ChronoUnit.DAYS)
    val prices = List(100, 152, 115, 151, 170, 100, 120, 180, 160, 162)
    val stockPrices: List[StockPrice] = generateSinusoidalSeries(
      numberOfDays = 100,
      amplitude = 100,
      frequency = 10,
      phase = 10
    )
    val signals = movingAverageSignalFinder.find(request =
      MovingAverageRequest(stockPrices = stockPrices)
    )
    assert(signals.isEmpty === false)
    assert(signals.size === 2)
  }
