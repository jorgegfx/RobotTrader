package com.jworkdev.trading.robot

import com.jworkdev.trading.robot.OrderType.Buy
import com.jworkdev.trading.robot.config.TradingMode.IntraDay
import com.jworkdev.trading.robot.config.{MACDStrategyConfiguration, OpenGapStrategyConfiguration, StrategyConfigurations}
import com.jworkdev.trading.robot.data.signals.SignalFinderStrategy
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequestFactory}
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.domain.TradingExchangeWindowType.BusinessDaysWeek
import com.jworkdev.trading.robot.domain.TradingStrategyType.OpenGap
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.market.data.SnapshotInterval.OneMinute
import com.jworkdev.trading.robot.pnl.{MarketDataEntry, PnLAnalysis, PnLAnalyzer, PnLMarketDataProvider}
import com.jworkdev.trading.robot.service.{OrderFactory, OrderRequest}

import java.time.{Instant, LocalTime}
import java.util
object PnLAnalyzerApp extends App:
  val initialCash = 1000.0
  var currentCash = initialCash
  private val symbol = "BFI"
  private val sampleCount = 15
  val cfg = StrategyConfigurations(
    macd = Some(MACDStrategyConfiguration(snapshotInterval = OneMinute)),
    openGap = Some(OpenGapStrategyConfiguration(signalCount = sampleCount))
  )
  val tests = List((symbol, OpenGap))
  private val pnLAnalyzer = PnLAnalyzer()
  private val marketDataStrategyRequestFactory = MarketDataStrategyRequestFactory()
  private val marketDataStrategyProvider = MarketDataStrategyProvider()
  private val pnLMarketDataProvider = PnLMarketDataProvider()
  private val signalFinderStrategy = SignalFinderStrategy()
  private val orderFactory = OrderFactory(signalFinderStrategy = SignalFinderStrategy())
  private val stopLossPercentage = 10
  private val positionStack: util.Deque[Position] = new util.LinkedList()
  private var orderCount = 1
  private val exchangeName = "NASDAQ"
  private val exchange = TradingExchange(id = exchangeName,
    name = exchangeName,
    windowType = BusinessDaysWeek,
    openingTime = Some(LocalTime.of(9, 0)),
    closingTime = Some(LocalTime.of(16, 0)),
    timezone = Some("America/New_York"))
  private val finInstrument = FinInstrument(symbol = symbol,
    name = "",
    `type` = Stock,
    volatility = Some(1.0),
    exchange = exchangeName,
    creationDate = Instant.now(),
    lastUpdate = None,
    isActive = true)


  tests.foreach { case (symbol: String, tradingStrategyType: TradingStrategyType) =>
    val entries =
      pnLMarketDataProvider.provide(symbol = symbol, daysCount = sampleCount, tradingStrategyType = tradingStrategyType)
    val orders = executeStrategy(tradingStrategyType = tradingStrategyType, entries = entries)
    println("Orders Created ...")
    orders.foreach(println)
  }


  private def createPosition(order: Order, lastPosition: Option[Position]): Position =
    if(order.`type` == OrderType.Buy)
      Position(id = orderCount,
        symbol = symbol,
        numberOfShares = order.shares,
        openPricePerShare = order.price,
        closePricePerShare = None,
        openDate = order.dateTime,
        closeDate = None,
        pnl = None,
        tradingStrategyType = order.tradingStrategyType)
    else
      lastPosition.map(position=>Position(id = orderCount,
        symbol = symbol,
        numberOfShares = order.shares,
        openPricePerShare = position.openPricePerShare,
        closePricePerShare = Some(order.price),
        openDate = position.openDate,
        closeDate = Some(order.dateTime),
        pnl = Some((order.price - position.openPricePerShare) * order.shares),
        tradingStrategyType = position.tradingStrategyType)).get

  private def executeStrategy(entries: List[MarketDataEntry],tradingStrategyType: TradingStrategyType): List[Order] =
    entries.flatMap(entry => {
      val currentPosition = if (positionStack.isEmpty) None else Some(positionStack.pop())
      val newOrder = orderFactory.create(orderRequest = OrderRequest(balancePerFinInst = currentCash,
        finInstrument = finInstrument,
        tradingStrategy = TradingStrategy(`type` = tradingStrategyType, pnl = None),
        openPosition = currentPosition,
        exchangeMap = Map(exchangeName -> exchange),
        strategyConfigurations = cfg,
        tradingMode = IntraDay,
        stopLossPercentage = stopLossPercentage,
        tradingPrice = entry.tradingPrice,
        tradingTime = entry.tradingTime,
        marketDataStrategyResponse = entry.marketDataStrategyResponse))
      orderCount = orderCount + 1
      newOrder.map(order => createPosition(order = order, lastPosition = currentPosition))
        .filter(_.closeDate.isEmpty)
        .foreach(positionStack.push)
      newOrder.foreach(order=>{
        if(order.`type` == Buy){
          currentCash = currentCash - order.totalPrice
        }else{
          currentCash = currentCash + order.totalPrice
        }
      })
      newOrder
    })

  private def printPnlAnalysis(pnLAnalysis: PnLAnalysis): Unit =
    println(s"PNL: ${pnLAnalysis.pnl}")
    pnLAnalysis.orders
      .map { order =>
        s"${order.`type`},${order.symbol},${order.dateTime},${order.shares},${order.price}"
      }
      .foreach(println)
