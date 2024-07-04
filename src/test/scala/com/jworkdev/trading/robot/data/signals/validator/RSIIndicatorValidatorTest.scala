package com.jworkdev.trading.robot.data.signals.validator

import com.jworkdev.trading.robot.market.data.StockPrice
import org.scalatest.flatspec.AnyFlatSpec

import java.time.Instant
import java.time.temporal.ChronoUnit

class RSIIndicatorValidatorTest extends AnyFlatSpec:
  private val PERIOD = 14
  private val rsiIndicatorValidator = new RSIIndicatorValidator(PERIOD)

  it should "Not have indicators when is empty" in:
    val res = rsiIndicatorValidator.validate(stockPrices = List.empty)
    assert(res.isEmpty)


  it should "Result empty with less than Period" in:
    val now = Instant.now()
    val stockPrices = List.fill(PERIOD-1)(now).zipWithIndex.map{ case (date: Instant, index: Int) =>
      StockPrice(symbol = "NVDA", open = 1, close = 2, high = 3, low = 1, volume = 100, snapshotTime = date.minus(index,ChronoUnit.DAYS))
    }
    val res = rsiIndicatorValidator.validate(stockPrices = stockPrices)
    assert(res.isEmpty)


  it should "Result empty with more than Period and no gain" in:
    val now = Instant.now()
    val stockPrices = List.fill(PERIOD + 1)(now).zipWithIndex.map { case (date: Instant, index: Int) =>
      StockPrice(symbol = "NVDA", open = 1, close = 2, high = 3, low = 1, volume = 100, snapshotTime = date.minus(index, ChronoUnit.DAYS))
    }
    val res = rsiIndicatorValidator.validate(stockPrices = stockPrices)
    assert(res.isEmpty)

  it should "Result non-empty with more than Period and gain" in :
    val now = Instant.now()
    val stockPrices = List.fill(PERIOD + 100)(now).zipWithIndex.map { case (date: Instant, index: Int) =>
      val closeValue = (Math.sin(index) * 100) + 100
      StockPrice(symbol = "NVDA", open = 1, close = closeValue, high = 3, low = 1, volume = 100, snapshotTime = date.minus(index, ChronoUnit.DAYS))
    }
    val res = rsiIndicatorValidator.validate(stockPrices = stockPrices)
    assert(res.nonEmpty)

