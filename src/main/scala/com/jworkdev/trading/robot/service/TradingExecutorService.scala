package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.StrategyConfigurations
import com.jworkdev.trading.robot.data.signals.{SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy
import com.jworkdev.trading.robot.data.strategy.{
  MarketDataStrategyProvider,
  MarketDataStrategyRequest,
  MarketDataStrategyRequestFactory,
  MarketDataStrategyResponse
}
import com.jworkdev.trading.robot.domain.{FinInstrument, Position, TradingExchange, TradingStrategy}
import com.jworkdev.trading.robot.market.data.MarketDataProvider
import com.typesafe.scalalogging.Logger
import zio.{Console, Task, ZIO}

import java.time.Instant
import scala.util.{Failure, Success, Try}
import com.jworkdev.trading.robot.time.InstantExtensions.isToday

case class TradingExecutorRequest(
    balancePerFinInst: Double,
    finInstruments: List[FinInstrument],
    tradingStrategies: List[TradingStrategy],
    openPositions: List[Position],
    exchangeMap: Map[String, TradingExchange],
    strategyConfigurations: StrategyConfigurations,
    stopLossPercentage: Int
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
      _ <- Console.printLine(
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
          stopLossPercentage = request.stopLossPercentage
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
      stopLossPercentage: Int
  ): Task[Option[Order]] =
    val orders = for
      _ <- Console.printLine(s"Executing ${finInstrument.symbol} ...")
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
      _ <- Console.printLine(s"Data for ${finInstrument.symbol} fetched!")
      orders <- currentPriceRes.fold(
        ex =>
          logger.error("Error fetching current price!",ex)
          ZIO.succeed(None),
        currentPrice =>
          execute(
            balancePerFinInst = balancePerFinInst,
            finInstrument = finInstrument,
            tradingStrategy = tradingStrategy,
            openPositions = openPositions,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations,
            stopLossPercentage = stopLossPercentage,
            currentPrice = currentPrice,
            marketDataStrategyResponse = marketDataStrategyResponse
          )
      )
    yield orders
    orders

  private def execute(
      balancePerFinInst: Double,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      openPositions: List[Position],
      exchangeMap: Map[String, TradingExchange],
      strategyConfigurations: StrategyConfigurations,
      stopLossPercentage: Int,
      currentPrice: Double,
      marketDataStrategyResponse: Try[MarketDataStrategyResponse]
  ): Task[Option[Order]] =
    logger.info(s"Trading on  $finInstrument")
    val symbolOpenPosition = openPositions.find(position =>
      finInstrument.symbol == position.symbol &&
        tradingStrategy.`type` == position.tradingStrategyType
    )
    val res = marketDataStrategyResponse match
      case Failure(exception) =>
        logger.error("Error getting strategy market data ...", exception)
        symbolOpenPosition.flatMap(position =>
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
            symbolOpenPosition match
              case Some(position) =>
                // Trying to make a Sell
                if lastSignal.`type` == SignalType.Sell then
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
              case None =>
                // Trying to make a Buy
                if lastSignal.`type` == SignalType.Buy then
                  if lastSignal.date.isToday() then
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
                    logger.info(s"Last Buy signal '${lastSignal.date}' not from today")
                    None
                else
                  logger.info(s"No Buy Signal")
                  None
          case None =>
            logger.info(s"No Last Signal found!")
            None
    ZIO.succeed(res)

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
