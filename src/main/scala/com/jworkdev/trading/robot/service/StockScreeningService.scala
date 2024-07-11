package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.domain
import com.jworkdev.trading.robot.domain.FinInstrument
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.market.data.SnapshotInterval.SixtyMinutes
import com.jworkdev.trading.robot.market.data.{ExchangeDataProvider, MarketDataProvider, VolatilityCalculator}
import zio.{Task, ZIO, ZLayer}

import java.time.Instant
import scala.util.Try

trait StockScreeningService:
  def screenFinInstruments(): Task[List[FinInstrument]]

class StockScreeningServiceImpl(
    private val exchangeDataProvider: ExchangeDataProvider,
    private val marketDataProvider: MarketDataProvider,
    private val parallelismLevel: Int = 5
) extends StockScreeningService:
  private val exchange = "NASDAQ"
  private val finInstrumentType: domain.FinInstrumentType = Stock

  private def distributeInGroups[T](list: List[T], n: Int): List[List[T]] = {
    val groupedList = list.zipWithIndex.groupBy(_._2 % n).values.map(_.map(_._1).toList).toList
    groupedList
  }

  override def screenFinInstruments(): Task[List[FinInstrument]] = for
    symbols <- exchangeDataProvider.
      findAllSymbols(exchange = exchange, finInstrumentType = finInstrumentType)
    batches <- ZIO.succeed(distributeInGroups(list = symbols, parallelismLevel))
    fibers <- ZIO.foreach(batches) { symbols =>
      buildFinInstruments(symbols = symbols).fork
    }
    results <- ZIO.foreach(fibers)(_.join)
  yield results.flatten

  private def buildFinInstruments(symbols: List[String]):Task[List[FinInstrument]] =
    ZIO.attemptBlocking(symbols.flatMap(buildFinInstrument))

  private def buildFinInstrument(symbol: String): Option[FinInstrument] =
    calculateVolatility(symbol = symbol).map(volatility =>
      FinInstrument(
        symbol = symbol,
        `type` = finInstrumentType,
        volatility = volatility,
        exchange = exchange,
        creationDate = Instant.now()
      )
    ).toOption

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
