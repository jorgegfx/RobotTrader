package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, OpenGapStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy.opengap.{OpenGapMarketDataStrategyResponse, OpenGapSignalInput}
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import com.jworkdev.trading.robot.domain.TradingStrategyType.OpenGap
import com.jworkdev.trading.robot.domain.{FinInstrument, TradingExchange, TradingStrategy}
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.market.data.StockPrice
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatestplus.mockito.MockitoSugar.mock

import java.time.{Instant, LocalDateTime, LocalTime}
import scala.util.Success

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
        strategyConfigurations = cfg,
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = tradingPrice,
        tradingTime = tradingTime,
        marketDataStrategyResponse = Success(marketDataStrategyResponse)
      )
    )
    assert(res.isEmpty)

  }
