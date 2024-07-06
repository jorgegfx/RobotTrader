package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.StrategyConfigurations
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.{FinInstrumentConfig, FinInstrumentType, TradingStrategyType}
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.test.{Spec, ZIOSpecDefault, assertTrue}

object TradingExecutorServiceSpec extends ZIOSpecDefault:
  private val marketDataStrategyProvider: MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]
  = mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
  private val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory =
    mock[MarketDataStrategyRequestFactory]
  def spec: Spec[Any, Throwable] = suite("testExecute") {
    val tradingExecutorService =
      new TradingExecutorServiceImpl(
        marketDataStrategyProvider = marketDataStrategyProvider,
        marketDataStrategyRequestFactory = marketDataStrategyRequestFactory
      )
    val balancePerFinInst = 1000
    val symbol = "NVDA"
    val finInstrumentConfigs = List(
      FinInstrumentConfig(
        symbol = symbol,
        pnl = None,
        strategy = TradingStrategyType.MACD,
        finInstrumentType = FinInstrumentType.Stock,
        lastPnlUpdate = None
      )
    )
    val strategyConfigurations = StrategyConfigurations()
    test("executes a buy when no openPositions") {
      for
        orders <- tradingExecutorService.execute(
          balancePerFinInst = balancePerFinInst,
          finInstrumentConfigs = finInstrumentConfigs,
          openPositions = List.empty,
          strategyConfigurations = strategyConfigurations
        )
      yield assertTrue(orders.nonEmpty)
    }
  }
