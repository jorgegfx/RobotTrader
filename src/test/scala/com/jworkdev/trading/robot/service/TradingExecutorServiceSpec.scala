package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.strategy.macd.{MACDMarketDataStrategyRequest, MACDMarketDataStrategyResponse}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.{FinInstrumentConfig, FinInstrumentType, TradingStrategyType}
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.test.{Spec, ZIOSpecDefault, assertTrue}

import scala.util.Success

object TradingExecutorServiceSpec extends ZIOSpecDefault:
  private val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
    mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
  private val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
  val tradingExecutorService =
    new TradingExecutorServiceImpl(
      marketDataStrategyProvider = marketDataStrategyProvider,
      marketDataStrategyRequestFactory = marketDataStrategyRequestFactory
    )
  val balancePerFinInst = 1000
  val symbol = "NVDA"
  def spec: Spec[Any, Throwable] = suite("testExecute")(
    test("No prices, No signals found, empty orders") {
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
      when(marketDataStrategyProvider.provide(request =
        MACDMarketDataStrategyRequest(symbol = symbol, snapshotInterval = OneMinute))).
        thenReturn(Success(macdMarketDataStrategyResponse))
      for
        orders <- tradingExecutorService.execute(
          balancePerFinInst = balancePerFinInst,
          finInstrumentConfigs = finInstrumentConfigs,
          openPositions = List.empty,
          strategyConfigurations = strategyConfigurations
        )
      yield assertTrue(orders.isEmpty)
    },
    test("No signals found, empty orders") {
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
      when(marketDataStrategyProvider.provide(request =
        MACDMarketDataStrategyRequest(symbol = symbol, snapshotInterval = OneMinute))).
        thenReturn(Success(macdMarketDataStrategyResponse))
      for
        orders <- tradingExecutorService.execute(
          balancePerFinInst = balancePerFinInst,
          finInstrumentConfigs = finInstrumentConfigs,
          openPositions = List.empty,
          strategyConfigurations = strategyConfigurations
        )
      yield assertTrue(orders.isEmpty)
    })

