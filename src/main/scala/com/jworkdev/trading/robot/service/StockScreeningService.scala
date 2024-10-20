package com.jworkdev.trading.robot.service

import com.jworkdev.trading.robot.data.ATRCalculator
import com.jworkdev.trading.robot.domain
import com.jworkdev.trading.robot.domain.{FinInstrument, getAverage, groupPricesByDate}
import com.jworkdev.trading.robot.domain.FinInstrumentType.Stock
import com.jworkdev.trading.robot.market.data.SnapshotInterval.{Daily, SixtyMinutes}
import com.jworkdev.trading.robot.market.data.{
  ExchangeDataProvider,
  MarketDataProvider,
  SnapshotInterval,
  StockPrice,
  VolatilityCalculator
}
import com.typesafe.scalalogging.Logger
import zio.{Duration, Task, ZIO, ZLayer}

import java.time.{Instant, ZonedDateTime}
import scala.util.{Failure, Success, Try}

trait StockScreeningService:
  def findAllFinInstrumentFromExchange(exchange: String): Task[List[FinInstrument]]
  def calculateStats(symbols: Set[String]): Task[List[FinInstrumentStats]]

case class FinInstrumentStats(
    symbol: String,
    priceVolatility: Double,
    averageDailyVolume: Double,
    preMarketGap: Option[Double],
    preMarketNumberOfShareTrades: Option[Double],
    averageTrueRange: Option[Double]
)

class StockScreeningServiceImpl(
    private val exchangeDataProvider: ExchangeDataProvider,
    private val marketDataProvider: MarketDataProvider,
    private val parallelismLevel: Int = 3
) extends StockScreeningService:
  private val exchange = "NASDAQ"
  private val finInstrumentType: domain.FinInstrumentType = Stock
  private val logger = Logger(classOf[StockScreeningServiceImpl])

  private def distributeInGroups[T](list: List[T], n: Int): List[List[T]] =
    val groupedList = list.zipWithIndex.groupBy(_._2 % n).values.map(_.map(_._1).toList).toList
    groupedList

  override def findAllFinInstrumentFromExchange(exchange: String): Task[List[FinInstrument]] =
    for
      symbols <- exchangeDataProvider.findAllSymbols(exchange = exchange, finInstrumentType = finInstrumentType)
      finInstruments <- ZIO.succeed(symbols.map(buildFinInstrument))
    yield finInstruments

  private val dailyCount = 14
  private val priceDaysCount = 7

  def calculateStats(symbols: Set[String]): Task[List[FinInstrumentStats]] = for
    batches <- ZIO.succeed(distributeInGroups(list = symbols.toList, parallelismLevel))
    fibers <- ZIO.foreach(batches) { symbolBatch =>
      calculateStatsForBatch(symbols = symbolBatch.toSet).fork
    }
    results <- ZIO.foreach(fibers)(_.join)
  yield results.flatten

  private def calculateStatsForBatch(symbols: Set[String]): Task[List[FinInstrumentStats]] =
    for
      res <- ZIO.foreach(symbols) { symbol =>
        for
          _ <- ZIO.sleep(Duration.fromSeconds(2))
          pricesFiber <- ZIO
            .attempt(
              marketDataProvider
                .getIntradayQuotesDaysRange(symbol = symbol, interval = SixtyMinutes, daysRange = priceDaysCount)
                .fold(
                  ex =>
                    logger.error("Error", ex)
                    None
                  ,
                  prices => Some(prices)
                )
            )
            .fork
          dailyPricesFiber <- ZIO
            .attempt(
              marketDataProvider
                .getIntradayQuotesDaysRange(symbol = symbol, interval = Daily, daysRange = dailyCount)
                .fold(
                  ex =>
                    logger.error("Error", ex)
                    None
                  ,
                  prices => Some(prices)
                )
            )
            .fork
          pricesOpt <- pricesFiber.join
          dailyPricesOpt <- dailyPricesFiber.join
          r <- ZIO.attemptBlocking {
            for
              prices <- pricesOpt
              dailyPrices <- dailyPricesOpt
            yield calculateStats(symbol = symbol, prices = prices, dailyPrices = dailyPrices)
          }
        yield r
      }
    yield res.toList.flatten

  private def calculateStats(
      symbol: String,
      prices: List[StockPrice],
      dailyPrices: List[StockPrice]
  ): FinInstrumentStats =
    val priceMap = groupPricesByDate(prices = prices)
    val dailyAvgMap = priceMap.view.mapValues(prices => getAverage(prices.map(_.volume))).toMap
    val averageDailyVolume = getAverage(dailyAvgMap.values.toList)
    val averageTrueRange = ATRCalculator
      .calculate(prices = dailyPrices, period = dailyCount)
      .fold(
        ex =>
          logger.error("Error", ex)
          None
        ,
        value => Some(value)
      )
    val priceVolatility = VolatilityCalculator.calculateFromPrices(prices = prices)
    FinInstrumentStats(
      symbol = symbol,
      priceVolatility = priceVolatility,
      averageDailyVolume = averageDailyVolume,
      preMarketGap = None,
      preMarketNumberOfShareTrades = None,
      averageTrueRange = averageTrueRange
    )

  private def buildFinInstrument(symbol: String): FinInstrument = FinInstrument(
    symbol = symbol,
    name = "",
    `type` = finInstrumentType,
    priceVolatility = None,
    averageDailyVolume = None,
    preMarketGap = None,
    preMarketNumberOfShareTrades = None,
    averageTrueRange = None,
    exchange = exchange,
    creationDate = ZonedDateTime.now(),
    lastUpdate = None,
    isActive = true
  )

object StockScreeningService:
  val layer: ZLayer[ExchangeDataProvider, Nothing, StockScreeningService] =
    ZLayer {
      for exchangeDataProvider <- ZIO.service[ExchangeDataProvider]
      yield StockScreeningServiceImpl(exchangeDataProvider, MarketDataProvider())
    }
