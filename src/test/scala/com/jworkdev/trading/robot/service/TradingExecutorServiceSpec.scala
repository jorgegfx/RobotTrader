package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.OrderTrigger.Signal as SignalTrigger
import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, StrategyConfigurations, TradingMode}
import com.jworkdev.trading.robot.data.signals.SignalType.{Buy, Sell}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy}
import com.jworkdev.trading.robot.data.strategy.macd.{MACDMarketDataStrategyRequest, MACDMarketDataStrategyResponse}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.market.data.{MarketDataProvider, StockPrice}
import com.jworkdev.trading.robot.time.LocalDateTimeExtensions.toZonedDateTime
import com.jworkdev.trading.robot.{Order, OrderType}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import zio.*
import zio.test.{test, *}

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, LocalTime, ZonedDateTime}
import scala.util.{Failure, Success}

object TradingExecutorServiceSpec extends ZIOSpecDefault:

  private val tradingDateTime = LocalDateTime.of(2024, 7, 30, 10, 0).toZonedDateTime
  private val exchangeMap = Map(
    "NASDAQ" -> TradingExchange(
      id = "NASDAQ",
      name = "NASDAQ",
      openingTime = Some(tradingDateTime.toLocalTime.minus(1, ChronoUnit.HOURS)),
      closingTime = Some(tradingDateTime.toLocalTime.plus(2, ChronoUnit.HOURS)),
      timezone = Some("America/New_York"),
      windowType = BusinessDaysWeek
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = buildFinInstrument(symbol = symbol).map((_,List.empty)).toMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol))
        .thenReturn(Failure(new IllegalStateException("Some Error")))
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = buildFinInstrument(symbol = symbol).map((_,List.empty)).toMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
      val orderFactory = mock[OrderFactory]
      val tradingExecutorService =
        new TradingExecutorServiceImpl(
          marketDataProvider = marketDataProvider,
          marketDataStrategyProvider = marketDataStrategyProvider,
          marketDataStrategyRequestFactory = marketDataStrategyRequestFactory,
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = orderFactory
        )
      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices =
        List(
          StockPrice(
            symbol = symbol,
            open = 1,
            close = 2,
            high = 2,
            low = 1,
            volume = 10,
            snapshotTime = tradingDateTime
          )
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
      val tradingPrice = 2.toDouble
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(tradingPrice))
      val finInstrumentMap = buildFinInstrument(symbol = symbol).map((_,List.empty)).toMap
      val request = TradingExecutorRequest(
        balancePerFinInst = balancePerFinInst,
        finInstrumentMap = finInstrumentMap,
        tradingStrategies = tradingStrategies,
        exchangeMap = exchangeMap,
        strategyConfigurations = strategyConfigurations,
        stopLossPercentage = 10,
        tradingMode = TradingMode.IntraDay,
        tradingDateTime = tradingDateTime
      )
      val buyOrder = Order(
        `type` = OrderType.Buy,
        dateTime = request.tradingDateTime,
        symbol = symbol,
        shares = 500L,
        price = 2.0d,
        tradingStrategyType = TradingStrategyType.MACD,
        positionId = None,
        trigger = SignalTrigger
      )
      when(
        orderFactory.create(
          OrderRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrument = request.finInstrumentMap.keys.head,
            tradingStrategy = request.tradingStrategies.head,
            openPosition = None,
            exchangeMap = request.exchangeMap,
            tradingMode = request.tradingMode,
            stopLossPercentage = request.stopLossPercentage,
            tradingPrice = tradingPrice,
            tradeDateTime = request.tradingDateTime,
            marketDataStrategyResponse = Success(macdMarketDataStrategyResponse)
          )
        )
      ).thenReturn(Some(buyOrder))
      for
        orders <- tradingExecutorService.execute(
          request = request
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
        )
      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices =
        List(
          StockPrice(
            symbol = symbol,
            open = 1,
            close = 2,
            high = 2,
            low = 1,
            volume = 10,
            snapshotTime = ZonedDateTime.now()
          )
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      val offExchangeMap = Map(
        "NASDAQ" -> TradingExchange(
          id = "NASDAQ",
          name = "NASDAQ",
          openingTime = Some(LocalTime.now().minus(2, ChronoUnit.HOURS)),
          closingTime = Some(LocalTime.now().minus(1, ChronoUnit.HOURS)),
          timezone = Some("America/New_York"),
          windowType = BusinessDaysWeek
        )
      )
      val finInstrumentMap = buildFinInstrument(symbol = symbol).map((_,List.empty)).toMap
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = finInstrumentMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = offExchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
        )
      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices =
        List(
          StockPrice(
            symbol = symbol,
            open = 1,
            close = 2,
            high = 2,
            low = 1,
            volume = 10,
            snapshotTime = tradingDateTime.minus(1, ChronoUnit.DAYS)
          )
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      val finInstrumentMap = buildFinInstrument(symbol = symbol).map((_,List.empty)).toMap
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = finInstrumentMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
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
            snapshotTime = ZonedDateTime.now()
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(200.toDouble))
      val finInstrumentMap = buildFinInstrument(symbol = symbol).map((_,List(
        Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = ZonedDateTime.now().minus(1, ChronoUnit.HOURS),
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.MACD
        )
      ))).toMap
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = finInstrumentMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      val finInstrumentMap = buildFinInstrument(symbol = symbol).map((_, List(
        Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = ZonedDateTime.now().minus(1, ChronoUnit.HOURS),
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.MACD
        )
      ))).toMap
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = finInstrumentMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
        )

      val tradingStrategies = List(TradingStrategy(`type` = TradingStrategyType.MACD, pnl = None))
      val macdMarketDataStrategyResponse = MACDMarketDataStrategyResponse(prices =
        List(
          StockPrice(
            symbol = symbol,
            open = 1,
            close = 2,
            high = 2,
            low = 1,
            volume = 10,
            snapshotTime = ZonedDateTime.now()
          )
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      val finInstrumentMap = buildFinInstrument(symbol = symbol).map((_, List(
        Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = ZonedDateTime.now().minus(1, ChronoUnit.HOURS),
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.MACD
        )
      ))).toMap
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = finInstrumentMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(2.toDouble))
      val finInstrumentMap = buildFinInstrument(symbol = symbol).map((_, List(
        Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = ZonedDateTime.now().minus(1, ChronoUnit.HOURS),
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.MACD
        )
      ))).toMap
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = finInstrumentMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
          signalFinderStrategy = signalFinderStrategy,
          orderFactory = OrderFactory(signalFinderStrategy = signalFinderStrategy)
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
      when(marketDataProvider.getCurrentMarketPriceQuote(symbol = symbol)).thenReturn(Success(98.toDouble))
      val finInstrumentMap = buildFinInstrument(symbol = symbol).map((_, List(
        Position(
          id = 1,
          symbol = symbol,
          numberOfShares = 2,
          openPricePerShare = 100,
          closePricePerShare = None,
          openDate = ZonedDateTime.now().minus(1, ChronoUnit.HOURS),
          closeDate = None,
          pnl = None,
          tradingStrategyType = TradingStrategyType.MACD
        )
      ))).toMap
      for
        orders <- tradingExecutorService.execute(
          TradingExecutorRequest(
            balancePerFinInst = balancePerFinInst,
            finInstrumentMap = finInstrumentMap,
            tradingStrategies = tradingStrategies,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = 10,
            tradingMode = TradingMode.IntraDay,
            tradingDateTime = tradingDateTime
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
      creationDate = ZonedDateTime.now(),
      lastUpdate = None,
      isActive = true
    )
  )
