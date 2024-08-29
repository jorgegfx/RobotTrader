package com.jworkdev.trading.robot.strategy

import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.data.signals.{Signal, SignalType}
import com.jworkdev.trading.robot.data.strategy.opengap.{OpenGapMarketDataStrategyResponse, OpenGapSignalInput}
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import com.jworkdev.trading.robot.domain.TradingStrategyType.OpenGap
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.market.data.StockPrice
import com.jworkdev.trading.robot.service.OrderFactory
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
import com.jworkdev.trading.robot.{Order, OrderTrigger, OrderType}
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatestplus.mockito.MockitoSugar.mock

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, LocalTime, ZonedDateTime}
import scala.util.Success

class OpenGapTradingStrategyExecutorTest extends AnyFunSuiteLike:
  private val orderFactory = mock[OrderFactory]
  private val openGapTradingStrategyExecutor = new OpenGapTradingStrategyExecutor(
    orderFactory = orderFactory
  )
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
    creationDate = ZonedDateTime.now(),
    lastUpdate = None,
    isActive = true
  )
  private val tradingStrategy = TradingStrategy(`type` = OpenGap, None)

  test("testExecuteExit") {
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
    when(
      orderFactory.createSell(
        position = position,
        finInstrument = finInstrument,
        tradingExchange = exchange,
        tradingMode = IntraDay,
        stopLossPercentage = 10,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    ).thenReturn(Some(Order(`type`= Sell,
      symbol = symbol,
      dateTime = tradingTime,
      shares = 100,
      price = tradingPrice,
      tradingStrategyType= TradingStrategyType.OpenGap,
      positionId = Some(position.id),
      trigger= OrderTrigger.Signal)))
    val res = openGapTradingStrategyExecutor.executeExit(request =
      TradingStrategyExitRequest(
        position = position,
        finInstrument = finInstrument,
        exchange = exchange,
        tradingStrategy = tradingStrategy,
        tradingMode = IntraDay,
        stopLossPercentage = 10,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isDefined)
  }

  test("testExecuteEntry") {
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
    val signal = Signal(
      date = tradingTime,
      `type` = SignalType.Buy,
      stockPrice = stockPrice
    )
    when(
      orderFactory.createBuy(
        finInstrument = finInstrument,
        tradeDateTime = tradingTime,
        tradingMode = IntraDay,
        tradingExchange = exchange,
        balancePerFinInst = 400,
        tradingPrice = tradingPrice,
        tradingStrategy = tradingStrategy,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    ).thenReturn(Some(Order(`type`= Buy,
      symbol = symbol,
      dateTime = tradingTime,
      shares = 100,
      price = tradingPrice,
      tradingStrategyType= TradingStrategyType.OpenGap,
      positionId = None,
      trigger= OrderTrigger.Signal)))
    val res = openGapTradingStrategyExecutor.executeEntry(request =
      TradingStrategyEntryRequest(
        balancePerFinInst = 400,
        finInstrument = finInstrument,
        exchange = exchange,
        tradingStrategy = tradingStrategy,
        tradingMode = IntraDay,
        tradingPrice = tradingPrice,
        tradeDateTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isDefined)
  }
