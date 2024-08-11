package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, OpenGapStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy.opengap.{OpenGapMarketDataStrategyResponse, OpenGapSignalInput}
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import com.jworkdev.trading.robot.domain.TradingStrategyType.OpenGap
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.market.data.StockPrice
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatestplus.mockito.MockitoSugar.mock

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, LocalTime}
import scala.util.{Failure, Success}

class OrderFactoryTest extends AnyFunSuiteLike:
  private val symbol = "BFI"
  private val exchangeName = "NASDAQ"
  private val exchange = TradingExchange(
    id = exchangeName,
    name = exchangeName,
    windowType = BusinessDaysWeek,
    openingTime = Some(LocalTime.of(9, 0)),
    closingTime = Some(LocalTime.of(16, 0)),
    timezone = Some("America/New_York")
  )
  private val finInstrument = FinInstrument(
    symbol = symbol,
    name = "",
    `type` = Stock,
    volatility = Some(1.0),
    exchange = exchangeName,
    creationDate = Instant.now(),
    lastUpdate = None,
    isActive = true
  )
  private val signalFinderStrategy = mock[SignalFinderStrategy]
  private val tradingStrategyType = OpenGap
  private val stopLossPercentage = 10
  private val cfg = StrategyConfigurations(
    macd = Some(MACDStrategyConfiguration(snapshotInterval = OneMinute)),
    openGap = Some(OpenGapStrategyConfiguration(signalCount = 1))
  )
  test("testCreateNoEnoughOfCash") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0)
    val signalInput = OpenGapSignalInput(
      tradingDateTime = tradingTime,
      closingPrice = 100,
      openingPrice = 95,
      volumeAvg = 100,
      currentPrices = List.empty
    )
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput))
    val orderFactory = new OrderFactoryImpl(signalFinderStrategy = signalFinderStrategy)
    val signal = Signal(
      date = tradingTime.toZonedDateTime.toInstant,
      `type` = SignalType.Buy,
      stockPrice = StockPrice(
        symbol = symbol,
        open = 95,
        close = 95,
        high = 99,
        low = 90,
        volume = 100,
        snapshotTime = tradingTime.toZonedDateTime.toInstant
      )
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List(signal))
    val res = orderFactory.create(orderRequest =
      OrderRequest(
        balancePerFinInst = 0.0,
        finInstrument = finInstrument,
        tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
        openPosition = None,
        exchangeMap = Map(exchangeName -> exchange),
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isEmpty)
  }

  test("testExecuteBuy") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0)
    val stockPrice = StockPrice(
      symbol = symbol,
      open = 95,
      close = 95,
      high = 99,
      low = 90,
      volume = 100,
      snapshotTime = tradingTime.toZonedDateTime.toInstant
    )
    val signalInput = OpenGapSignalInput(
      tradingDateTime = tradingTime,
      closingPrice = 100,
      openingPrice = 95,
      volumeAvg = 100,
      currentPrices = List(stockPrice)
    )
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput))
    val orderFactory = new OrderFactoryImpl(signalFinderStrategy = signalFinderStrategy)
    val signal = Signal(
      date = tradingTime.toZonedDateTime.toInstant,
      `type` = SignalType.Buy,
      stockPrice = stockPrice
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List(signal))
    val res = orderFactory.create(orderRequest =
      OrderRequest(
        balancePerFinInst = 400,
        finInstrument = finInstrument,
        tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
        openPosition = None,
        exchangeMap = Map(exchangeName -> exchange),
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isDefined)
    assert(res.get.`type` === Buy)
  }

  test("testExecuteSell") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0)
    val stockPrice = StockPrice(
      symbol = symbol,
      open = 95,
      close = 95,
      high = 99,
      low = 90,
      volume = 100,
      snapshotTime = tradingTime.toZonedDateTime.toInstant
    )
    val signalInput = OpenGapSignalInput(
      tradingDateTime = tradingTime,
      closingPrice = 100,
      openingPrice = 95,
      volumeAvg = 100,
      currentPrices = List(stockPrice)
    )
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput))
    val orderFactory = new OrderFactoryImpl(signalFinderStrategy = signalFinderStrategy)
    val signal = Signal(
      date = tradingTime.toZonedDateTime.toInstant,
      `type` = SignalType.Sell,
      stockPrice = stockPrice
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List(signal))
    val res = orderFactory.create(orderRequest =
      OrderRequest(
        balancePerFinInst = 400,
        finInstrument = finInstrument,
        tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
        openPosition = Some(Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = tradingTime.minus(1, ChronoUnit.HOURS).toZonedDateTime.toInstant,
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.OpenGap
        )),
        exchangeMap = Map(exchangeName -> exchange),
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isDefined)
    assert(res.get.`type` === Sell)
  }

  test("testStopLossWithNoSell") {
    val tradingPrice = 70
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0)
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List.empty)
    val orderFactory = new OrderFactoryImpl(signalFinderStrategy = signalFinderStrategy)
    val stockPrice = StockPrice(
      symbol = symbol,
      open = 95,
      close = 95,
      high = 99,
      low = 90,
      volume = 100,
      snapshotTime = tradingTime.toZonedDateTime.toInstant
    )
    val signal = Signal(
      date = tradingTime.toZonedDateTime.toInstant,
      `type` = SignalType.Buy,
      stockPrice = stockPrice
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List(signal))
    val res = orderFactory.create(orderRequest =
      OrderRequest(
        balancePerFinInst = 0.0,
        finInstrument = finInstrument,
        tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
        openPosition = Some(Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = tradingTime.minus(1, ChronoUnit.HOURS).toZonedDateTime.toInstant,
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.OpenGap
        )),
        exchangeMap = Map(exchangeName -> exchange),
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isDefined)
  }
  test("testStopLoss") {
    val tradingPrice = 70
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0)
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List.empty)
    val orderFactory = new OrderFactoryImpl(signalFinderStrategy = signalFinderStrategy)
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List.empty)
    val res = orderFactory.create(orderRequest =
      OrderRequest(
        balancePerFinInst = 0.0,
        finInstrument = finInstrument,
        tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
        openPosition = Some(Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = tradingTime.minus(1, ChronoUnit.HOURS).toZonedDateTime.toInstant,
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.OpenGap
        )),
        exchangeMap = Map(exchangeName -> exchange),
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isDefined)
  }
  test("testStopLossOnError") {
    val tradingPrice = 70
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0)
    val orderFactory = new OrderFactoryImpl(signalFinderStrategy = signalFinderStrategy)
    val res = orderFactory.create(orderRequest =
      OrderRequest(
        balancePerFinInst = 0.0,
        finInstrument = finInstrument,
        tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
        openPosition = Some(Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = tradingTime.minus(1, ChronoUnit.HOURS).toZonedDateTime.toInstant,
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.OpenGap
        )),
        exchangeMap = Map(exchangeName -> exchange),
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Failure(new IllegalStateException("Some error"))
      )
    )
    assert(res.isDefined)
  }
  test("testCloseDay") {
    val tradingPrice = 110
    val tradingTime = LocalDateTime.of(2024, 8, 9, 15, 35)
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List.empty)
    val orderFactory = new OrderFactoryImpl(signalFinderStrategy = signalFinderStrategy)
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List.empty)
    val res = orderFactory.create(orderRequest =
      OrderRequest(
        balancePerFinInst = 0.0,
        finInstrument = finInstrument,
        tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
        openPosition = Some(Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = tradingTime.minus(1, ChronoUnit.HOURS).toZonedDateTime.toInstant,
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.OpenGap
        )),
        exchangeMap = Map(exchangeName -> exchange),
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isDefined)
  }
