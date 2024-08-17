package com.jworkdev.trading.robot.data.signals

import com.jworkdev.trading.robot.data.strategy.opengap.OpenGapSignalInput
import com.jworkdev.trading.robot.market.data.StockPrice
import org.scalatest.funsuite.AnyFunSuiteLike

import java.time.LocalDateTime
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
class OpenGapSignalFinderTest extends AnyFunSuiteLike:

  test("testFindBuySell") {
    val symbol = "NVDA"
    val signalFinder = OpenGapSignalFinder()
    val currentDateTime = LocalDateTime.of(2024, 8, 7, 9, 0)
    val signals = signalFinder.find(request =
      OpenGapRequest(signalInputs =
        List(
          OpenGapSignalInput(
            tradingDateTime = currentDateTime,
            closingPrice = 100,
            openingPrice = 95,
            volumeAvg = 100,
            currentPrices = List(
              StockPrice(
                symbol = symbol,
                open = 95,
                close = 95,
                high = 150,
                low = 20,
                volume = 100,
                snapshotTime = LocalDateTime.of(2024, 8, 7, 9, 0).toZonedDateTime
              ),
              StockPrice(
                symbol = symbol,
                open = 95,
                close = 99,
                high = 150,
                low = 20,
                volume = 100,
                snapshotTime = LocalDateTime.of(2024, 8, 7, 9, 1).toZonedDateTime
              )
            )
          )
        )
      )
    )
    assert(signals.nonEmpty)
    assert(signals.size === 2)
    assert(signals.head.`type` === SignalType.Buy)
    assert(signals.last.`type` === SignalType.Sell)
  }

  test("testFindBuy") {
    val symbol = "NVDA"
    val signalFinder = OpenGapSignalFinder()
    val currentDateTime = LocalDateTime.of(2024, 8, 7, 9, 0)
    val signals = signalFinder.find(request =
      OpenGapRequest(signalInputs =
        List(
          OpenGapSignalInput(
            tradingDateTime = currentDateTime,
            closingPrice = 100,
            openingPrice = 95,
            volumeAvg = 100,
            currentPrices = List(
              StockPrice(
                symbol = symbol,
                open = 95,
                close = 95,
                high = 150,
                low = 20,
                volume = 100,
                snapshotTime = LocalDateTime.of(2024, 8, 7, 9, 0).toZonedDateTime
              ),
              StockPrice(
                symbol = symbol,
                open = 95,
                close = 98,
                high = 150,
                low = 20,
                volume = 100,
                snapshotTime = LocalDateTime.of(2024, 8, 7, 9, 1).toZonedDateTime
              )
            )
          )
        )
      )
    )
    assert(signals.nonEmpty)
    assert(signals.size === 1)
    assert(signals.head.`type` === SignalType.Buy)
  }

  test("testFind Already Filled") {
    val symbol = "NVDA"
    val signalFinder = OpenGapSignalFinder()
    val currentDateTime = LocalDateTime.of(2024, 8, 7, 9, 0)
    val signals = signalFinder.find(request =
      OpenGapRequest(signalInputs =
        List(
          OpenGapSignalInput(
            tradingDateTime = currentDateTime,
            closingPrice = 100,
            openingPrice = 95,
            volumeAvg = 100,
            currentPrices = List(
              StockPrice(
                symbol = symbol,
                open = 95,
                close = 20,
                high = 150,
                low = 20,
                volume = 100,
                snapshotTime = LocalDateTime.of(2024, 8, 7, 9, 0).toZonedDateTime
              ),
              StockPrice(
                symbol = symbol,
                open = 95,
                close = 100,
                high = 150,
                low = 20,
                volume = 100,
                snapshotTime = LocalDateTime.of(2024, 8, 7, 9, 1).toZonedDateTime
              )
            )
          )
        )
      )
    )
    assert(signals.isEmpty)
  }
