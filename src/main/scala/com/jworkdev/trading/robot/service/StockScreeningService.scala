package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.domain
import com.jworkdev.trading.robot.domain.FinInstrument
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.market.data.SnapshotInterval.SixtyMinutes
import com.jworkdev.trading.robot.market.data.{ExchangeDataProvider, MarketDataProvider, VolatilityCalculator}
import com.typesafe.scalalogging.Logger
import zio.{Duration, Task, ZIO, ZLayer}

import java.time.Instant
import scala.util.Try

trait StockScreeningService:
  def findAllFinInstrumentFromExchange(exchange: String): Task[List[FinInstrument]]
  def calculateVolatility(finInstruments: List[FinInstrument]): Task[Map[String,Double]]

class StockScreeningServiceImpl(
    private val exchangeDataProvider: ExchangeDataProvider,
    private val marketDataProvider: MarketDataProvider,
    private val parallelismLevel: Int = 3
) extends StockScreeningService:
  private val exchange = "NASDAQ"
  private val finInstrumentType: domain.FinInstrumentType = Stock
  private val logger = Logger(classOf[StockScreeningServiceImpl])

  private def distributeInGroups[T](list: List[T], n: Int): List[List[T]] = {
    val groupedList = list.zipWithIndex.groupBy(_._2 % n).values.map(_.map(_._1).toList).toList
    groupedList
  }

  override def findAllFinInstrumentFromExchange(exchange: String): Task[List[FinInstrument]] =
    for
        symbols <- exchangeDataProvider.
          findAllSymbols(exchange = exchange, finInstrumentType = finInstrumentType)
        finInstruments <- ZIO.succeed(symbols.map(buildFinInstrument))
    yield finInstruments

  def calculateVolatility(finInstruments: List[FinInstrument]): Task[Map[String,Double]] = for
    batches <- ZIO.succeed(distributeInGroups(list = finInstruments, parallelismLevel))
    fibers <- ZIO.foreach(batches) { finInstruments =>
      calculateVolatilityParallel(finInstruments = finInstruments).fork
    }
    results <- ZIO.foreach(fibers)(_.join)
  yield results.flatten.toMap

  private def calculateVolatilityParallel(finInstruments: List[FinInstrument]):Task[Map[String,Double]] =
    for
      res <- ZIO.foreach(finInstruments){finInstrument=>
        for
        _ <- ZIO.sleep(Duration.fromSeconds(2))
        r <- ZIO.attemptBlocking(calculateVolatility(finInstrument = finInstrument))
        yield r
      }
    yield res.flatten.toMap

  private def buildFinInstrument(symbol: String): FinInstrument = FinInstrument(
      symbol = symbol,
      `type` = finInstrumentType,
      volatility = None,
      exchange = exchange,
      creationDate = Instant.now(),
      lastUpdate = None
    )

  private def calculateVolatility(finInstrument: FinInstrument): Option[(String,Double)] =
    calculateVolatility(symbol = finInstrument.symbol).filter(volatility=> !volatility.isNaN).fold(ex=>
      logger.error(s"Error calculating volatility for ${finInstrument.symbol} !",ex)
      None,
      volatility => Some((finInstrument.symbol,volatility))
    )

  private def calculateVolatility(symbol: String): Try[Double] =
    marketDataProvider
      .getIntradayQuotesDaysRange(symbol = symbol, interval = SixtyMinutes, daysRange = 7).
        map(VolatilityCalculator.calculate)

object StockScreeningService:
  val layer: ZLayer[ExchangeDataProvider, Nothing, StockScreeningService] =
    ZLayer {
      for exchangeDataProvider <- ZIO.service[ExchangeDataProvider]
      yield StockScreeningServiceImpl(exchangeDataProvider, MarketDataProvider())
    }
