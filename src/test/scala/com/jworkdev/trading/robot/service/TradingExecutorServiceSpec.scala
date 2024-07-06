package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals.SignalType.Buy
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.strategy.macd.{MACDMarketDataStrategyRequest, MACDMarketDataStrategyResponse}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.{FinInstrumentConfig, FinInstrumentType, TradingStrategyType}
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.market.data.StockPrice
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.test.{Spec, ZIOSpecDefault, assertTrue}

import java.time.Instant
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
      val finInstrumentConfigs = List(
        FinInstrumentConfig(
          symbol = symbol,
          pnl = None,
          strategy = TradingStrategyType.MACD,
          finInstrumentType = FinInstrumentType.Stock,
          lastPnlUpdate = None
        )
      )
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
          finInstrumentConfigs = finInstrumentConfigs,
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
      val finInstrumentConfigs = List(
        FinInstrumentConfig(
          symbol = symbol,
          pnl = None,
          strategy = TradingStrategyType.MACD,
          finInstrumentType = FinInstrumentType.Stock,
          lastPnlUpdate = None
        )
      )
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices =
        List(
          StockPrice(symbol = symbol, open = 1, close = 2, high = 2, low = 1, volume = 10, snapshotTime = Instant.now())
        )
      )
      val signals = macdMarketDataStrategyResponse.prices.map(price => Signal(date = price.snapshotTime, `type` = Buy, stockPrice = price))
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
          finInstrumentConfigs = finInstrumentConfigs,
          openPositions = List.empty,
          strategyConfigurations = strategyConfigurations
        )
      yield assertTrue(orders.nonEmpty)
    }
  )
