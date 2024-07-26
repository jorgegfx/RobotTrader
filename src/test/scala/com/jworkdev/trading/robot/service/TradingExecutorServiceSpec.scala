package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, StrategyConfigurations, TradingMode}
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.strategy.macd.{MACDMarketDataStrategyRequest, MACDMarketDataStrategyResponse}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.{FinInstrument, FinInstrumentType, Position, TradingExchange, TradingStrategy, TradingStrategyType}
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, StockPrice}
import com.jworkdev.trading.robot.{Order, OrderType}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.*
import zio.test.{test, *}

import java.time.{Instant, LocalTime}
import java.time.temporal.ChronoUnit
import scala.util.{Failure, Success}

object TradingExecutorServiceSpec extends ZIOSpecDefault:
  private val exchangeMap = Map(
    "NASDAQ" -> TradingExchange(
      id = "NASDAQ",
      name = "NASDAQ",
      openingTime = LocalTime.now().minus(1, ChronoUnit.HOURS),
      closingTime = LocalTime.now().plus(2, ChronoUnit.HOURS),
      timezone = "America/New_York"
    )
  )
  val balancePerFinInst = 1000
  val symbol = "NVDA"

  def spec: Spec[Any, Throwable] = suite("testExecute")(
    test("No prices, No signals found, empty orders") {
      val marketDataStrategyProvider
          : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataProvider = mock[MarketDataProvider]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
            tradingStrategies = tradingStrategies,
            openPositions = List.empty,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(orders.isEmpty)
    },
    test("No prices, No signals found, error on current price") {
      val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataProvider = mock[MarketDataProvider]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Failure(new IllegalStateException("Some Error")))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
            tradingStrategies = tradingStrategies,
            openPositions = List.empty,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(orders.isEmpty)
    },
    test("Buy Signal found") {
      val marketDataStrategyProvider
          : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val marketDataProvider = mock[MarketDataProvider]
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
            tradingStrategies = tradingStrategies,
            openPositions = List.empty,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(
        orders.map(order => (order.`type`, order.price, order.shares)) == List((OrderType.Buy, 2.0d, 500L))
      )
    },
    test("Buy Signal found After Trading Window") {
      val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val marketDataProvider = mock[MarketDataProvider]
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      val offExchangeMap = Map(
        "NASDAQ" -> TradingExchange(
          id = "NASDAQ",
          name = "NASDAQ",
          openingTime = LocalTime.now().minus(2, ChronoUnit.HOURS),
          closingTime = LocalTime.now().minus(1, ChronoUnit.HOURS),
          timezone = "America/New_York"
        )
      )
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
            tradingStrategies = tradingStrategies,
            openPositions = List.empty,
            exchangeMap = offExchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(
        orders.isEmpty
      )
    },
    test("Buy Signal found from Yesterday") {
      val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val marketDataProvider = mock[MarketDataProvider]
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
        )
      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices =
        List(
          StockPrice(symbol = symbol,
            open = 1,
            close = 2,
            high = 2,
            low = 1,
            volume = 10,
            snapshotTime = Instant.now().minus(1, ChronoUnit.DAYS))
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
            tradingStrategies = tradingStrategies,
            openPositions = List.empty,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(
        orders.isEmpty
      )
    },
    test("Sell Signal found") {
      val marketDataStrategyProvider
          : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val marketDataProvider = mock[MarketDataProvider]
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(200.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
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
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(
        orders.map(order => (order.`type`, order.price, order.shares)) == List((OrderType.Sell, 200.0d, 2L))
      )
    },
    test("Stop loss") {
      val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataProvider = mock[MarketDataProvider]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
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
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(
        orders.map(order => (order.`type`, order.price, order.shares)) == List((OrderType.Sell, 2.0d, 2L))
      )
    },
    test("Stop loss with Buy Signal") {
      val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataProvider = mock[MarketDataProvider]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
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
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(
        orders.map(order => (order.`type`, order.price, order.shares)) == List((OrderType.Sell, 2.0d, 2L))
      )
    },
    test("Stop loss having signal data fetching error") {
      val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataProvider = mock[MarketDataProvider]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
        )

      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices = List.empty)
      val strategyConfigurations =
        StrategyConfigurations(macd = Some(MACDStrategyConfiguration(snapshotInterval = OneMinute)))
      when(
        marketDataStrategyProvider.provide(request =
          MACDMarketDataStrategyRequest(symbol = symbol, snapshotInterval = OneMinute)
        )
      ).thenReturn(Failure(new IllegalStateException("Some Error!")))
      when(
        signalFinderStrategy.findSignals(signalFinderRequest =
          macdMarketDataStrategyResponse.buildSignalFinderRequest()
        )
      ).thenReturn(List.empty)
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
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
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(
        orders.map(order => (order.`type`, order.price, order.shares)) == List((OrderType.Sell, 2.0d, 2L))
      )
    },
    test("No Stop loss") {
      val marketDataStrategyProvider
      : MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse] =
        mock[MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse]]
      val signalFinderStrategy = mock[SignalFinderStrategy]
      val marketDataProvider = mock[MarketDataProvider]
      val marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy
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
      when(marketDataProvider.getCurrentQuote(symbol = symbol)).thenReturn(Success(98.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstruments = buildFinInstrument(symbol = symbol),
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
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay
          )
        )
      yield assertTrue(orders.isEmpty)
    }
  )

  private def buildFinInstrument(symbol: String) = List(
    FinInstrument(
      symbol = symbol,
      name = "Nvidia",
      `type` = FinInstrumentType.Stock,
      exchange = "NASDAQ",
      volatility = Some(10d),
      creationDate = Instant.now(),
      lastUpdate = None,
      isActive = true
    )
  )
