package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, OpenGapStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy.opengap.{OpenGapMarketDataStrategyResponse, OpenGapSignalInput}
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import com.jworkdev.trading.robot.domain.TradingStrategyType.OpenGap
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.market.data.StockPrice
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
import com.jworkdev.trading.robot.{Order, OrderTrigger}
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatestplus.mockito.MockitoSugar.mock

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, LocalTime, ZonedDateTime}
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
    priceVolatility = Some(1.0),
    averageDailyVolume = None,
    preMarketGap = None,
    preMarketNumberOfShareTrades = None,
    averageTrueRange = None,
    exchange = exchangeName,
    creationDate = ZonedDateTime.now(),
    lastUpdate = None,
    isActive = true
  )
  private val signalFinderStrategy = mock[SignalFinderStrategy]
  private val forcePositionExitService = mock[ForcePositionExitService]
  private val tradingStrategyType = OpenGap
  private val stopLossPercentage = 10
  private val cfg = StrategyConfigurations(
    macd = Some(MACDStrategyConfiguration(snapshotInterval = OneMinute)),
    openGap = Some(OpenGapStrategyConfiguration(signalCount = 1))
  )

  test("testCreateNoEnoughOfCash") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0).toZonedDateTime
    val signalInput = OpenGapSignalInput(
      tradingDateTime = tradingTime,
      closingPrice = 100,
      openingPrice = 95,
      volumeAvg = 100,
      currentPrices = List.empty
    )
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput))
    val orderFactory = new OrderFactoryImpl(
      signalFinderStrategy = signalFinderStrategy,
      forcePositionExitService = forcePositionExitService
    )
    val signal = Signal(
      date = tradingTime,
      `type` = SignalType.Buy,
      stockPrice = StockPrice(
        symbol = symbol,
        open = 95,
        close = 95,
        high = 99,
        low = 90,
        volume = 100,
        snapshotTime = tradingTime
      )
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List(signal))
    val res = orderFactory.createBuy(
      finInstrument = finInstrument,
      tradeDateTime = tradingTime,
      tradingMode = IntraDay,
      tradingExchange = exchange,
      balancePerFinInst = 0.0,
      tradingPrice = tradingPrice,
      tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
      marketDataStrategyResponse = Success(marketDataStrategyResponse)
    )
    assert(res.isEmpty)
  }

  test("testExecuteBuy") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0).toZonedDateTime
    val stockPrice = StockPrice(
      symbol = symbol,
      open = 95,
      close = 95,
      high = 99,
      low = 90,
      volume = 100,
      snapshotTime = tradingTime
    )
    val signalInput = OpenGapSignalInput(
      tradingDateTime = tradingTime,
      closingPrice = 100,
      openingPrice = 95,
      volumeAvg = 100,
      currentPrices = List(stockPrice)
    )
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput))
    val orderFactory = new OrderFactoryImpl(
      signalFinderStrategy = signalFinderStrategy,
      forcePositionExitService = forcePositionExitService
    )
    val signal = Signal(
      date = tradingTime,
      `type` = SignalType.Buy,
      stockPrice = stockPrice
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List(signal))
    val res = orderFactory.createBuy(
      finInstrument = finInstrument,
      tradeDateTime = tradingTime,
      tradingMode = IntraDay,
      tradingExchange = exchange,
      balancePerFinInst = 400,
      tradingPrice = tradingPrice,
      tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
      marketDataStrategyResponse = Success(marketDataStrategyResponse)
    )
    assert(res.isDefined)
    assert(res.get.`type` === Buy)
  }

  test("testExecuteBuy validate not passed") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 17, 0).toZonedDateTime
    val stockPrice = StockPrice(
      symbol = symbol,
      open = 95,
      close = 95,
      high = 99,
      low = 90,
      volume = 100,
      snapshotTime = tradingTime
    )
    val signalInput = OpenGapSignalInput(
      tradingDateTime = tradingTime,
      closingPrice = 100,
      openingPrice = 95,
      volumeAvg = 100,
      currentPrices = List(stockPrice)
    )
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput))
    val orderFactory = new OrderFactoryImpl(
      signalFinderStrategy = signalFinderStrategy,
      forcePositionExitService = forcePositionExitService
    )
    val signal = Signal(
      date = tradingTime,
      `type` = SignalType.Buy,
      stockPrice = stockPrice
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List(signal))
    val res = orderFactory.createBuy(
      finInstrument = finInstrument,
      tradeDateTime = tradingTime,
      tradingMode = IntraDay,
      tradingExchange = exchange,
      balancePerFinInst = 400,
      tradingPrice = tradingPrice,
      tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
      marketDataStrategyResponse = Success(marketDataStrategyResponse)
    )
    assert(res.isEmpty)
  }

  test("testExecuteSell") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0).toZonedDateTime
    val stockPrice = StockPrice(
      symbol = symbol,
      open = 95,
      close = 95,
      high = 99,
      low = 90,
      volume = 100,
      snapshotTime = tradingTime
    )
    val signalInput = OpenGapSignalInput(
      tradingDateTime = tradingTime,
      closingPrice = 100,
      openingPrice = 95,
      volumeAvg = 100,
      currentPrices = List(stockPrice)
    )
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List(signalInput))
    val orderFactory = new OrderFactoryImpl(
      signalFinderStrategy = signalFinderStrategy,
      forcePositionExitService = forcePositionExitService
    )
    val signal = Signal(
      date = tradingTime,
      `type` = SignalType.Sell,
      stockPrice = stockPrice
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List(signal))
    val position = Position(
      id = 1,
      symbol = symbol,
      numberOfShares = 2,
      openPricePerShare = 100,
      closePricePerShare = None,
      openDate = tradingTime.minus(1, ChronoUnit.HOURS),
      closeDate = None,
      pnl = None,
      tradingStrategyType = TradingStrategyType.OpenGap
    )
    val res = orderFactory.createSell(
      position = position,
      finInstrument = finInstrument,
      tradingExchange = exchange,
      tradingMode = IntraDay,
      stopLossPercentage = 10,
      tradingPrice = tradingPrice,
      tradeDateTime = tradingTime,
      marketDataStrategyResponse = Success(marketDataStrategyResponse)
    )
    assert(res.isDefined)
    assert(res.get.`type` === Sell)
  }
  test("createSell with error") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0).toZonedDateTime
    val stockPrice = StockPrice(
      symbol = symbol,
      open = 95,
      close = 95,
      high = 99,
      low = 90,
      volume = 100,
      snapshotTime = tradingTime
    )
    val orderFactory = new OrderFactoryImpl(
      signalFinderStrategy = signalFinderStrategy,
      forcePositionExitService = forcePositionExitService
    )
    val signal = Signal(
      date = tradingTime,
      `type` = SignalType.Sell,
      stockPrice = stockPrice
    )
    val position = Position(
      id = 1,
      symbol = symbol,
      numberOfShares = 2,
      openPricePerShare = 100,
      closePricePerShare = None,
      openDate = tradingTime.minus(1, ChronoUnit.HOURS),
      closeDate = None,
      pnl = None,
      tradingStrategyType = TradingStrategyType.OpenGap
    )
    when(forcePositionExitService.executeCloseDayOrStopLoss(
      finInstrument = finInstrument,
      position = position,
      currentPrice = tradingPrice,
      stopLossPercentage = 10,
      tradeDateTime = tradingTime,
      tradingExchange = exchange,
      tradingMode = IntraDay
    )).thenReturn(Some(Order(`type`= Sell,
      symbol = symbol,
      dateTime = tradingTime,
      shares = 100,
      price = tradingPrice,
      tradingStrategyType= TradingStrategyType.OpenGap,
      positionId = Some(position.id),
      trigger= OrderTrigger.StopLoss)))
    val res = orderFactory.createSell(
      position = position,
      finInstrument = finInstrument,
      tradingExchange = exchange,
      tradingMode = IntraDay,
      stopLossPercentage = 10,
      tradingPrice = tradingPrice,
      tradeDateTime = tradingTime,
      marketDataStrategyResponse = Failure(new IllegalStateException("some error"))
    )
    assert(res.isDefined)
    assert(res.get.trigger == OrderTrigger.StopLoss)
  }
  test("testExecuteSell No Signal") {
    val tradingPrice = 100
    val tradingTime = LocalDateTime.of(2024, 8, 9, 10, 0).toZonedDateTime
    val stockPrice = StockPrice(
      symbol = symbol,
      open = 95,
      close = 95,
      high = 99,
      low = 90,
      volume = 100,
      snapshotTime = tradingTime
    )
    val marketDataStrategyResponse = OpenGapMarketDataStrategyResponse(signalInputs = List.empty)
    val orderFactory = new OrderFactoryImpl(
      signalFinderStrategy = signalFinderStrategy,
      forcePositionExitService = forcePositionExitService
    )
    when(signalFinderStrategy.findSignals(signalFinderRequest = marketDataStrategyResponse.buildSignalFinderRequest()))
      .thenReturn(List.empty)
    val position = Position(
      id = 1,
      symbol = symbol,
      numberOfShares = 2,
      openPricePerShare = 100,
      closePricePerShare = None,
      openDate = tradingTime.minus(1, ChronoUnit.HOURS),
      closeDate = None,
      pnl = None,
      tradingStrategyType = TradingStrategyType.OpenGap
    )
    when(forcePositionExitService.executeCloseDayOrStopLoss(
      finInstrument = finInstrument,
      position = position,
      currentPrice = tradingPrice,
      stopLossPercentage = 10,
      tradeDateTime = tradingTime,
      tradingExchange = exchange,
      tradingMode = IntraDay
    )).thenReturn(Some(Order(`type` = Sell,
      symbol = symbol,
      dateTime = tradingTime,
      shares = 100,
      price = tradingPrice,
      tradingStrategyType = TradingStrategyType.OpenGap,
      positionId = Some(position.id),
      trigger = OrderTrigger.StopLoss)))
    val res = orderFactory.createSell(
      position = position,
      finInstrument = finInstrument,
      tradingExchange = exchange,
      tradingMode = IntraDay,
      stopLossPercentage = 10,
      tradingPrice = tradingPrice,
      tradeDateTime = tradingTime,
      marketDataStrategyResponse = Success(marketDataStrategyResponse)
    )
    assert(res.isDefined)
    assert(res.get.trigger == OrderTrigger.StopLoss)
  }
