package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.StrategyConfigurations
import com.jworkdev.trading.robot.data.signals.{SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest, MarketDataStrategyRequestFactory, MarketDataStrategyResponse}
import com.jworkdev.trading.robot.domain.{FinInstrument, Position, TradingExchange, TradingStrategy}
import com.typesafe.scalalogging.Logger
import zio.{Console, Task, ZIO}

import java.time.Instant
import scala.util.{Failure, Success}
import com.jworkdev.trading.robot.time.InstantExtensions.isToday

trait TradingExecutorService:
  def execute(
      balancePerFinInst: Double,
      finInstruments: List[FinInstrument],
      tradingStrategies: List[TradingStrategy],
      openPositions: List[Position],
      exchangeMap : Map[String, TradingExchange],
      strategyConfigurations: StrategyConfigurations
  ): Task[List[Order]]

class TradingExecutorServiceImpl(
    marketDataStrategyProvider: MarketDataStrategyProvider[MarketDataStrategyRequest, MarketDataStrategyResponse],
    marketDataStrategyRequestFactory: MarketDataStrategyRequestFactory,
    signalFinderStrategy: SignalFinderStrategy
) extends TradingExecutorService:
  private val logger = Logger(classOf[TradingExecutorServiceImpl])

  override def execute(
      balancePerFinInst: Double,
      finInstruments: List[FinInstrument],
      tradingStrategies: List[TradingStrategy],
      openPositions: List[Position],
      exchangeMap : Map[String, TradingExchange],
      strategyConfigurations: StrategyConfigurations
  ): Task[List[Order]] =
    val input = tradingStrategies.flatMap(tradingStrategy=>finInstruments.
        map(finInstrument=>(tradingStrategy,finInstrument)))
    for
      _ <- Console.printLine(s"Trading on  ${finInstruments.map(_.symbol)} using $tradingStrategies")
      fibers <- ZIO.foreach(input) {
        case (tradingStrategy: TradingStrategy, finInstrument: FinInstrument) =>
          execute(
            balancePerFinInst = balancePerFinInst,
            finInstrument = finInstrument,
            tradingStrategy = tradingStrategy,
            openPositions = openPositions,
            exchangeMap = exchangeMap,
            strategyConfigurations = strategyConfigurations
          ).fork
      }
      results <- ZIO.foreach(fibers)(_.join)
    yield results.flatten

  private def execute(
      balancePerFinInst: Double,
      finInstrument: FinInstrument,
      tradingStrategy: TradingStrategy,
      openPositions: List[Position],
      exchangeMap : Map[String, TradingExchange],
      strategyConfigurations: StrategyConfigurations
  ): Task[Option[Order]] =
    logger.info(s"Trading on  $finInstrument")
    val symbolOpenPosition = openPositions.find(position =>
      finInstrument.symbol == position.symbol &&
        tradingStrategy.`type` == position.tradingStrategyType
    )
    val res = marketDataStrategyRequestFactory
      .createMarketDataStrategyRequest(
        symbol = finInstrument.symbol,
        tradingStrategyType = tradingStrategy.`type`,
        strategyConfigurations = strategyConfigurations
      )
      .flatMap(request => marketDataStrategyProvider.provide(request)) match
      case Failure(exception) =>
        logger.error("Error", exception)
        None
      case Success(marketDataResponse) =>
        val signals =
          signalFinderStrategy.findSignals(signalFinderRequest = marketDataResponse.buildSignalFinderRequest())
        signals.lastOption match
          case Some(lastSignal) =>
            logger.info(s"Last Signal: $lastSignal")
            val orderPrice = lastSignal.stockPrice.close
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
                      price = orderPrice,
                      positionId = Some(position.id),
                      tradingStrategyType = tradingStrategy.`type`
                    )
                  logger.info(s"Creating Sell Order: $order")
                  Some(order)
                else
                  logger.info(s"No Sell Signal")
                  None
              case None =>
                // Trying to make a Buy
                if lastSignal.`type` == SignalType.Buy then
                  if lastSignal.date.isToday() then
                    val numberOfShares = (balancePerFinInst / orderPrice).toLong
                    val order =
                      Order(
                        `type` = Buy,
                        symbol = finInstrument.symbol,
                        dateTime = Instant.now(),
                        shares = numberOfShares,
                        price = orderPrice,
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

object TradingExecutorService:
  def apply(): TradingExecutorService = new TradingExecutorServiceImpl(
    MarketDataStrategyProvider(),
    MarketDataStrategyRequestFactory(),
    SignalFinderStrategy()
  )
