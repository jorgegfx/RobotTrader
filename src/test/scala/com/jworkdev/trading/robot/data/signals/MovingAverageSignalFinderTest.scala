package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.StockQuote
import org.scalatest.flatspec.AnyFlatSpec

import java.time.Instant
import java.time.temporal.ChronoUnit

class MovingAverageSignalFinderTest extends AnyFlatSpec:
  it should "Not have signals when is empty" in {
    val movingAverageSignalFinder = MovingAverageSignalFinder()
    val stockQuotes: List[StockQuote] = List()
    val signals = movingAverageSignalFinder.find(request =
      MovingAverageRequest(stockQuotes = stockQuotes)
    )
    assert(signals.isEmpty === true)
  }
  it should "Not have signals when has only one entry" in {
    val movingAverageSignalFinder = MovingAverageSignalFinder()
    val now = Instant.now
    val stockQuotes: List[StockQuote] = List(
      StockQuote(
        symbol = "NDVA",
        company = "Nvidia",
        open = 1,
        close = 2,
        high = 3,
        low = 1,
        snapshotTime = now.minus(1, ChronoUnit.DAYS)
      )
    )
    val signals = movingAverageSignalFinder.find(request =
      MovingAverageRequest(stockQuotes = stockQuotes)
    )
    assert(signals.isEmpty === true)
  }

  it should "Have signals" in {
    val movingAverageSignalFinder = MovingAverageSignalFinder()
    val start = Instant.now.minus(14, ChronoUnit.DAYS)
    val prices = List(100, 152, 115, 151, 170, 100, 120, 180, 160, 162)
    val stockQuotes: List[StockQuote] =
      prices.zipWithIndex.map {
        case (price: Int, index: Int) =>
          StockQuote(
            symbol = "NDVA",
            company = "Nvidia",
            open = 1,
            close = price.toDouble,
            high = 3,
            low = 1,
            snapshotTime = start.plus(index, ChronoUnit.DAYS)
          )
      }
    val signals = movingAverageSignalFinder.find(request =
      MovingAverageRequest(stockQuotes = stockQuotes)
    )
    assert(signals.isEmpty === false)
    assert(signals.size === 2)
  }
