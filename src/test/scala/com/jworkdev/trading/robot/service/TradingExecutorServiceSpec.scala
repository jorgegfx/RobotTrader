package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.strategy.macd.{MACDMarketDataStrategyRequest, MACDMarketDataStrategyResponse}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.{FinInstrument, FinInstrumentType, Position, TradingStrategy, TradingStrategyType}
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.market.data.StockPrice
import com.jworkdev.trading.robot.{Order, OrderType}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.*
import zio.test.{test, *}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.util.Success

object TradingExecutorServiceSpec extends ZIOSpecDefault:

  def spec: Spec[Any, Throwable] = suite("testExecute")(
    test("No prices, No signals found, empty orders") {
      val balancePerFinInst = 1000
      val symbol = "NVDA"
      val marketDataStrategyProvider
          : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
        )
      val finInstruments = List(
        FinInstrument(
          symbol = symbol,
          `type` = FinInstrumentType.Stock,
          exchange = "NASDAQ",
          volatility = Some(10D),
          creationDate = Instant.now(),
          lastUpdate = None
        )
      )
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
      for
        orders <- tradingExecutorService.execute(
          balancePerFinInst = balancePerFinInst,
          finInstruments = finInstruments,
          tradingStrategies = tradingStrategies,
          openPositions = List.empty,
          strategyConfigurations = strategyConfigurations
        )
      yield assertTrue(orders.isEmpty)
    },
    test("Buy Signal found") {
      val balancePerFinInst = 1000
      val symbol = "NVDA"
      val marketDataStrategyProvider
          : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
        )
      val finInstruments = List(
        FinInstrument(
          symbol = symbol,
          `type` = FinInstrumentType.Stock,
          exchange = "NASDAQ",
          volatility = Some(10D),
          creationDate = Instant.now(),
          lastUpdate = None
        )
      )
      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices =
        List(
          StockPrice(symbol = symbol, open = 1, close = 2, high = 2, low = 1, volume = 10, snapshotTime = Instant.now())
        )
      )
      val signals = macdMarketDataStrategyResponse.prices.map(price =>
        Signal(date = price.snapshotTime, `type` = Buy, stockPrice = price)
      )
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
      ).thenReturn(signals)
      for
        orders <- tradingExecutorService.execute(
          balancePerFinInst = balancePerFinInst,
          finInstruments = finInstruments,
          tradingStrategies = tradingStrategies,
          openPositions = List.empty,
          strategyConfigurations = strategyConfigurations
        )
      yield assertTrue(
        orders.map(order => (order.`type`, order.price, order.shares)) == List((OrderType.Buy, 2.0d, 500L))
      )
    },
    test("Sell Signal found") {
      val balancePerFinInst = 1000
      val symbol = "NVDA"
      val marketDataStrategyProvider
          : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
        )
      val finInstruments = List(
        FinInstrument(
          symbol = symbol,
          `type` = FinInstrumentType.Stock,
          exchange = "NASDAQ",
          volatility = Some(10D),
          creationDate = Instant.now(),
          lastUpdate = None
        )
      )
      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices =
        List(
          StockPrice(
            symbol = symbol,
            open = 100,
            close = 200,
            high = 250,
            low = 50,
            volume = 10,
            snapshotTime = Instant.now()
          )
        )
      )
      val signals = macdMarketDataStrategyResponse.prices.map(price =>
        Signal(date = price.snapshotTime, `type` = Sell, stockPrice = price)
      )
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
      ).thenReturn(signals)
      for
        orders <- tradingExecutorService.execute(
          balancePerFinInst = balancePerFinInst,
          finInstruments = finInstruments,
          tradingStrategies = tradingStrategies,
          openPositions = List(
            Position(
              id = 1,
              symbol = symbol,
              numberOfShares = 2,
              openPricePerShare = 100,
              closePricePerShare = None,
              openDate = Instant.now().minus(1, ChronoUnit.HOURS),
              closeDate = None,
              pnl = None,
              tradingStrategyType = TradingStrategyType.MACD
            )
          ),
          strategyConfigurations = strategyConfigurations
        )
      yield assertTrue(
        orders.map(order => (order.`type`, order.price, order.shares)) == List((OrderType.Sell, 200.0d, 2L))
      )
    }
  )
