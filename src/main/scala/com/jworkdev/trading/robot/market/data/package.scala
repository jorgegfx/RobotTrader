package com.jworkdev.trading.robot.market

import com.jworkdev.trading.robot.domain.FinInstrumentType
import com.jworkdev.trading.robot.market.data.alphavantage.AlphaVantageExchangeDataProvider
import com.jworkdev.trading.robot.market.data.yahoo.YahooFinanceMarketDataProvider
import zio.{Task, ULayer, ZLayer}

import java.time.ZonedDateTime
import scala.math.*
import scala.util.Try

package object data:
  case class StockPrice(
      symbol: String,
      open: Double,
      close: Double,
      high: Double,
      low: Double,
      volume: Long,
      snapshotTime: ZonedDateTime
  )

  enum SnapshotInterval:
    case OneMinute, FiveMinutes, FifteenMinutes, ThirtyMinutes, SixtyMinutes, Hourly, Daily

  enum SnapshotFrequency:
    case Daily, Weekly, Monthly

  trait MarketDataProvider:
    def getCurrentMarketPriceQuote(symbol: String): Try[Double]

    def getIntradayQuotes(
        symbol: String,
        interval: SnapshotInterval
    ): Try[List[StockPrice]]

    def getIntradayQuotesDaysRange(
        symbol: String,
        interval: SnapshotInterval,
        daysRange: Int
    ): Try[List[StockPrice]]

    def getQuotes(
        symbol: String,
        frequency: SnapshotFrequency
    ): Try[List[StockPrice]]

  trait ExchangeDataProvider:
    def findAllSymbols(exchange: String, finInstrumentType: FinInstrumentType): Task[List[String]]

  object MarketDataProvider:
    def apply(): MarketDataProvider =
      new YahooFinanceMarketDataProvider()

  object ExchangeDataProvider:
    val layer: ULayer[ExchangeDataProvider] = ZLayer.succeed(new AlphaVantageExchangeDataProvider)

  object VolatilityCalculator:
    private def calculateReturns(values: Seq[Double]): Seq[Double] =
      values.sliding(2).flatMap { case Seq(yesterday, today) =>
        if(!today.isNaN && !yesterday.isNaN && yesterday != 0)
          Some(log(today / yesterday))
        else None
      }.toSeq

    def calculateFromPrices(prices: List[StockPrice]): Double =
      calculate(values = prices.map(_.close))

    def calculate(values: List[Double]): Double =
      if(values.isEmpty)
        0.0D
      else
        val returns = calculateReturns(values = values)
        val meanReturn = returns.sum / returns.size
        val variance = returns.map(r => pow(r - meanReturn, 2)).sum / (returns.size - 1)
        sqrt(variance)
