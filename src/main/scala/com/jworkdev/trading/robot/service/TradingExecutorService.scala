package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.{StrategyConfigurations, TradingMode}
import com.jworkdev.trading.robot.data.signals.{Signal, SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.*
import com.jworkdev.trading.robot.market.data.MarketDataProvider
import com.typesafe.scalalogging.Logger
import zio.{Task, ZIO}

import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime}
import scala.util.{Failure, Success, Try}

case class TradingExecutorRequest(
    balancePerFinInst: Double,
    finInstruments: List[FinInstrument],
    tradingStrategies: List[TradingStrategy],
    openPositions: List[Position],
    exchangeMap: Map[String, TradingExchange],
    strategyConfigurations: StrategyConfigurations,
    stopLossPercentage: Int,
    tradingMode: TradingMode
)

trait TradingExecutorService:
  def execute(
      request: TradingExecutorRequest
  ): Task[List[Order]]

class TradingExecutorServiceImpl(
    marketDataProvider: MarketDataProvider,
    marketDataStrategyProvider: MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse],
    marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory,
    signalFinderStrategy: SignalFinderStrategy
) extends TradingExecutorService:
  private val logger = Logger(classOf[TradingExecutorServiceImpl])


  override def execute(
      request: TradingExecutorRequest
  ): Task[List[Order]] =
    val input = request.tradingStrategies.flatMap(tradingStrategy =>
      request.finInstruments.map(finInstrument => (tradingStrategy, finInstrument))
    )
    for
      _ <- ZIO.logInfo(
        s"Trading on  ${request.finInstruments.map(_.symbol)} using ${request.tradingStrategies}"
      )
      fibers <- ZIO.foreach(input) { case (tradingStrategy: TradingStrategy, finInstrument: FinInstrument) =>
        execute(
          balancePerFinInst = request.balancePerFinInst,
          finInstrument = finInstrument,
          tradingStrategy = tradingStrategy,
          openPositions = request.openPositions,
          exchangeMap = request.exchangeMap,
          strategyConfigurations = request.strategyConfigurations,
          stopLossPercentage = request.stopLossPercentage,
          tradingMode = request.tradingMode
        ).fork
      }
      results <- ZIO.foreach(fibers)(_.join)
    yield results.flatten

  private def execute(
      balancePerFinInst: Double,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      openPositions: List[Position],
      exchangeMap: Map[String, TradingExchange],
      strategyConfigurations: StrategyConfigurations,
      stopLossPercentage: Int,
      tradingMode: TradingMode
  ): Task[Option[Order]] =
    for
      _ <- ZIO.logInfo(s"Executing ${finInstrument.symbol} ...")
      currentPriceFiber <- ZIO.attempt(marketDataProvider.getCurrentQuote(symbol = finInstrument.symbol)).fork
      marketDataStrategyResponseFiber <- ZIO
        .attempt(
          marketDataStrategyRequestFactory
            .createMarketDataStrategyRequest(
              symbol = finInstrument.symbol,
              tradingStrategyType = tradingStrategy.`type`,
              strategyConfigurations = strategyConfigurations
            )
            .flatMap(request => marketDataStrategyProvider.provide(request))
        )
        .fork
      marketDataStrategyResponse <- marketDataStrategyResponseFiber.join
      currentPriceRes <- currentPriceFiber.join
      _ <- ZIO.logInfo(s"Data for ${finInstrument.symbol} fetched!")
      orders <- currentPriceRes.fold(
        ex =>
          logger.error("Error fetching current price!", ex)
          ZIO.succeed(None)
        ,
        currentPrice =>
          execute(
            balancePerFinInst = balancePerFinInst,
            finInstrument = finInstrument,
            tradingStrategy = tradingStrategy,
            openPositions = openPositions,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            tradingMode = tradingMode,
            stopLossPercentage = stopLossPercentage,
            currentPrice = currentPrice,
            marketDataStrategyResponse = marketDataStrategyResponse
          )
      )
    yield orders

  private def execute(
      balancePerFinInst: Double,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      openPositions: List[Position],
      exchangeMap: Map[String, TradingExchange],
      strategyConfigurations: StrategyConfigurations,
      tradingMode: TradingMode,
      stopLossPercentage: Int,
      currentPrice: Double,
      marketDataStrategyResponse: Try[MarketDataStrategyResponse]
  ): Task[Option[Order]] =
    logger.info(s"Trading on  $finInstrument")
    val openPosition = openPositions.find(position =>
      finInstrument.symbol == position.symbol &&
        tradingStrategy.`type` == position.tradingStrategyType
    )
    val res = marketDataStrategyResponse match
      case Failure(exception) =>
        logger.error("Error getting strategy market data!", exception)
        openPosition.flatMap(position =>
          executeStopLoss(
            finInstrument = finInstrument,
            position = position,
            currentPrice = currentPrice,
            stopLossPercentage = stopLossPercentage,
            tradingStrategy = tradingStrategy
          )
        )
      case Success(marketDataResponse) =>
        val signals =
          signalFinderStrategy.findSignals(signalFinderRequest = marketDataResponse.buildSignalFinderRequest())
        signals.lastOption match
          case Some(lastSignal) =>
            logger.info(s"Last Signal: $lastSignal")
            openPosition match
              case Some(position) =>
                // Trying to make a Sell
                executeSellSignal(
                  signal = lastSignal,
                  finInstrument = finInstrument,
                  tradingStrategy = tradingStrategy,
                  position = position,
                  stopLossPercentage = stopLossPercentage,
                  currentPrice = currentPrice
                )
              case None =>
                // Trying to make a Buy
                executeBuySignal(
                  signal = lastSignal,
                  finInstrument = finInstrument,
                  tradingMode = tradingMode,
                  tradingStrategy = tradingStrategy,
                  balancePerFinInst = balancePerFinInst,
                  currentPrice = currentPrice,
                  exchangeMap = exchangeMap
                )
          case None =>
            logger.info(s"No Last Signal found!")
            openPosition.flatMap(position =>
              executeStopLoss(
                finInstrument = finInstrument,
                position = position,
                currentPrice = currentPrice,
                stopLossPercentage = stopLossPercentage,
                tradingStrategy = tradingStrategy
              )
            )
    ZIO.succeed(res)

  private def executeBuySignal(
      signal: Signal,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      exchangeMap: Map[String, TradingExchange],
      tradingMode: TradingMode,
      balancePerFinInst: Double,
      currentPrice: Double
  ): Option[Order] =
    if signal.`type` == SignalType.Buy then
      if TradingWindowValidator.isNotOutOfBuyingWindow(currentLocalTime = LocalDateTime.now(),
          tradingMode = tradingMode,
          finInstrument = finInstrument,
          tradingExchangeMap = exchangeMap) then
        val numberOfShares = (balancePerFinInst / currentPrice).toLong
        val order =
          Order(
            `type` = Buy,
            symbol = finInstrument.symbol,
            dateTime = Instant.now(),
            shares = numberOfShares,
            price = currentPrice,
            tradingStrategyType = tradingStrategy.`type`
          )
        logger.info(s"Creating Buy Order: $order")
        Some(order)
      else
        logger.info(s"Is closing IntraDay signal = $signal, " +
          s"tradingMode = $tradingMode, " +
          s"finInstrument = $finInstrument, tradingExchangeMap = $exchangeMap")
        None
    else
      logger.info(s"No Buy Signal")
      None

  private def executeSellSignal(
      signal: Signal,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      position: Position,
      stopLossPercentage: Int,
      currentPrice: Double
  ): Option[Order] =
    if signal.`type` == SignalType.Sell then
      val order =
        Order(
          `type` = Sell,
          symbol = finInstrument.symbol,
          dateTime = Instant.now(),
          shares = position.numberOfShares,
          price = currentPrice,
          positionId = Some(position.id),
          tradingStrategyType = tradingStrategy.`type`
        )
      logger.info(s"Creating Sell Order: $order")
      Some(order)
    else
      logger.info(s"No Sell Signal")
      executeStopLoss(
        finInstrument = finInstrument,
        position = position,
        currentPrice = currentPrice,
        stopLossPercentage = stopLossPercentage,
        tradingStrategy = tradingStrategy
      )

  private def executeStopLoss(
      finInstrument: FinInstrument,
      position: Position,
      currentPrice: Double,
      stopLossPercentage: Int,
      tradingStrategy: TradingStrategy
  ): Option[Order] =
    if position.shouldExitForStopLoss(
        currentPricePerShare = currentPrice,
        stopLossPercentage = stopLossPercentage
      )
    then
      logger.info(s"Stop Loss for Position : ${position.id}!")
      Some(
        Order(
          `type` = Sell,
          symbol = finInstrument.symbol,
          dateTime = Instant.now(),
          shares = position.numberOfShares,
          price = currentPrice,
          positionId = Some(position.id),
          tradingStrategyType = tradingStrategy.`type`
        )
      )
    else None

