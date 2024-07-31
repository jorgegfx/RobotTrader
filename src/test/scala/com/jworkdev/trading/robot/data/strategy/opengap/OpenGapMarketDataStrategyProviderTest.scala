package com.jworkdev.trading.robot.data.strategy.opengap

import com.jworkdev.trading.robot.market.data.{MarketDataProvider, SnapshotInterval, StockPrice}
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatestplus.mockito.MockitoSugar.mock

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import scala.util.{Failure, Success}
class OpenGapMarketDataStrategyProviderTest extends AnyFunSuiteLike:
  private val marketDataProvider: MarketDataProvider = mock[MarketDataProvider]
  val symbol = "NVDA"
  test("test Provide empty prices") {
    val prices = List.empty
    val provider = OpenGapMarketDataStrategyProvider(marketDataProvider)
    val request = OpenGapMarketDataStrategyRequest(symbol = "NVDA", signalCount = 2)
    when(
      marketDataProvider
        .getIntradayQuotesDaysRange(
          symbol = request.symbol,
          interval = SnapshotInterval.SixtyMinutes,
          daysRange = request.signalCount + 1
        )
    ).thenReturn(Success(prices))
    provider.provide(request = request) match
      case Failure(exception) => fail(exception)
      case Success(value) =>
        assert(value.signalInputs.isEmpty)
  }
  test("testProvide single entry") {
    val currentDateTime = LocalDateTime.of(2024, 7, 31, 10, 0)
    val prices = List(
      StockPrice(
        symbol = symbol,
        open = 1,
        close = 2,
        high = 2,
        low = 1,
        volume = 10,
        snapshotTime = currentDateTime.minus(1, ChronoUnit.DAYS).toZonedDateTime.toInstant
      )
    )
    val provider = OpenGapMarketDataStrategyProvider(marketDataProvider)
    val request = OpenGapMarketDataStrategyRequest(symbol = "NVDA", signalCount = 2)
    when(
      marketDataProvider
        .getIntradayQuotesDaysRange(
          symbol = request.symbol,
          interval = SnapshotInterval.SixtyMinutes,
          daysRange = request.signalCount + 1
        )
    ).thenReturn(Success(prices))
    provider.provide(request = request) match
      case Failure(exception) => fail(exception)
      case Success(value) =>
        assert(value.signalInputs.isEmpty)

  }
  test("testProvide Single price") {
    val currentDateTime = LocalDateTime.of(2024, 7, 31, 10, 0).toZonedDateTime.toInstant
    val prices = List(
      StockPrice(
        symbol = symbol,
        open = 1,
        close = 5,
        high = 2,
        low = 1,
        volume = 10,
        snapshotTime = currentDateTime.minus(1, ChronoUnit.DAYS)
      ),
      StockPrice(symbol = symbol, open = 2, close = 3, high = 4, low = 1, volume = 11, snapshotTime = currentDateTime)
    )
    val provider = OpenGapMarketDataStrategyProvider(marketDataProvider)
    val request = OpenGapMarketDataStrategyRequest(symbol = "NVDA", signalCount = 1)
    when(
      marketDataProvider
        .getIntradayQuotesDaysRange(
          symbol = request.symbol,
          interval = SnapshotInterval.SixtyMinutes,
          daysRange = request.signalCount + 1
        )
    ).thenReturn(Success(prices))
    provider.provide(request = request) match
      case Failure(exception) => fail(exception)
      case Success(value) =>
        assert(value.signalInputs.nonEmpty)
        assert(1 === value.signalInputs.size)
        val signalInput = value.signalInputs.head
        assert(signalInput.openingPrice === 2.0 )
        assert(signalInput.closingPrice === 5.0)
  }
  test("testProvide Multiple prices") {
    val currentDateTime = LocalDateTime.of(2024, 7, 31, 10, 0).toZonedDateTime.toInstant
    val dayBefore = currentDateTime.minus(1, ChronoUnit.DAYS)
    val dayBeforePrices = Range
      .inclusive(1, 5)
      .map(index =>
        StockPrice(
          symbol = symbol,
          open = 1 + index,
          close = 5 + index,
          high = 2,
          low = 1,
          volume = 10,
          snapshotTime = dayBefore.plus(index, ChronoUnit.HOURS)
        )
      )
    val currentPrices = Range
      .inclusive(1, 5)
      .map(index =>
        StockPrice(
          symbol = symbol,
          open = 1 + index,
          close = 5 + index,
          high = 2,
          low = 1,
          volume = 10,
          snapshotTime = currentDateTime.plus(index, ChronoUnit.HOURS)
        )
      )
    val prices = (dayBeforePrices ++ currentPrices).toList
    val provider = OpenGapMarketDataStrategyProvider(marketDataProvider)
    val request = OpenGapMarketDataStrategyRequest(symbol = "NVDA", signalCount = 1)
    when(
      marketDataProvider
        .getIntradayQuotesDaysRange(
          symbol = request.symbol,
          interval = SnapshotInterval.SixtyMinutes,
          daysRange = request.signalCount + 1
        )
    ).thenReturn(Success(prices))
    provider.provide(request = request) match
      case Failure(exception) => fail(exception)
      case Success(value) =>
        assert(value.signalInputs.nonEmpty)
        assert(value.signalInputs.size === 1)
        val signalInput = value.signalInputs.head
        assert(signalInput.openingPrice === 2.0)
        assert(signalInput.closingPrice === 10.0)
  }
  test("testProvide Multiple prices not sorted") {
    val currentDateTime = LocalDateTime.of(2024, 7, 31, 10, 0).toZonedDateTime.toInstant
    val dayBefore = currentDateTime.minus(1, ChronoUnit.DAYS)
    val dayBeforePrices = List(
      StockPrice(
        symbol = symbol,
        open = 1,
        close = 4,
        high = 2,
        low = 1,
        volume = 10,
        snapshotTime = dayBefore
      ),
      StockPrice(
        symbol = symbol,
        open = 1,
        close = 5,
        high = 2,
        low = 1,
        volume = 10,
        snapshotTime = dayBefore.plus(1, ChronoUnit.HOURS)
      )
    )
    val currentPrices = List(
      StockPrice(
        symbol = symbol,
        open = 4,
        close = 4,
        high = 2,
        low = 1,
        volume = 10,
        snapshotTime = currentDateTime
      ),
      StockPrice(
        symbol = symbol,
        open = 5,
        close = 5,
        high = 2,
        low = 1,
        volume = 10,
        snapshotTime = currentDateTime.plus(1, ChronoUnit.HOURS)
      )
    )
    val prices = (dayBeforePrices ++ currentPrices).toList
    val provider = OpenGapMarketDataStrategyProvider(marketDataProvider)
    val request = OpenGapMarketDataStrategyRequest(symbol = "NVDA", signalCount = 1)
    when(
      marketDataProvider
        .getIntradayQuotesDaysRange(
          symbol = request.symbol,
          interval = SnapshotInterval.SixtyMinutes,
          daysRange = request.signalCount + 1
        )
    ).thenReturn(Success(prices))
    provider.provide(request = request) match
      case Failure(exception) => fail(exception)
      case Success(value) =>
        assert(value.signalInputs.nonEmpty)
        assert(value.signalInputs.size === 1)
        val signalInput = value.signalInputs.head
        assert(signalInput.closingPrice === 5.0)
        assert(signalInput.openingPrice === 4.0)

  }
