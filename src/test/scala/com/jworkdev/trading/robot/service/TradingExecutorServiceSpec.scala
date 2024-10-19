package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, StrategyConfigurations, TradingMode}
import com.jworkdev.trading.robot.data.signals.SignalFinderStrategy
import com.jworkdev.trading.robot.data.strategy.macd.{MACDMarketDataStrategyRequest, MACDMarketDataStrategyResponse}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import com.jworkdev.trading.robot.domain.TradingStrategyType.{MACD, OpenGap}
import com.jworkdev.trading.robot.market.data.MarketDataProvider
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.strategy.{TradingStrategyEntryRequest, TradingStrategyExecutor}
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
import com.jworkdev.trading.robot.{Order, OrderTrigger, OrderType}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.*
import zio.test.{test, *}

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, LocalTime, ZonedDateTime}
import scala.util.Success

object TradingExecutorServiceSpec extends ZIOSpecDefault:

  private val tradingDateTime = LocalDateTime.of(2024, 7, 30, 10, 0).toZonedDateTime
  private val exchange = TradingExchange(
    id = "NASDAQ",
    name = "NASDAQ",
    openingTime = Some(tradingDateTime.toLocalTime.minus(1, ChronoUnit.HOURS)),
    closingTime = Some(tradingDateTime.toLocalTime.plus(2, ChronoUnit.HOURS)),
    timezone = Some("America/New_York"),
    windowType = BusinessDaysWeek
  )
  private val exchangeMap = Map(
    "NASDAQ" -> exchange
  )
  val balancePerFinInst = 1000
  val symbol = "NVDA"

  def spec: Spec[Any, Throwable] = suite("testExecute")(
    test("Entry") {
      val marketDataStrategyProvider
          : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataProvider = mock[MarketDataProvider]
      val tradingStrategyExecutor = mock[TradingStrategyExecutor]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          tradingStrategyExecutorMap = Map(OpenGap -> tradingStrategyExecutor,
                                           MACD -> tradingStrategyExecutor)
        )
      val tradingPrice: Double = 2.0d
      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices = List.empty)
      val strategyConfigurations =
        StrategyConfigurations(macd = Some(MACDStrategyConfiguration(snapshotInterval = OneMinute)))
      when(
        marketDataStrategyProvider.provide(request =
          MACDMarketDataStrategyRequest(symbol = symbol, snapshotInterval = OneMinute)
        )
      ).thenReturn(Success(macdMarketDataStrategyResponse))
      when(
        signalFinderStrategy.findSignals(signalFinderRequest =
          macdMarketDataStrategyResponse.buildSignalFinderRequest()
        )
      ).thenReturn(List.empty)
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(tradingPrice))
      val finInstruments = buildFinInstrument(symbol = symbol)
      when(tradingStrategyExecutor.executeEntry(tradingStrategyEntryRequest =
        TradingStrategyEntryRequest(
          balancePerFinInst = balancePerFinInst,
          finInstrument = finInstruments.head,
          exchange = exchange,
          tradingStrategy = tradingStrategies.head,
          tradingMode = TradingMode.IntraDay,
          tradingPrice = tradingPrice,
          tradeDateTime = tradingDateTime,
          marketDataStrategyResponse = Success(macdMarketDataStrategyResponse)
        )
      )).thenReturn(Some(Order(`type`= OrderType.Buy,
        symbol = symbol,
        dateTime = tradingDateTime,
        shares = 100,
        price = tradingPrice,
        tradingStrategyType= TradingStrategyType.OpenGap,
        positionId = None,
        trigger= OrderTrigger.Signal)))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            maxTradingCapitalPerTrade = balancePerFinInst,
            finInstrumentMap = finInstruments.map((_,List.empty)).toMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            takeProfitPercentage = 80,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
          )
        )
      yield assertTrue(orders.nonEmpty)
    },test("Exit") {
      val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataProvider = mock[MarketDataProvider]
      val tradingStrategyExecutor = mock[TradingStrategyExecutor]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          tradingStrategyExecutorMap = Map(OpenGap -> tradingStrategyExecutor,
            MACD -> tradingStrategyExecutor)
        )
      val tradingPrice: Double = 2.0d
      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices = List.empty)
      val strategyConfigurations =
        StrategyConfigurations(macd = Some(MACDStrategyConfiguration(snapshotInterval = OneMinute)))
      when(
        marketDataStrategyProvider.provide(request =
          MACDMarketDataStrategyRequest(symbol = symbol, snapshotInterval = OneMinute)
        )
      ).thenReturn(Success(macdMarketDataStrategyResponse))
      when(
        signalFinderStrategy.findSignals(signalFinderRequest =
          macdMarketDataStrategyResponse.buildSignalFinderRequest()
        )
      ).thenReturn(List.empty)
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(tradingPrice))
      val finInstruments = buildFinInstrument(symbol = symbol)
      val position = Position(
        id = 1,
        symbol = symbol,
        numberOfShares = 2,
        openPricePerShare = 100,
        closePricePerShare = None,
        openDate = tradingDateTime.minus(1, ChronoUnit.HOURS),
        closeDate = None,
        pnl = None,
        tradingStrategyType = TradingStrategyType.MACD
      )
      when(tradingStrategyExecutor.executeEntry(tradingStrategyEntryRequest =
        TradingStrategyEntryRequest(
          balancePerFinInst = balancePerFinInst,
          finInstrument = finInstruments.head,
          exchange = exchange,
          tradingStrategy = tradingStrategies.head,
          tradingMode = TradingMode.IntraDay,
          tradingPrice = tradingPrice,
          tradeDateTime = tradingDateTime,
          marketDataStrategyResponse = Success(macdMarketDataStrategyResponse)
        )
      )).thenReturn(Some(Order(`type`= OrderType.Sell,
        symbol = symbol,
        dateTime = tradingDateTime,
        shares = 100,
        price = tradingPrice,
        tradingStrategyType= TradingStrategyType.OpenGap,
        positionId = Some(position.id),
        trigger= OrderTrigger.Signal)))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            maxTradingCapitalPerTrade = balancePerFinInst,
            finInstrumentMap = finInstruments.map((_,List.empty)).toMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            takeProfitPercentage = 80,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
          )
        )
      yield assertTrue(orders.nonEmpty)
    }
  )

  private def buildFinInstrument(symbol: String) = List(
    FinInstrument(
      symbol = symbol,
      name = "Nvidia",
      `type` = FinInstrumentType.Stock,
      exchange = "NASDAQ",
      priceVolatility = Some(1.0),
      averageDailyVolume = None,
      preMarketGap = None,
      preMarketNumberOfShareTrades = None,
      averageTrueRange = None,
      creationDate = ZonedDateTime.now(),
      lastUpdate = None,
      isActive = true
    )
  )
