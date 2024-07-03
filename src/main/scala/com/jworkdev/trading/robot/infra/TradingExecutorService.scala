package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.config.StrategyConfigurations
import com.jworkdev.trading.robot.market.data.MarketDataProvider
import com.jworkdev.trading.robot.market.data.SnapshotInterval.FiveMinutes
import com.jworkdev.trading.robot.data.signals.{MovingAverageRequest, SignalFinderStrategy, SignalType}
import com.jworkdev.trading.robot.data.strategy.{MarketDataStrategyProvider, MarketDataStrategyRequest}
import com.jworkdev.trading.robot.data.strategy.macd.MACDMarketDataStrategyRequest
import com.jworkdev.trading.robot.domain.{FinInstrumentConfig, Position, TradingStrategyType}
import com.typesafe.scalalogging.Logger
import zio.{Console, Task, ZIO}

import java.time.Instant
import scala.util.{Failure, Success, Try}

trait TradingExecutorService:
  def execute(
      balancePerFinInst: Double,
      finInstrumentConfigs: List[FinInstrumentConfig],
      openPositions: List[Position],
      strategyConfigurations: StrategyConfigurations
  ): Task[List[Order]]

class TradingExecutorServiceImpl(
    marketDataProvider: MarketDataProvider
) extends TradingExecutorService:
  private val logger = Logger(classOf[TradingExecutorServiceImpl])

  private def createMarketDataStrategyRequest(
      symbol: String,
      tradingStrategyType: TradingStrategyType,
      strategyConfigurations: StrategyConfigurations
  ): Try[MarketDataStrategyRequest] =
    tradingStrategyType match
      case TradingStrategyType.OpenGap =>
        Failure(new IllegalStateException("No OpenGap configuration found!"))
      case TradingStrategyType.MACD =>
        strategyConfigurations.macd match
          case Some(macdCfg) =>
            Success(MACDMarketDataStrategyRequest(symbol = symbol, snapshotInterval = macdCfg.snapshotInterval))
          case None => Failure(new IllegalStateException("No MACD configuration found!"))

  private def execute(
      balancePerFinInst: Double,
      finInstrumentConfig: FinInstrumentConfig,
      openPositions: List[Position],
      strategyConfigurations: StrategyConfigurations
  ): Task[Option[Order]] =
    logger.info(s"Trading on  $finInstrumentConfig")
    val symbolOpenPosition = openPositions.find(position => finInstrumentConfig.symbol == position.symbol)
    val res = createMarketDataStrategyRequest(
      symbol = finInstrumentConfig.symbol,
      tradingStrategyType = finInstrumentConfig.strategy,
      strategyConfigurations = strategyConfigurations
    ).flatMap(request => MarketDataStrategyProvider.provide(request)) match
      case Failure(exception) =>
        logger.error("Error", exception)
        None
      case Success(marketDataResponse) =>
        val signals =
          SignalFinderStrategy.findSignals(signalFinderRequest = marketDataResponse.buildSignalFinderRequest())
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
                      symbol = finInstrumentConfig.symbol,
                      dateTime = Instant.now(),
                      shares = position.numberOfShares,
                      price = orderPrice,
                      positionId = Some(position.id)
                    )
                  logger.info(s"Creating Sell Order: $order")
                  Some(order)
                else
                  logger.info(s"No Sell Signal")
                  None
              case None =>
                // Trying to make a Buy
                if lastSignal.`type` == SignalType.Buy then
                  val numberOfShares = (balancePerFinInst / orderPrice).toLong
                  val order =
                    Order(
                      `type` = Buy,
                      symbol = finInstrumentConfig.symbol,
                      dateTime = Instant.now(),
                      shares = numberOfShares,
                      price = orderPrice
                    )
                  logger.info(s"Creating Buy Order: $order")
                  Some(order)
                else
                  logger.info(s"No Buy Signal")
                  None
          case None =>
            logger.info(s"No Last Signal found!")
            None
    ZIO.succeed(res)

  override def execute(
      balancePerFinInst: Double,
      finInstrumentConfigs: List[FinInstrumentConfig],
      openPositions: List[Position],
      strategyConfigurations: StrategyConfigurations
  ): Task[List[Order]] = for
    _ <- Console.printLine(s"Trading on  $finInstrumentConfigs")
    fibers <- ZIO.foreach(finInstrumentConfigs)(finInstrumentConfig =>
      execute(
        balancePerFinInst = balancePerFinInst,
        finInstrumentConfig = finInstrumentConfig,
        openPositions = openPositions,
        strategyConfigurations = strategyConfigurations
      ).fork
    )
    results <- ZIO.foreach(fibers)(_.join)
  yield (results.flatten)

object TradingExecutorService:
  def apply(): TradingExecutorService = new TradingExecutorServiceImpl(
    MarketDataProvider()
  )
