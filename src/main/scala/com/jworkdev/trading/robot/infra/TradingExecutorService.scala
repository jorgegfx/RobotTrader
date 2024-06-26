package com.jworkdev.trading.robot.infra

import com.jworkdev.trading.robot.Order
import com.jworkdev.trading.robot.OrderType.{Buy, Sell}
import com.jworkdev.trading.robot.data.FinancialIInstrumentDataProvider
import com.jworkdev.trading.robot.data.StockQuoteInterval.FiveMinutes
import com.jworkdev.trading.robot.data.signals.{
  MovingAverageRequest,
  SignalFinderStrategy,
  SignalType
}
import com.jworkdev.trading.robot.domain.{FinInstrumentConfig, Position}
import com.typesafe.scalalogging.Logger
import zio.{Task, ZIO}

import java.time.Instant
import scala.util.{Failure, Success}

trait TradingExecutorService:
  def execute(
      balancePerFinInst: Double,
      finInstrumentConfigs: List[FinInstrumentConfig],
      openPositions: List[Position]
  ): Task[List[Order]]

class TradingExecutorServiceImpl(
    financialIInstrumentDataProvider: FinancialIInstrumentDataProvider
) extends TradingExecutorService:
  private val logger = Logger(classOf[TradingExecutorServiceImpl])
  private def execute(
      balancePerFinInst: Double,
      finInstrumentConfig: FinInstrumentConfig,
      openPositions: List[Position]
  ): Option[Order] =
    logger.info(s"Trading on  $finInstrumentConfig")
    val symbolOpenPosition = openPositions.find(position =>
      finInstrumentConfig.symbol == position.symbol
    )
    financialIInstrumentDataProvider.getIntradayQuotes(
      symbol = finInstrumentConfig.symbol,
      FiveMinutes
    ) match
      case Failure(exception) =>
        logger.error("Error", exception)
        None
      case Success(stockPrices) =>
        val signals = SignalFinderStrategy.findSignals(signalFinderRequest =
          MovingAverageRequest(stockPrices = stockPrices)
        )
        signals.lastOption match
          case Some(lastSignal) =>
            val orderPrice =
              stockPrices.find(_.snapshotTime == lastSignal.date).head.close
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
                else None
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
                else None
          case None => None

  override def execute(
      balancePerFinInst: Double,
      finInstrumentConfigs: List[FinInstrumentConfig],
      openPositions: List[Position]
  ): Task[List[Order]] = for
    fibers <- ZIO.foreach(finInstrumentConfigs)(n =>
      execute(
        balancePerFinInst = balancePerFinInst,
        finInstrumentConfigs = finInstrumentConfigs,
        openPositions = openPositions
      ).fork
    )
    results <- ZIO.foreach(fibers)(_.join)
  yield (results.flatten)

object TradingExecutorService:
  def apply(): TradingExecutorService = new TradingExecutorServiceImpl(
    FinancialIInstrumentDataProvider()
  )