object TradingExecutorService:
  def apply(): TradingExecutorService = new TradingExecutorServiceImpl(
    MarketDataProvider(),
    MarketDataStrategyProvider(),
    MarketDataStrategyRequestFactory(),
    SignalFinderStrategy()
  )

object TradingWindowValidator:
  private val limitHoursBeforeCloseDay = 1

  private def isNotOutOfBuyingWindow(currentLocalTime: LocalDateTime,
                                     exchange: TradingExchange): Boolean =
    val res = for
      limitClosingTime <- exchange.currentCloseWindow(currentDateTime = currentLocalTime).
        map(_.minus(limitHoursBeforeCloseDay, ChronoUnit.HOURS))
      currentOpenWindow <- exchange.currentOpenWindow(currentDateTime = currentLocalTime)
    yield currentLocalTime.isBefore(limitClosingTime) &&
      currentLocalTime.isAfter(currentOpenWindow)
    res.getOrElse(false) && exchange.isTradingExchangeDay(currentLocalTime = currentLocalTime)

  private def isNotOutOfBuyingWindow(currentLocalTime: LocalDateTime,
                                     finInstrument: FinInstrument,
                                     tradingExchangeMap: Map[String, TradingExchange]): Boolean =
    tradingExchangeMap.get(finInstrument.exchange).exists(exchange => {
      exchange.windowType == TradingExchangeWindowType.Always ||
        isNotOutOfBuyingWindow(currentLocalTime = currentLocalTime, exchange = exchange)
    })

  def isNotOutOfBuyingWindow(currentLocalTime: LocalDateTime,
                             tradingMode: TradingMode,
                             finInstrument: FinInstrument,
                             tradingExchangeMap: Map[String, TradingExchange]): Boolean =
    tradingMode == TradingMode.IntraDay &&
      isNotOutOfBuyingWindow(currentLocalTime=currentLocalTime,
        finInstrument = finInstrument,
        tradingExchangeMap = tradingExchangeMap)


