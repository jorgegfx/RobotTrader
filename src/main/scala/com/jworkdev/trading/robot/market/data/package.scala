package com.jworkdev.trading.robot.market

import com.jworkdev.trading.robot.domain.FinInstrumentType
import com.jworkdev.trading.robot.market.data.alphavantage.AlphaVantageExchangeDataProvider
import com.jworkdev.trading.robot.market.data.yahoo.YahooFinanceMarketDataProvider
import zio.{Task, ULayer, ZLayer}
import scala.math._
import java.time.Instant
import scala.util.Try

package object data:
  case class StockPrice(
      symbol: String,
      open: Double,
      close: Double,
      high: Double,
      low: Double,
      volume: Long,
      snapshotTime: Instant
  ) {}

  enum SnapshotInterval:
    case OneMinute, FiveMinutes, FifteenMinutes, ThirtyMinutes, SixtyMinutes

  enum SnapshotFrequency:
    case Daily, Weekly, Monthly

  trait MarketDataProvider:
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
    private def calculateReturns(prices: Seq[Double]): Seq[Double] =
      prices.sliding(2).flatMap { case Seq(yesterday, today) =>
        if(!today.isNaN && !yesterday.isNaN && yesterday != 0)
          Some((today - yesterday) / yesterday)
        else None
      }.toSeq

    def calculate(prices: List[StockPrice]): Double =
      if(prices.isEmpty)
        0.0D
      else
        val returns = calculateReturns(prices = prices.map(_.close))
        val meanReturn = returns.sum / returns.size
        val variance = returns.map(r => pow(r - meanReturn, 2)).sum / (returns.size - 1)
        sqrt(variance)
